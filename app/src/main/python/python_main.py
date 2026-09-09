#!/usr/bin/env python3
"""Python entry point for the avbtool Android runtime.

The Kotlin side calls into this module via Chaquopy::

    py.call("python_main.run", args_json_str)

args_json_str is a JSON-encoded string like::

    {"commandName": "gen_key_pair", "args": ["--key_key=...", "--pub_key=..."]}

and the returned value is a JSON string matching the shape of
``data/model/AvbExecutionResult.kt``.

M3.2 scope
----------
- ``version``: local info probe (no avbtool import).
- Any other command: dynamically imported ``avbtool.py`` and run through
  its argparse main. ``SystemExit`` is captured; stdout/stderr are
  redirected through ``io.StringIO``.

M3.3 (deferred, v2 plan)
------------------------
Patch avbtool's 4 ``subprocess.call(['openssl', ...])`` sites to a
pure-Python RSA module (``avb_rsa.py``). No external pip dependency.
See ``docs/tech/AOSP_PATCH.md`` for the failure trace of the v1
``cryptography`` attempt.

M3.4 scope
----------
Add ``__help__`` virtual command: runs ``avbtool <subcmd> --help`` and
returns the argparse help text. Kotlin-side ``AvbToolRunnerImpl.fetchHelp``
calls this and feeds the output to ``AvbHelpParser``.

M3.5.2 scope
------------
- M3.5.2a: `init_runtime(cache_dir)` -- pointed by Kotlin's
  `AvbToolRunnerImpl.ensureInitialized()`, so `tempfile.NamedTemporaryFile()`
  (used once inside `avbtool.py`'s `sign()`) lands under the app's private
  cache dir instead of the system `/tmp`.
- M3.5.2c: mmap-backed large-file I/O is delegated to `avb_io` (see
  `avb_io.py`). `avb_fec.encode_fec` uses `avb_io.smart_read`/`smart_write`.
- M3.5.2b (SAF fd bridge): deferred to M4.2c -- needs a SAF picker to test
  end-to-end; M4.2b uses the stage+promote path (see ``_collect_workdir_files``).
"""

import io
import json
import os
import sys
import tempfile
import time
import traceback

import avb_io  # M3.5.2a/c: init_runtime() for TMPDIR + mmap helpers


_AOSP_HEAD = "386fb90492db3bd6bc484a579bcde5b43a2a0292"
_AVBTOOL_VERSION = "1.0.0"

# M3.5.2a: tracks whether init_runtime() has been called by the Kotlin side.
# We keep the flag so we can idempotently call avb_io.init_runtime() (it's
# cheap and idempotent anyway, but a caller-visible side effect is nice).
_runtime_initialized: bool = False


def init_runtime(cache_dir):
    """Point tempfile at `cache_dir/avbtool-tmp/`.

    Called from Kotlin's AvbToolRunnerImpl.ensureInitialized() right after
    we import this module, before any avbtool command runs.

    ``cache_dir`` is ``Context.cacheDir.absolutePath`` on the Android side
    (typically ``/data/data/<pkg>/cache``). We create a subdir
    ``avbtool-tmp/`` under it and set ``TMPDIR`` accordingly so
    ``tempfile.NamedTemporaryFile()`` (used once inside avbtool.py's
    ``sign()``) writes there instead of the system ``/tmp``.

    Returns True if we rewired TMPDIR this call; False if it was already
    pointing somewhere (idempotent).
    """
    global _runtime_initialized
    if _runtime_initialized:
        return False
    ok = avb_io.init_runtime(cache_dir)
    _runtime_initialized = True
    return ok


def _success(exit_code, stdout, stderr, duration_ms, generated_files=None):
    """Serialize a Success envelope. ``generated_files`` (M4.2b) is an
    ordered list of absolute paths created by avbtool during this call;
    the Kotlin runner uses them to promote the freshest one to a SAF Uri
    when the user picked an output destination."""
    return json.dumps({
        "kind": "Success",
        "exitCode": exit_code,
        "stdout": stdout,
        "stderr": stderr,
        "durationMs": duration_ms,
        "generatedFiles": list(generated_files or []),
    })


def _failure(error_code, message, duration_ms=0):
    return json.dumps({
        "kind": "Failure",
        "errorCode": error_code,
        "message": message,
        "durationMs": duration_ms,
    })


def _handle_version(_args):
    start = time.monotonic()
    info = {
        "tool": "avbtool",
        "version": _AVBTOOL_VERSION,
        "aospHead": _AOSP_HEAD,
        "python": sys.version.split()[0],
        "runtime": "Chaquopy",
        "fecAvailable": False,
    }
    duration = int((time.monotonic() - start) * 1000)
    return _success(0, json.dumps(info), "", duration)


def _collect_workdir_files(workdir):
    """Return absolute paths of regular files inside ``workdir``. Used by
    ``_handle_avbtool_command`` to report files avbtool wrote during the
    run (M4.2b). The tmpdir is fresh per run, so anything inside is
    considered output."""
    out = []
    try:
        for name in sorted(os.listdir(workdir)):
            full = os.path.join(workdir, name)
            if os.path.isfile(full):
                out.append(full)
    except OSError:
        pass
    return out


