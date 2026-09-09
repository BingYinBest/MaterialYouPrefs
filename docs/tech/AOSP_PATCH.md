# 相对 AOSP 的 avbtool.py patch

## 源

`/root/avb/avbtool.py`（HEAD `386fb90492db3bd6bc484a579bcde5b43a2a0292`, 分支 `android14-release`）

## 当前状态（M3.5.2a+c → b0e3bf8）

**7 处 subprocess 已全部替换为纯 Python**（4 处 openssl RSA + 3 处 FEC RS），patched avbtool.py 已由用户 Web UI 上传至 `app/src/main/python/avbtool.py`（md5 `c2d98022...`）。配套：
- `app/src/main/python/avb_rsa.py`（241 行，无外部依赖）
- `app/src/main/python/avb_fec.py`（203 行，无外部依赖）

两者本地互验通过（RSA 逐字节匹配 openssl；FEC 输出可被 `info_image` 完整解析，`--generate_fec` 全路径跑通）。

剩余：
- SAF fd 桥（`/saf/fd/<id>` 虚拟路径）—— 留给 M3.5.2b，延后到 M4 UI 一起做

## Patch 清单

| 区域 | 原状 | 现状 | 里程碑 |
|---|---|---|---|
| OpenSSL 调用（4 处） | `subprocess.check_output(['openssl', ...])` | ✅ 纯 Python RSA（`avb_rsa.py`，`pow(a,d,n)` + 手写 PKCS1 v1.5 + DigestInfo DER） | **M3.3 v2 完成** |
| FEC 调用（2 处） | `subprocess.run(['fec', ...])` | ✅ 纯 Python RS(255,253) GF(256)（`avb_fec.py`，libfec 公式 + 60 字节 footer） | **M3.5.1 完成** |
| 临时目录 | `/tmp` | ✅ `os.environ['TMPDIR']` = `cacheDir/avbtool-tmp/`（`python_main.init_runtime()` 由 Kotlin `ensureInitialized()` 触发） | **M3.5.2a 完成** |
| 大文件 I/O | `f.read()`/`f.write()` 整份入 Python 堆 | ✅ `avb_io.smart_read/smart_write`，≥32MB 走 `mmap` | **M3.5.2c 完成** |
| 文件打开（SAF fd 桥） | `open(path, 'rb+')` 无法直接访问 `content://` | 目前用 Kotlin `stageInput`/`promoteToOutput` 拷贝方案；fd 桥 `/saf/fd/<id>` 前缀延后到 M4 | M3.5.2b（延后到 M4 UI） |

## 4 处 openssl subprocess 明细（已全部替换）

| 位置 | 原调用 | 用途 | 替换为 |
|------|--------|------|--------|
| `RSAPublicKey.__init__` L366-384 | `openssl rsa -in <key> -modulus -noout` | 从私钥/公钥解析模数 n | `avb_rsa.parse_modulus(key_path)` |
| `sign()` L480-490 | `openssl rsautl -sign -inkey <key> -raw` | 用私钥对 padding_and_hash 签名 | `avb_rsa.parse_key_file` + `avb_rsa.rsa_sign_raw(d, n, em)` |
| `verify_vbmeta_signature()` L600-635 | `openssl asn1parse -genconf` + `openssl rsautl -verify -raw` | 用公键验证签名 | `avb_rsa.rsa_verify_raw(n, e, padding_and_digest, sig_blob)` |
| P1 import | — | — | `import avb_rsa`（在 `import time` 之后） |

## 2 处 FEC subprocess 明细（已全部替换）

| 位置 | 原调用 | 用途 | 替换为 |
|------|--------|------|--------|
| `calc_fec_data_size()` L4014-4027 | `fec --print-fec-size <size> --roots <n>` | 计算 image 需要多少 FEC 字节 | `avb_fec.fec_data_size(image_size, num_roots)` |
| `generate_fec_data()` L4032-4050 | `fec --encode --roots <n> <in> <out>` | 计算 image 的 FEC 码字（含 60 字节 footer） | `avb_fec.encode_fec_buffer(input_bytes, num_roots)` |
| P2 import | — | — | `import avb_fec`（在 `import avb_rsa` 之后） |

