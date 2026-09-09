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
- Any other command: ``avbtool.py`` is executed via ``runpy.run_path`` with
  ``run_name='__main__'`` so the ``if __name__ == '__main__'`` block at the
  bottom of the vendored AOSP script runs end-to-end. That block does
  ``tool = AvbTool(); tool.run(sys.argv)`` -- the same code path a real
  `python3 avbtool.py ...` invocation takes, which is far more stable than
  importing the module and instantiating ``AvbTool`` ourselves.

Why runpy and not ``import avbtool; avbtool.AvbTool().run(argv)``?
------------------------------------------------------------------
That's the approach v1.0.0-avbtool shipped with. On-device it produced
``TypeError: 'module' object is not callable`` -- the class attribute ``AvbTool``
was returning a module instead of a class for reasons we couldn't fully
reproduce locally (we verified ``import avbtool; avbtool.AvbTool`` works on
CPython 3.12 Linux). The v1.0.0-avbtool.2+ rebuild goes through runpy so
we stop depending on attribute-level introspection of the vendored module
at all. The AOSP ``__main__`` block is what CI's
``python3 avbtool.py --help`` path exercises, so matching it on Android
is the safest thing to do.

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
- M3.5.2a: ``init_runtime(cache_dir)`` -- pointed by Kotlin's
  ``AvbToolRunnerImpl.ensureInitialized()``, so
  ``tempfile.NamedTemporaryFile()`` (used once inside ``avbtool.py``'s
  ``sign()``) lands under the app's private cache dir instead of the
  system ``/tmp``.
- M3.5.2c: mmap-backed large-file I/O is delegated to ``avb_io`` (see
  ``avb_io.py``). ``avb_fec.encode_fec`` uses
  ``avb_io.smart_read``/``smart_write``.
- M3.5.2b (SAF fd bridge): deferred to M4.2c -- needs a SAF picker to
  test end-to-end; M4.2b uses the stage+promote path (see
  ``_collect_workdir_files``).
"""

import io
import json
import os
import runpy
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
    """Point tempfile at ``cache_dir/avbtool-tmp/``.

    Called from Kotlin's ``AvbToolRunnerImpl.ensureInitialized()`` right
    after we import this module, before any avbtool command runs.

    ``cache_dir`` is ``Context.cacheDir.absolutePath`` on the Android side
    (typically ``/data/data/<pkg>/cache``). We create a subdir
    ``avbtool-tmp/`` under it and set ``TMPDIR`` accordingly so
    ``tempfile.NamedTemporaryFile()`` (used once inside ``avbtool.py``'s
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


def _find_avbtool_py():
    """Locate ``avbtool.py``. Runs alongside ``python_main.py`` inside the
    Chaquopy ``src/main/python`` directory. We use ``__file__`` first so
    that a relocated source tree still works, then fall back to walking a
    small number of common Android paths."""
    here = os.path.dirname(os.path.abspath(__file__)) if __file__ else None
    if here:
        candidate = os.path.join(here, "avbtool.py")
        if os.path.isfile(candidate):
            return candidate
    for base in (os.getcwd(), "/data/data", "/data/local/tmp"):
        if not base:
            continue
        for root, _dirs, files in os.walk(base):
            if "avbtool.py" in files:
                return os.path.join(root, "avbtool.py")
            # Don't descend too deep -- this is a fallback, not a search
            # index.
            if root.count(os.sep) - base.count(os.sep) > 3:
                break
    raise FileNotFoundError("avbtool.py not found near " + str(here))


def _handle_avbtool_command(command, args, workdir=None):
    """Execute one avbtool subcommand via the vendored ``avbtool.py``.

    Uses ``runpy.run_path`` with ``run_name='__main__'`` so the
    ``if __name__ == '__main__'`` block at the bottom of avbtool.py runs --
    the same code path a real ``python3 avbtool.py <subcmd> ...`` takes.

    If ``workdir`` is given, the process is ``chdir``'d into it for the
    duration of the call so that relative ``--output=...`` paths produced
    by avbtool land somewhere predictable (M4.2b).

    Returns a JSON string (see :func:`_success`).
    """
    script_path = _find_avbtool_py()

    argv = ["avbtool.py", command] + list(args)
    old_argv = list(sys.argv)
    sys.argv = argv

    old_stdout, old_stderr = sys.stdout, sys.stderr
    buf_out = io.StringIO()
    buf_err = io.StringIO()
    sys.stdout = buf_out
    sys.stderr = buf_err
    old_cwd = os.getcwd() if workdir else None
    exit_code = 0
    try:
        if workdir:
            os.chdir(workdir)
        # runpy.run_path reads the script source fresh and execs it in a
        # new globals dict. Because run_name is "__main__", avbtool.py's
        # bottom ``if __name__ == '__main__'`` block fires and does
        # ``AvbTool().run(sys.argv)`` -- the canonical AOSP entry point.
        try:
            runpy.run_path(script_path, run_name="__main__")
        except SystemExit as e:
            code = e.code
            if code is None or code is False:
                exit_code = 0
            elif isinstance(code, int):
                exit_code = code
            else:
                # argparse uses exit(2) for parse errors; if someone raises
                # SystemExit with a non-int payload, surface it on stderr.
                exit_code = 1
                buf_err.write("\n[avbtool] SystemExit with non-int: " + repr(code) + "\n")
    except Exception as e:
        exit_code = 1
        buf_err.write("\n[python_main] avbtool run_path raised: " +
                      type(e).__name__ + ": " + str(e) + "\n" +
                      traceback.format_exc(limit=8) + "\n")
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

    # Everything else goes through avbtool.py via runpy.run_path.
    tmpdir = tempfile.mkdtemp(prefix="avbtool-", dir=os.getcwd() or None)
    try:
        result = _handle_avbtool_command(command, argv, workdir=tmpdir)
    except FileNotFoundError as e:
        duration = int((time.monotonic() - started) * 1000)
        return _failure(
            "PYTHON_EXCEPTION",
            "Cannot locate avbtool.py: " + str(e),
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
