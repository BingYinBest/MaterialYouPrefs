# SAF Bridge

## 现状（M3.5.2 之后）
`avbtool.py` 内部使用 Python `open(path)` 打开镜像文件；`path` 是 `content://` URI 无法直接访问。目前 **不需要** fd 桥就能端到端跑通：

- **输入**：Kotlin `AvbToolRunnerImpl.stageInput(uri)` 把 `content://` 拷贝到 `cacheDir/avbtool-in-<nano>.<ext>`（真实路径），Python 用 `open(path, 'rb')` 读。
- **输出**：Python 写真实路径，Kotlin `promoteToOutput(localPath, uri)` 把结果拷回 `content://`。
- **tempfile**：M3.5.2a 里 `python_main.init_runtime(cacheDir)` 把 `TMPDIR` 重定向到 `cacheDir/avbtool-tmp/`，`avbtool.py` 里 `sign()` 的 `tempfile.NamedTemporaryFile()` 走私有 cache（`/tmp` 在 target SDK 24+ 上不一定可写）。
- **大文件**：M3.5.2c 的 `avb_io.smart_read/smart_write` 在 ≥32MB 时用 `mmap`，避免整份镜像塞 Python 堆。

这套拷贝方案 100MB 以下够用。fd 桥接是优化，非必需。

## 待做（M3.5.2b，延后到 M4 UI）
真正的 fd 桥（`/saf/fd/<id>` 虚拟路径）留到 M4：
1. Kotlin 侧用 `contentResolver.openFileDescriptor(uri, "rw")` 取得 `ParcelFileDescriptor`
2. 通过 `pfd.detachFd()` 拿到 int fd，注册到 `Kotlin ↔ Python` 表
3. Python 层注册 `builtins.open` monkey-patch，把 `/saf/fd/<id>` 前缀路径解析为 `os.fdopen(fd, 'rb+')`
4. `avbtool.py` patched 版对 `/saf/fd/` 前缀路径自动走此 hook
5. 每次操作前申请 fd，操作后显式 `os.close(fd)`；Kotlin 侧监听 URI 变化清理

延后原因：M3.5.2b 的端到端验证需要 SAF picker，而 picker 属于 M4 UI 范围；单在 M3.5 做无法真机回归。届时会跟 DetailScreen 的 SAF 输入选择器一起落地。
