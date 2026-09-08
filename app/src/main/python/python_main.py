#!/usr/bin/env python3
"""Python entry point for the avbtool Android runtime.

The Kotlin side calls into this module via Chaquopy:

    py.call("python_main.run", args_json_str)

args_json_str is a JSON-encoded string like::

    {"commandName": "gen_key_pair", "args": ["--key_key=...", "--pub_key=..."]}

and the returned value is a JSON string matching the shape of
``data/model/AvbExecutionResult.kt``.

M3.1 scope
----------
Only ``python_main`` lives here. We do NOT import ``avbtool`` yet because
that 200 KB vendored file lands in M3.2. The dispatch table has a small
``version`` command so we can smoke-test the whole bridge end-to-end
before touching avbtool at all.
"""

import json
import sys
import time


_AOSP_HEAD = "386fb90492db3bd6bc484a579bcde5b43a2a0292"


def _success(exit_code, stdout, stderr, duration_ms):
    return json.dumps({
        "kind": "Success",
        "exitCode": exit_code,
        "stdout": stdout,
        "stderr": stderr,
        "durationMs": duration_ms,
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
        "version": "1.0.0",
        "aospHead": _AOSP_HEAD,
        "python": sys.version.split()[0],
        "runtime": "Chaquopy",
        "fecAvailable": False,
    }
    duration = int((time.monotonic() - start) * 1000)
    return _success(0, json.dumps(info), "", duration)


_HANDLERS = {
    "version": _handle_version,
}


def run(args_json):
    """Dispatch a single avbtool subcommand. Returns a JSON string."""
    started = time.monotonic()
    try:
        req = json.loads(args_json)
    except json.JSONDecodeError as e:
        return _failure("PYTHON_EXCEPTION", "Invalid args JSON: " + str(e))

    command = req.get("commandName", "")
    argv = list(req.get("args", []))
    handler = _HANDLERS.get(command)
    if handler is None:
        duration = int((time.monotonic() - started) * 1000)
        return _failure(
            "UNKNOWN_PARAM",
            "No handler for command '" + command + "'. "
            "(avbtool.py vendor lands in M3.2)",
            duration,
        )

    try:
        return handler(argv)
    except Exception as e:
        duration = int((time.monotonic() - started) * 1000)
        return _failure("PYTHON_EXCEPTION", type(e).__name__ + ": " + str(e), duration)


def main(argv):
    if not argv:
        print("Usage: python_main.py <json-args>")
        return 2
    print(run(argv[0]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
