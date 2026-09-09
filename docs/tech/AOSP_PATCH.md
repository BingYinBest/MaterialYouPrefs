# 相对 AOSP 的 avbtool.py patch

## 源

`/root/avb/avbtool.py`（HEAD `386fb90492db3bd6bc484a579bcde5b43a2a0292`, 分支 `android14-release`）

## 当前状态（M3.2 → 931e7cf）

**仅 vendored 原始文件**，未应用任何 patch。签名/验签相关的 4 处 `openssl subprocess` 调用会失败，因为 Android 上没有 openssl 二进制。

## Patch 清单（未来路线图）

| 区域 | 原状 | 目标 | 里程碑 |
|---|---|---|---|
| OpenSSL 调用（4 处） | `subprocess.check_output(['openssl', ...])` | 纯 Python RSA（`pow(a,d,n)` + 手写 PKCS1 v1.5 + DigestInfo DER），**不依赖外部包** | M3.3 v2 |
| FEC 调用（2 处） | `subprocess.run(['fec', ...])` | `ctypes.CDLL('libavbfec.so').fec_rs(...)`（用 NDK 编译 fec_rs.c）| M3.5 |
| 文件打开 | `open(path, 'rb+')` | `saf_open(path)`（自动识别 `/saf/fd/` 前缀，走 Storage Access Framework）| M3.5 |
| 临时目录 | `/tmp` | `os.environ['TMPDIR']` = app cache dir | M3.5 |
| 日志 | `print()` | 走 stdout（不改）| — |
| 退出码 | 保留 | 保留 | — |

## 4 处 openssl subprocess 明细

| 行号 | 原调用 | 用途 |
|------|--------|------|
| L369-384 | `openssl rsa -in <key> -modulus -noout` | 从私钥/公钥解析模数 n |
| L483-489 | `openssl rsautl -sign -inkey <key> -raw -out <blob>` | 用私钥对 padding_and_hash 签名 |
| L623-628 | `openssl asn1parse -genconf -nocap` | 从模数生成公钥 DER |
| L630-638 | `openssl rsautl -verify -pubin -inkey <der> -raw -in <blob>` | 用公钥验证签名 |

## 为什么 v1 用 cryptography 失败（M3.3 v1 → Run 122）

CI 日志节选：

```
Looking in indexes: https://pypi.org/simple, https://chaquo.com/pypi-13.1
Collecting cryptography==43.0.1
  Downloading cryptography-43.0.1.tar.gz (686 kB)
Preparing wheel metadata: finished with status 'error'
FileNotFoundError: [Errno 2] No such file or directory: 'maturin'
```

**根因**：
1. `cryptography` 官方 PyPI 只有 manylinux wheel（`cp312-cp312-manylinux_x86_64.whl` 等），**没有 Android arm64-v8a wheel**
2. `https://chaquo.com/pypi-13.1/` **不 mirror** cryptography：
   - `curl https://chaquo.com/pypi-13.1/simple/cryptography/` → **HTTP 404**
3. pip 只能拉 sdist，sdist 用 `maturin`（Rust）做 build backend，GitHub runner 默认没装

**教训**：Python 密码学库要优先评估「Android arm64 wheel 是否可用」。

## M3.3 v2 纯 Python RSA 方案（待实施）

新增 `app/src/main/python/avb_rsa.py`（约 200 行，无外部依赖）：

- **签名**：`sig = pow(int.from_bytes(padding_and_hash, 'big'), d, n)`
- **验签**：`m = pow(int.from_bytes(sig, 'big'), e, n)` 然后比较 DER 编码的 DigestInfo
- **Padding**：手写 PKCS1 v1.5 EM 格式：`0x00 0x01 <0xff*N> 0x00 <DigestInfo>`
- **DigestInfo**：SHA-256 前缀固定字节 `30 31 30 0d 06 09 60 86 48 01 65 03 04 02 01 05 00 04 20 <32 bytes digest>`
- **DER 解析**：用标准库手写 ASN.1 SEQUENCE 解析从 key blob 里取 n/e/d
- **参考**：AOSP libavb 里的 `rsa_util.c` 就是这个逻辑

然后 patch avbtool.py 的 4 处 openssl subprocess 调用改为 `from avb_rsa import ...`。

## Patch 文件维护

- 每次改 avbtool.py 时同步生成 patch：
  `diff -u /root/avb/avbtool.py app/src/main/python/avbtool.py > patches/avbtool-android.patch`
- 保留 patch 便于以后同步上游 AOSP 更新（3-way merge）
- patch 文件当前状态：**待生成**（M3.3 v2 完成后补上）