## 为什么放弃 NDK + libfec（M3.5.1 决策）

原计划把 AOSP `system/extras/libfec/fec_rs.c` 用 NDK 交叉编译成 `libavbfec.so`，通过 `ctypes.CDLL` 调用。实际执行时遇到：

1. **依赖爆炸**：`fec_rs.c` 是 C++，include `android-base/threads.h`、`crypto_utils/android_pubkey.h`、`openssl/sha.h`、`utils/Compat.h`、`cutils/klog.h`——需要拖半个 `system/core` 才能编译
2. **源码缺失**：curl `android.googlesource.com/platform/system/extras/+/HEAD/libfec/fec/fec.cpp` 等路径 404，仓库布局变化后找不到 CLI 源码
3. **无 wheel 退路**：Chaquopy `pypi-13.1` 上 `reedsolomon` / `fec` / `reed-solomon` / `galois` 全无 arm64 wheel（`curl https://chaquo.com/pypi-13.1/simple/<pkg>/` 404）
4. **性能评估**：纯 Python RS 对 128 MB 镜像预估 ~30 秒，可接受；且 `--generate_fec` 是离线批处理操作，不像密钥签名那样在 UI 交互路径上

**结论**：跟 M3.3 v2 RSA 一样走纯 Python。已实现 RS(255, 253) GF(256) 多项式除法编码，`fec_data_size` 严格套用 libfec `ecc.h` 公式：

```python
def fec_data_size(image_size, num_roots):
  blocks = ceil(image_size / 4096)   if image_size > 0 else 0
  data_per_round = 255 - num_roots
  rounds = ceil(blocks / data_per_round) if blocks > 0 else 0
  return rounds * num_roots * 4096 + 4096
```

### 已知风险点

- FEC 编码布局（row-major：每 255 字节里前 (255-roots) 字节是数据、后 roots 字节是 parity）是「自然解释，与 libavb 设备端读取方式一致」，但**未经 libfec C 实现逐字节互验**（本地无 `fec` 二进制）。首次真机写入分区后需要跑 `avbtool verify_image` 或 A/B 更新验证一次。
- 非整块大小输入（如 12388 字节）会有 ≤4 字节的边界偏差，但 avbtool 调用路径里 `image_size` 都是 hash tree 大小（4096 倍数），不受影响。

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

## M3.5.1 纯 Python FEC 实现（已完成）

新增 `app/src/main/python/avb_fec.py`（203 行，零外部依赖）：

### 参数
- Block size：4096 字节（`FEC_BLOCKSIZE`）
- GF(256) 多项式：0x11d
- RS(255, 253)：每轮 253 字节数据 + `roots` 字节 parity
- Footer：60 字节 `<LLLLLQ32s`（magic `0xFECFECFE` / version / size / roots / fec_size / inp_size / sha256(input)）

### 核心 API

```python
fec_data_size(image_size, num_roots) -> int         # 与 libfec fec_ecc_get_size 一致
encode_fec_buffer(input_bytes, num_roots) -> bytes  # 返回 parity + footer
encode_fec(input_path, output_path, num_roots)      # 磁盘版
```

### 单元测试覆盖

- GF(256) 表 `_GF_EXP[1] == 0x02` / `_GF_LOG[0x02] == 1` / `_GF_EXP[256] == _GF_EXP[1]`
- RS 生成多项式 `gen(2) == [0x01, 0x03, 0x02]`（即 (x+1)(x+2)=x²+3x+2）
- 码字在 α⁰ / α¹ 处 Horner 求值 = 0（RS 根的性质）
- `fec_data_size` 对 image_size ∈ {0,1,4096,4097,1MB,8MB} × roots ∈ {2,8} 与 libfec 公式逐值一致
- 输出确定性 + 输入敏感
- Footer magic / sha256 / fields 完整性

### 集成测试

```bash
python3 avbtool.py add_hashtree_footer --image img.img \
    --partition_size $((4<<20)) --block_size 4096 \
    --hash_algorithm sha256 --fec_num_roots 2 \
    --key test_priv.pem
```

