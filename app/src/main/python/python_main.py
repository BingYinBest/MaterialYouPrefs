    import avbtool as _avbtool  # noqa: F401 -- triggers vendored module load

    # Diagnostic hook: if we get "TypeError: 'module' object is not callable"
    # here, inspect what `_avbtool` and `_avbtool.AvbTool` actually are.
    # (See v1.0.0-avbtool.2 hotfix -- a real Android runtime failure was
    # hiding behind a truncated stderr display.)
    if not hasattr(_avbtool, 'AvbTool'):
        raise RuntimeError(
            "avbtool module loaded but has no 'AvbTool' attribute. "
            "Type of _avbtool: " + type(_avbtool).__name__ +
            ", file: " + getattr(_avbtool, '__file__', '<no __file__>')
        )
    if not callable(getattr(_avbtool, 'AvbTool')):
        raise RuntimeError(
            "avbtool.AvbTool exists but is not callable. "
            "Type: " + type(getattr(_avbtool, 'AvbTool')).__name__
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
        tool = _avbtool.AvbTool()
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
