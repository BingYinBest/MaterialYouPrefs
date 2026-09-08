# 相对 AOSP 的 avbtool.py patch

## 源
`/root/avb/avbtool.py`（HEAD `386fb90492db3bd6bc484a579bcde5b43a2a0292`, 分支 `android14-release`）

## Patch 清单

| 区域 | 原状 | patched |
|---|---|---|
| OpenSSL 调用 | `subprocess.check_output(['openssl', ...])` | `cryptography.hazmat...` |
| FEC 调用 | `subprocess.run(['fec_rs', ...])` | `ctypes.CDLL('libavbfec.so').fec_rs(...)` |
| 文件打开 | `open(path, 'rb+')` | `saf_open(path)`（自动识别 `/saf/fd/` 前缀） |
| 临时目录 | `/tmp` | `os.environ['TMPDIR']` = app cache |
| 日志 | `print()` | 走 stdout（不改） |
| 退出码 | 保留 | 保留 |

## Patch 文件
维护在 `patches/avbtool-android.patch`，可 `git apply` 生成 patched 版本。
