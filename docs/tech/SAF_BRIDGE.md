# SAF Bridge

## 问题
`avbtool.py` 内部使用 Python `open(path)` 打开镜像文件；`path` 是 `content://` URI 无法直接访问。

## 方案：fd 桥接

1. Kotlin 侧用 `contentResolver.openFileDescriptor(uri, "rw")` 取得 `ParcelFileDescriptor`
2. 通过 `pfd.detachFd()` 拿到 int fd
3. 在 Python 层注册一个 `saf.fd_open(uri)` hook，返回 `os.fdopen(fd, 'rb+')`
4. `avbtool.py` patched 版对 path 前缀为 `/saf/fd/` 的文件使用此 hook

## 内存映射
对大镜像使用 `mmap`（`mmap` 底层就是 fd），避免整个镜像读入内存。

## 生命周期
- 每次操作前申请 fd，操作后显式 `os.close(fd)`
- Kotlin 侧监听 URI 变化（用户切换文件时清理）