def _handle_avbtool_command(command, args, workdir=None):
    """Import avbtool.py and dispatch one subcommand.

    We re-implement the ``__main__`` block of avbtool.py so the
    argparse parser is exercised end-to-end (help / error paths
    included).

    If ``workdir`` is given, the process is ``chdir``'d into it for the
    duration of the call so that relative ``--output=...`` paths produced
    by avbtool land somewhere predictable (M4.2b).
    """
    import avbtool as _avbtool  # noqa: F401 -- triggers vendored module load

    # --- v1.0.0-avbtool.2 diagnostic hook ---
    # If we reach here, the vendored avbtool.py loaded OK. If it doesn't
    # expose a callable `AvbTool`, surface a precise error instead of the
    # opaque "TypeError: 'module' object is not callable" that would
    # otherwise pop up. This was hiding the real cause behind a truncated
    # stderr display on-device.
    avbtool_attr = getattr(_avbtool, 'AvbTool', None)
    if avbtool_attr is None:
        raise RuntimeError(
            "avbtool module loaded but has no 'AvbTool' attribute. "
            "_avbtool type: " + type(_avbtool).__name__ +
            ", __file__: " + str(getattr(_avbtool, '__file__', '<none>'))
        )
    if not callable(avbtool_attr):
        raise RuntimeError(
            "avbtool.AvbTool exists but is not callable. "
            "type: " + type(avbtool_attr).__name__
        )

    argv = ["avbtool"] + [command] + list(args)
    old_argv = list(sys.argv)
    sys.argv = argv

    old_stdout, old_stderr = sys.stdout, sys.stderr
    buf_out = io.StringIO()
    buf_err = io.StringIO()
    sys.stdout = buf_out
    sys.stderr = buf_err
    old_cwd = os.getcwd() if workdir else None
    try:
        if workdir:
            os.chdir(workdir)
        tool = avbtool_attr()
        try:
            tool.run(argv)
            exit_code = 0
        except SystemExit as e:
            exit_code = e.code if isinstance(e.code, int) else (1 if e.code else 0)
    finally:
        sys.stdout, sys.stderr = old_stdout, old_stderr
        sys.argv = old_argv
        if workdir and old_cwd:
            try:
                os.chdir(old_cwd)
            except OSError:
                pass

    generated = _collect_workdir_files(workdir) if workdir else []
    return _success(
        exit_code,
        buf_out.getvalue(),
        buf_err.getvalue(),
        0,  # duration filled by caller
        generated_files=generated,
    )


def _handle_help(args):
    """Handle the ``__help__`` virtual command: run ``avbtool <subcmd> --help``.

    Args come in as ``["<subcommand_name>"]``. Returns the raw argparse
    help output in ``stdout`` so the Kotlin-side ``AvbHelpParser`` can
    parse it. Errors (unknown subcommand) land in ``stderr``.
    """
    if not args:
        return _failure("UNKNOWN_PARAM", "__help__ requires one subcommand name")
    return _handle_avbtool_command(args[0], ["--help"])


def run(args_json):
    """Dispatch a single avbtool subcommand. Returns a JSON string."""
    started = time.monotonic()

    try:
        req = json.loads(args_json)
    except json.JSONDecodeError as e:
        return _failure("PYTHON_EXCEPTION", "Invalid args JSON: " + str(e))

    command = req.get("commandName", "")
    argv = list(req.get("args", []))

    # `version` is handled locally (no avbtool import).
    if command == "version":
        return _handle_version(argv)

    # `__help__` is a virtual command -- dispatch to ``<subcmd> --help``.
    if command == "__help__":
        return _handle_help(argv)

    # Everything else goes through avbtool.py.
    tmpdir = tempfile.mkdtemp(prefix="avbtool-", dir=os.getcwd() or None)
    try:
        result = _handle_avbtool_command(command, argv, workdir=tmpdir)
    except ModuleNotFoundError as e:
        duration = int((time.monotonic() - started) * 1000)
        return _failure(
            "PYTHON_EXCEPTION",
            "Cannot import avbtool module: " + str(e),
            duration,
        )
    except Exception as e:
        duration = int((time.monotonic() - started) * 1000)
        tb = traceback.format_exc(limit=5)
        return _failure(
            "PYTHON_EXCEPTION",
            type(e).__name__ + ": " + str(e) + "\n" + tb,
            duration,
        )
    finally:
        # Clean up tmpdir contents (best-effort). Files left by avbtool
        # are still reachable from stdout JSON if caller wants to fetch.
        try:
            if os.path.isdir(tmpdir) and not os.listdir(tmpdir):
                os.rmdir(tmpdir)
        except OSError:
            pass

    # Fill in durationMs now that we have the real wall time.
    duration = int((time.monotonic() - started) * 1000)
    parsed = json.loads(result)
    parsed["durationMs"] = duration
    return json.dumps(parsed)


def main(argv):
    if not argv:
        print("Usage: python_main.py <json-args>")
        return 2
    print(run(argv[0]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
