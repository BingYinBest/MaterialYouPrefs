# 相对 AOSP 的 avbtool.py patch

## 源

`/root/avb/avbtool.py`（HEAD `386fb90492db3bd6bc484a579bcde5b43a2a0292`, 分支 `android14-release`）

## 当前状态（M3.3 v2 → 4d62088）

**4 处 openssl subprocess 已全部替换为纯 Python RSA**，patched avbtool.py 已由用户 Web UI 上传至 `app/src/main/python/avbtool.py`（md5 `fb692416...`）。配套 `app/src/main/python/avb_rsa.py`（241 行，无外部依赖）也已推送。

`avb_rsa.py` 与 openssl 生成的签名逐字节一致（本地互验通过）。剩余：
- FEC 相关 2 处 `subprocess`（留给 M3.5）
- 文件打开 / 临时目录 / SAF 桥（留给 M3.5）

## Patch 清单

| 区域 | 原状 | 现状 | 里程碑 |
|---|---|---|---|
| OpenSSL 调用（4 处） | `subprocess.check_output(['openssl', ...])` | ✅ 纯 Python RSA（`avb_rsa.py`，`pow(a,d,n)` + 手写 PKCS1 v1.5 + DigestInfo DER） | **M3.3 v2 完成** |
| FEC 调用（2 处） | `subprocess.run(['fec', ...])` | `ctypes.CDLL('libavbfec.so').fec_rs(...)`（用 NDK 编译 fec_rs.c） | M3.5（待做） |
| 文件打开 | `open(path, 'rb+')` | `saf_open(path)`（识别 `/saf/fd/` 前缀走 SAF） | M3.5 |
| 临时目录 | `/tmp` | `os.environ['TMPDIR']` = app cache dir | M3.5 |

## 4 处 openssl subprocess 明细（已全部替换）

| 位置 | 原调用 | 用途 | 替换为 |
|------|--------|------|--------|
| `RSAPublicKey.__init__` L366-384 | `openssl rsa -in <key> -modulus -noout` | 从私钥/公钥解析模数 n | `avb_rsa.parse_modulus(key_path)` |
| `sign()` L480-490 | `openssl rsautl -sign -inkey <key> -raw` | 用私钥对 padding_and_hash 签名 | `avb_rsa.parse_key_file` + `avb_rsa.rsa_sign_raw(d, n, em)` |
| `verify_vbmeta_signature()` L600-635 | `openssl asn1parse -genconf` + `openssl rsautl -verify -raw` | 用公钥验证签名 | `avb_rsa.rsa_verify_raw(n, e, padding_and_digest, sig_blob)` |
| P1 import | — | — | `import avb_rsa`（在 `import time` 之后） |

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

**教训**：Python 密码学库要优先评估「Android arm64 wheel 是否可用」。没有 wheel 就直接上纯 Python（`pow(a,d,n)` 就够用）。

## M3.3 v2 纯 Python RSA 实现（已完成）

新增 `app/src/main/python/avb_rsa.py`（241 行，零外部依赖）：

### 数据格式支持
- **私钥**：PEM（`RSA PRIVATE KEY` 传统 / `PRIVATE KEY` PKCS#8）、DER（PKCS#8 / RSAPrivateKey）
- **公钥**：PEM（`PUBLIC KEY`）、DER（SubjectPublicKeyInfo）
- **AVB 原始**：`num_bits | n0_inv | n | rr`（备用于 blob 解析路径）

### 核心 API

```python
parse_key_file(path) -> (n, e, d_or_none)  # d 为 None 表示公钥
parse_modulus(path) -> n                    # RSAPublicKey.__init__ 用
rsa_sign_raw(d, n, padding_and_hash) -> sig # sign() 用
rsa_verify_raw(n, e, padding, sig) -> bool  # verify_vbmeta_signature() 用
```

### 签名实现

```python
sig = pow(int.from_bytes(padding_and_hash, 'big'), d, n)
sig_bytes = sig.to_bytes((n.bit_length() + 7) // 8, 'big')
```

`padding_and_hash` 由 avbtool.py 的 `ALGORITHMS[...].padding + digest` 生成，已经带 PKCS#1 v1.5 padding + SHA-256 DigestInfo DER 前缀，不需要 `avb_rsa.py` 再处理。

### 验签实现

```python
recovered = pow(int.from_bytes(signature, 'big'), e, n)
return recovered.to_bytes(...) == padding_and_digest
```

### DER 解析

自实现 `_read_len` / `_read_seq` / `_read_int` / `_read_octet_string` 通用 helpers，用第 2 元素 tag（`0x30` SEQUENCE vs `0x02` INTEGER）区分 PKCS#8 与传统 RSAPrivateKey。

### 本地验证（全部通过）

用 `openssl genrsa 2048` + `openssl rsautl -sign -raw` 生成基准签名，对比 `avb_rsa.rsa_sign_raw`：

- ✅ 逐字节一致
- ✅ 我方 `rsa_verify_raw` 正确接受 openssl 签名
- ✅ 篡改签名任意 1 字节后正确拒绝

## Patch 文件维护

- 每次改 avbtool.py 时同步生成：
  `diff -u /root/avb/avbtool.py app/src/main/python/avbtool.py > patches/avbtool-android.patch`
- 保留 patch 便于以后同步上游 AOSP 更新（3-way merge）
- patch 文件当前状态：`patches/avbtool-android.patch`（5.6 KB, 137 行，4 处 hunk）