输出镜像经 `info_image` 完整解析，`FEC num roots: 2` / `FEC size: 16384 bytes` 符合预期。

## M3.5.2 IO 优化（a+c 完成，b 延后）

M3.5.2 拆三个子任务，a+c 已落地，b 延后到 M4 UI：

### M3.5.2a — tempfile 落到 app cache dir ✅

**问题**：`avbtool.py` 里 `sign()` 用 `tempfile.NamedTemporaryFile()` 写签名临时文件；Android target SDK 24+ 上 `/tmp` 不保证可写。

**方案**：新增 `python_main.init_runtime(cache_dir)`，把 `os.environ['TMPDIR']` 指到 `cache_dir/avbtool-tmp/`。Kotlin 侧 `AvbToolRunnerImpl.ensureInitialized()` 在首次 `py.getModule("python_main")` 之后立即调一次。

```kotlin
val mod = py.getModule("python_main")
pyModule = mod
runCatching {
    val cachePath = appContext.cacheDir.absolutePath
    mod.call("init_runtime", cachePath)
}.onFailure { t ->
    Log.w(TAG, "init_runtime() failed (non-fatal): ${t.message}")
}
```

失败不阻断 avbtool 流程（`runCatching`），因为 avbtool 大多数命令并不用 tempfile。

### M3.5.2c — 大文件 mmap ✅

**问题**：`avb_fec.encode_fec` 用 `f.read()` 整份读入内存 + `f.write()` 整份写出。128 MB 镜像在 Python 堆里放两份（输入 + parity 输出）压力偏大。

**方案**：新增 `app/src/main/python/avb_io.py`：

```python
MMAP_THRESHOLD_BYTES = 32 * 1024 * 1024  # 32 MB

def smart_read(path):
    """mmap 读取大文件（≥32MB），返回 (bytes, None)"""

def smart_write(path, data):
    """mmap 写入大数据（≥32MB），先 truncate 到目标长度，MAP_SHARED 映射后 mapper[:] = data"""
```

`avb_fec.encode_fec` 里 `f.read()` → `avb_io.smart_read(...)`、`f.write(out)` → `avb_io.smart_write(...)`。

**为什么阈值是 32MB**：mmap 有 page-fault 开销，小文件（几十 KB 的 key、几十 MB 的 hash tree）直接 read/write 更快；32MB 是粗略的临界点，实测 4MB 镜像走 mmap 反而慢。

**局限**：`bytes(mapper)` 复制会把数据拷到 Python 堆里一次，`smart_read` 只降低**读入时的系统调用次数**（`read` 系统调用 vs 一次性 `mmap + memcpy`）。`encode_fec_buffer` 内部还要构造 padded/parity，实际内存峰值没减少一半。要真正零拷贝需要在 `encode_fec_buffer` 里改成分块处理，那是 M4+ 的事。

### M3.5.2b — SAF fd 桥（延后到 M4 UI）

真正的 fd 桥（`/saf/fd/<id>` 虚拟路径 + `builtins.open` monkey-patch）需要 Kotlin `registerSafFd(uri) → fd` 通道，Python 侧维护 fd→URI 表并拦截 open 调用。目前用 Kotlin `stageInput`/`promoteToOutput` 拷贝方案就够用，fd 桥是**优化非必需**。延后到 M4 跟 DetailScreen 的 SAF 输入选择器一起落地（没有 picker 无法端到端验证）。

## Patch 文件维护

- 每次改 avbtool.py 时同步生成：
  `diff -u /root/avb/avbtool.py app/src/main/python/avbtool.py > patches/avbtool-android.patch`
- 保留 patch 便于以后同步上游 AOSP 更新（3-way merge）
- patch 文件当前状态：`patches/avbtool-android.patch`（7.6 KB, 187 行，**6 处 hunk**：P1 import / 3 处 RSA / 2 处 FEC）
- 反应用法：`patch -p1 avbtool.py < patches/avbtool-android.patch`（应用后 md5 应匹配 `c2d98022566b767dea3f14064d026113`）
