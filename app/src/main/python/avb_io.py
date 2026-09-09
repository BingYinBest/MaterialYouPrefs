#!/usr/bin/env python3
"""Large-file I/O helpers for avbtool on Android.

M3.5.2c: use ``mmap`` for large files (> ``MMAP_THRESHOLD_BYTES``) so
we don't have to hold the whole image in Python heap. Falls back to a
plain ``read``/``write`` for small files where mmap has no benefit.

Why this exists:
----------------
``avb_fec.encode_fec_buffer`` (and the internal helpers it calls) work
on a Python ``bytes`` buffer. On Android, the JVM/Chaquopy heap is
much tighter than on a desktop. Copying a 128 MB image into Python
memory plus a copy of the parity output can blow the process out of
the way. ``mmap`` lets the kernel do the paging, and the ``bytes``
objects we return are only created when actually needed (they are
``bytes`` views on top of the mapping).

Note: ``mmap.mmap`` on Python 3.4+ returns a ``mmap`` object that
supports slicing as ``bytes``. When you slice the entire mapping you
get one ``bytes`` object that reflects the file content -- the mmap
object itself is still what we need to keep alive so the mapping
remains valid.

M3.5.2b (SAF fd bridge) will extend this module with a
``/saf/fd/<id>`` virtual path handler that dispatches to
``builtins.open`` with an ``os.fdopen`` on a registered fd. That
work is deferred to M4 UI work (needs a SAF picker to test end-to-
end).
"""

import mmap
import os


# Mmap for images at least this big. Below this, mmap has more overhead
# (page faulting, syscalls per access) than plain read/write.
MMAP_THRESHOLD_BYTES = 32 * 1024 * 1024  # 32 MB


def smart_read(path):
    """Read ``path`` into ``bytes``, mmap-backed for large files.

    Returns ``(data_bytes, mapper_or_None)``. If the caller is done
    with ``data_bytes`` (and doesn't need the underlying file anymore)
    it can ``mapper.close()`` to free the kernel mapping. If the
    caller may need to access ``data_bytes`` after this function
    returns, it must either hold the mapper or ``bytes(data_bytes)``
    to copy the mapping out.

    For ``avb_fec.encode_fec`` we return ``(bytes(...), None)`` in
    the mmap case too, because the caller wants a plain ``bytes`` it
    can keep after we're done -- the copy out of the mmap is the
    price of a stable bytes object. This is still a win vs. Python
    ``f.read()`` for large files: mmap is a single syscall to map,
    the ``bytes(...)`` copy is a tight kernel-side memcpy, and no
    Python-level loop is involved.
    """
    size = os.path.getsize(path)
    if size < MMAP_THRESHOLD_BYTES:
        with open(path, 'rb') as f:
            return f.read(), None

    fd = os.open(path, os.O_RDONLY)
    try:
        mapper = mmap.mmap(fd, 0, flags=mmap.MAP_PRIVATE, prot=mmap.PROT_READ)
        try:
            return bytes(mapper), None
        finally:
            mapper.close()
    finally:
        os.close(fd)


def smart_write(path, data):
    """Write ``data`` to ``path``, mmap-backed for large payloads.

    Small writes go through ``write()`` (one syscall). Large writes
    map the destination file to ``len(data)`` and memcpy into the
    mapping; the kernel then flushes pages to disk on close.
    """
    size = len(data)
    if size < MMAP_THRESHOLD_BYTES:
        with open(path, 'wb') as f:
            f.write(data)
        return

    # Ensure the file exists at the right length before we mmap it.
    with open(path, 'wb') as f:
        f.truncate(size)

    # We need to re-open the fd now that the file has a non-zero size.
    # mmap.mmap requires fd >= 0 (not -1) and size > 0.
    with open(path, 'r+b') as f:
        mapper = mmap.mmap(
            f.fileno(),
            size,
            flags=mmap.MAP_SHARED,
            prot=mmap.PROT_READ | mmap.PROT_WRITE,
        )
        try:
            mapper[:] = data
            mapper.flush()
        finally:
            mapper.close()


# ---------- init_runtime (M3.5.2a) ----------

# Called once from Python-side boot (see python_main.py) to relocate
# the temp dir used by ``tempfile.NamedTemporaryFile()`` (1 site in
# avbtool.py at L462, inside ``sign()``) from the Android ``/tmp``
# to the app's private cache directory. Without this, on some Android
# devices (e.g. target SDK 24+ apps with scoped storage) writing to
# the system ``/tmp`` can fail with PermissionError.
# Returns True if we actually rewired TMPDIR, False if unchanged.

def init_runtime(cache_dir):
    """Point ``tempfile`` at ``cache_dir`` so tempfile files land on the
    app's private cache (not the system ``/tmp``). Must be called
    before any avbtool command runs.

    ``cache_dir`` should be a directory that exists and is writable by
    the current process (e.g. ``Context.cacheDir`` on Android).

    Also creates a per-invocation subdirectory ``avbtool-tmp/`` under
    ``cache_dir`` and points ``TMPDIR`` at that subdirectory so that
    the ``python_main.run`` cleanup path (which unlinks the file
    created by tempfile) can't race with itself.
    """
    if not cache_dir:
        return False
    try:
        os.makedirs(cache_dir, exist_ok=True)
    except OSError:
        return False

    # Prefer a dedicated subdir so we don't step on other app caches.
    subdir = os.path.join(cache_dir, 'avbtool-tmp')
    try:
        os.makedirs(subdir, exist_ok=True)
    except OSError:
        subdir = cache_dir

    old = os.environ.get('TMPDIR')
    os.environ['TMPDIR'] = subdir
    return old != subdir


__all__ = [
    'MMAP_THRESHOLD_BYTES',
    'init_runtime',
    'smart_read',
    'smart_write',
]
