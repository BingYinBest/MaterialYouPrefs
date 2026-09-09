# DEVLOG — 每日开发流水账

> **目的**：与 `CHANGELOG.md`（倒序动作记录）、`TODO.md`（里程碑 checklist）、`DECISIONS.md`（决策记录）并列，本文件是**按日期归档**的开发流水账。
>
> **写入规范**：
> - 每天一个二级标题 `## YYYY-MM-DD`
> - 每条以 `### HH:MM` 或 `### HH:MM – 短标题` 开头
> - 内容包含：动作、commit SHA、CI Run 号、遇到的问题、下一步
> - 不追求精炼，追求**可追溯**——半年后能查出来为什么这么做

---

## 2026-09-10

### 12:55 — M3.5.2a+c 完成，IO 优化落地（`06fec52`）

**M3.5.2 拆三子任务，本轮完成 a+c，b 延后到 M4 UI**（用户拍板）。5 个 commit：

- `eba1aa3` 新增 `app/src/main/python/avb_io.py`（158 行，md5 `7d5454b2...`）
- `625a194` `avb_fec.py` — `encode_fec()` 用 `smart_read/smart_write`
- `8fbe72f` `python_main.py` — 新增 `init_runtime(cache_dir)`
- `b0e3bf8` `AvbToolRunnerImpl.kt` — `ensureInitialized()` 里调 `init_runtime(appContext.cacheDir.absolutePath)`
- `06fec52` 文档：AOSP_PATCH / SAF_BRIDGE / DEVLOG / TODO

**M3.5.2a — tempfile 落到 app cache**：
- `python_main.init_runtime(cache_dir)` 把 `os.environ['TMPDIR']` 指到 `cache_dir/avbtool-tmp/`
- Kotlin 侧 `ensureInitialized()` 首次 import 后立即调，`runCatching` 包裹，失败不阻断（avbtool 大多数命令不用 tempfile）
- 目的：Android target SDK 24+ 上 `/tmp` 不保证可写，避免 `sign()` 里的 `NamedTemporaryFile()` 抛 PermissionError

**M3.5.2c — 大文件 mmap**：
- 新增 `avb_io.py`：`smart_read/smart_write`，阈值 `MMAP_THRESHOLD_BYTES = 32 * 1024 * 1024`（32 MB）
- 小文件走普通 `f.read()/f.write()`；大文件先 `os.open` 拿 fd，`mmap.MAP_PRIVATE + PROT_READ`（读）/ `mmap.MAP_SHARED + PROT_READ|PROT_WRITE`（写）
- 大文件写入前 `truncate(size)` 分配长度，再 `mapper[:] = data; mapper.flush()`
- 局限：`bytes(mapper)` 仍会复制到 Python 堆一次，实际内存峰值没减半；只是**减少系统调用次数**

**M3.5.2b（延后）**：SAF fd 桥（`/saf/fd/<id>` + `builtins.open` monkey-patch + Kotlin `registerSafFd`）延后到 M4 UI。当前 `stageInput/promoteToOutput` 拷贝方案 100MB 以下够用，fd 桥是优化非必需；单在 M3.5 做无法端到端验证（需要 SAF picker）。

**本地验证**（Linux env）：
- `py_compile` 三份 py 全部通过
- `smart_read`/`smart_write` 33MB 随机数据 roundtrip ✅
- `init_runtime` 幂等（第 2 次返回 False）✅
- avbtool `add_hashtree_footer --image img --partition_size $((4<<20)) --fec_num_roots 2 --key test_priv.pem` 端到端 ✅ → `info_image` 输出 `FEC num roots: 2 / FEC size: 16384 bytes`

**踩坑**：初版 `smart_read` 用 `mmap.mmap(open(path,'rb'), ...)` 报 `TypeError: '_io.BufferedReader' object cannot be interpreted as an integer`——`mmap.mmap` 需要 fd 或 fileno，不能传 BufferedReader。改成 `fd = os.open(path, os.O_RDONLY)` + `try/finally os.close(fd)` 修复。

**下一步**：等 CI 绿后打 tag `m3.5.2-io-mmap-tmpdir`；然后进 M4 UI（DetailScreen 参数表单 + SAF picker + 执行输出双 pane + execution_history；届时把 M3.5.2b 合并进来一起做）。

---

## 2026-09-09

### 23:52 — M3.5.1 完成，纯 Python FEC 落地（`ae2a28a`）

**2 处 fec subprocess 已全部替换**。三个助手 commit + 一个 Web UI 上传（用户）：
- `f6a21cb` `app/src/main/python/avb_fec.py`（203 行，纯 Python RS(255,253) GF(256)，零外部依赖）
- `b9236f4` `patches/avbtool-android.patch`（187 行，**6 处 hunk** = M3.3 的 4 处 + M3.5 的 2 处）
- `9289957` `AvbToolRunnerImpl.kt` `FEC_LOADED = true`
- `ae2a28a` 用户 Web UI 上传 patched `avbtool.py`（md5 `c2d98022...`）

**实现路径**（纯 Python，不依赖 NDK/libfec）：
- `calc_fec_data_size(image_size, roots)` → 直接套 libfec `ecc.h` 公式：`rounds * roots * 4096 + 4096`
- `generate_fec_data(path, roots)` → `open().read()` + `avb_fec.encode_fec_buffer()` + footer magic/hash 校验
- RS(255, 253) 用标准多项式除法：GF(256) 表 0x11d 多项式 + 生成多项式 (x+α⁰)(x+α¹)
- 60 字节 footer `<LLLLLQ32s`（magic=0xFECFECFE / version / size / roots / fec_size / inp_size / sha256）

**放弃 NDK+libfec 的原因**：
1. libfec C++ 源码依赖 `android-base/threads.h`、`crypto_utils/android_pubkey.h`、`openssl/sha.h`、`utils/Compat.h`、`cutils/klog.h` 等 AOSP 内部头，交叉编译要拖半个 `system/core`
2. `system/extras` 仓库布局变化后找不到独立的 CLI 源码
3. Chaquopy `pypi-13.1` 上 `reedsolomon`/`fec`/`reed-solomon`/`galois` 全无 arm64 wheel（curl 验证 HTTP 404）
4. 性能评估：128 MB 镜像 ~30 秒，可接受

**本地验证**：
- ✅ GF(256) 表正确（`_GF_EXP[1]=0x02`, `_GF_LOG[0x02]=1`）
- ✅ 生成多项式 `gen(2)=[0x01,0x03,0x02]`（(x+1)(x+2)=x²+3x+2）
- ✅ 码字在 α⁰/α¹ 处 Horner 求值 = 0（RS 根性质）
- ✅ `fec_data_size` 对 size∈{0,1,4096,4097,1MB,8MB} × roots∈{2,8} 与 libfec 公式逐值一致
- ✅ 集成测试：`add_hashtree_footer --fec_num_roots 2` 对 1MB 随机镜像跑通，输出 `FEC num roots: 2` / `FEC size: 16384 bytes`
- ✅ `patch avbtool_orig.py < avbtool-android.patch` 应用后 md5 与远端 patched 版一致
- ✅ `py_compile` avbtool.py + avb_fec.py 通过

**已知风险点**（写进 AOSP_PATCH.md）：
- FEC 编码布局（row-major：每 255 字节里前 (255-roots) 是数据、后 roots 是 parity）是「自然解释」，**未经 libfec C 实现逐字节互验**（本地无 `fec` 二进制）。首次真机写入后需要跑一次 `avbtool verify_image` 或 A/B 更新
- 非整块大小输入有 ≤4 字节边界偏差，但 avbtool 调用路径里 image_size 都是 4096 倍数，不受影响

**下一步**：等 CI 绿 → 打 tag `m3.5-fec-pure-python` → M3.5.2 SAF 桥（`/saf/fd/<fd>` 虚拟路径 + Python 侧 monkey-patch `builtins.open` + Kotlin `registerSafFd` 通道）。

### 22:45 — M3.3 v2 完成，纯 Python RSA 落地（`4d62088`）

**4 处 openssl subprocess 已全部替换**。两个 commit（助手推）+ 一个 Web UI 上传（用户）：
- `296c0bf` `app/src/main/python/avb_rsa.py`（241 行，纯 Python RSA，零外部依赖）
- `fdb209e` `patches/avbtool-android.patch`（137 行，4 处 hunk）
- `4d62088` 用户 Web UI 上传 patched `avbtool.py`（md5 `fb692416...`）

**实现路径**（不依赖 `cryptography`、不需要系统 openssl）：
- `RSA.__init__` 用 `avb_rsa.parse_modulus(key_path)` 直接解析 PEM/DER 里的模数
- `sign()` 用 `pow(int.from_bytes(em,'big'), d, n)` + `.to_bytes()` 生成签名
- `verify_vbmeta_signature()` 用 `pow(int.from_bytes(sig,'big'), e, n)` 反解后逐字节比对
- DER 解析自己写：`_read_len`/`_read_seq`/`_read_int`/`_read_octet_string` + 第 2 元素 tag 区分 PKCS#8 vs 传统 RSAPrivateKey

**本地互验**（openssl 生成的基准签名 vs 我方 Python 签名）：
- ✅ `openssl genrsa 2048` + `openssl rsautl -sign -raw` 输出 == 我方 `rsa_sign_raw` 输出（逐字节一致）
- ✅ 我方 `rsa_verify_raw` 正确接受 openssl 签名
- ✅ 篡改任意 1 字节后正确拒绝
- ✅ `py_compile` avbtool.py + avb_rsa.py 通过
- ✅ `patch avbtool_orig.py < avbtool-android.patch` 应用后 md5 与远端 patched 版一致

**下一步**：等 CI 绿 → 打 tag `m3.3-pure-python-rsa` → M3.5（SAF 桥 + FEC NDK）。

### 21:14 — M3.4 完成，Run 163 绿（`8eecab3`）

**fetchHelp 真实现**。两个 commit：
- `c48ff81` `python_main.py` 加 `__help__` 虚拟命令：`{"commandName": "__help__", "args": ["<subcmd>"]}` → dispatch 到 `<subcmd> --help`，返回 argparse help 文本
- `8eecab3` `AvbToolRunnerImpl.fetchHelp()` 从空实现改成真的调 `python_main.run` + `__help__` 命令，然后管道到 `AvbHelpParser.parse()` 返回 `List<CommandParam>`

**效果**：
- `CommandRepository.getByIdOrFetch()` 现在能真正从 avbtool 拉参数元数据
- Room 里 `paramsJson` 24h 后不再靠种子，而是从 --help 输出刷新
- 失败不抛异常，返回 `emptyList()` 让调用方 fallback

**下一步**：M3.3 v2（纯 Python RSA）或 M3.5（SAF 桥 + FEC）。

### 21:44 — M3.3 v1 失败复盘 + 签名固定完成，Run 151 绿

**当前 HEAD**：`931e7cf`。签名固定完成，M3.3 openssl→cryptography patch 因依赖不可用而**回滚**。

**本轮 11 个 commit**（含 Web UI 试错）：
- `6f37aa0` `pip { install("cryptography==43.0.1") }` 加进 build.gradle.kts
- `6507c8d` 用户 Web UI 上传 patched avbtool.py（cryptography 版）
- `f8237d6` 撤回 cryptography + 加固定 `signingConfig { devFixed }` 指向 `keystores/dev.keystore`
- 若干 Web UI 中间态（Create keystores / Delete keystores / 试错上传）
- `a601808` 用户回滚 avbtool.py 到原始版（openssl subprocess 版）
- `931e7cf` Delete dev.keystore（顶层误传，真正 keystore 在 `keystores/dev.keystore`）

**M3.3 v1 失败根因**（Run 122）：

```
Looking in indexes: https://pypi.org/simple, https://chaquo.com/pypi-13.1
Collecting cryptography==43.0.1
  Downloading cryptography-43.0.1.tar.gz (686 kB)
Preparing wheel metadata: finished with status 'error'
FileNotFoundError: [Errno 2] No such file or directory: 'maturin'
```

- `cryptography` 官方 PyPI 只有 manylinux wheel，**没有 Android arm64-v8a wheel**
- `https://chaquo.com/pypi-13.1/` **不 mirror** cryptography（curl 验证 HTTP 404）
- 只能拉 sdist，sdist 用 `maturin`（Rust）做 build backend，GitHub runner 没装

**教训**：Python 密码学库要优先评估「Android arm64 wheel 是否可用」。没有 wheel 就直接上纯 Python（`pow(a,d,n)` 就够用）。

**签名固定**：
- 新增 `keystores/dev.keystore`（2754 B, PKCS12, RSA-2048, `CN=AvbTool Dev`, 有效期 10000 天）
- `build.gradle.kts` 加 `signingConfigs { devFixed { storeFile = "keystores/dev.keystore" } }`，debug/release 都指向它
- 参数硬编码在 build.gradle.kts：store=`avbtool-dev-store` / alias=`avbtool-dev`
- **警告**：这是 dev keystore，公开到 repo。**将来 release 一定走 CI Secrets**，不能把 release keystore 提交到 repo

### 21:20 — M3.2 交付 + 文档体系完善

**签名 tag**：`m3.2-avbtool-vendored` @ `10f5802c`

**交付**：
- 用户上传 AOSP `avbtool.py`（200 KB, 4935 行, HEAD `386fb904`）到 `app/src/main/python/avbtool.py`
- `python_main.py` 更新为完整 dispatch：`version` 走本地处理，其他命令 `import avbtool` + argparse 桥
- CI Run 115 绿

**本轮文档改进**：
- 新增 `dev-log/DEVLOG.md`（本文件）：日期归档的开发流水账
- 新增 `docs/README.md`：docs 根入口，把 requirements/tech/standards/workflow 四个子目录串起来
- 更新 `claude.md`：补齐 DEVLOG 和 docs/README 索引；补 M3 的 5 个子里程碑清单；补「AI 助手工作法」的具体路径
- 更新 `dev-log/TODO.md`：勾掉 M2.5 / M2.6+ / M3.1 / M3.2；标注 T-M2.5-seed 已完成（M2.6+ 走 fallback）
- 更新 `dev-log/CHANGELOG.md`：追加 M3.1 + M3.2 完整动作

### 21:17 — M3.2 上传完成，CI 绿

用户通过 GitHub Web UI 上传 `avbtool.py`（201397 B, md5 `abff24c4ee8f696151432e6f2a4766c9`）。
我推送 `python_main.py` 完整 dispatch 版本，commit `10f5802c`，CI Run 115 绿。

### 21:10 — M3.1 完成，Chaquopy 15 API 踩坑总结

**Run 109 绿**，`2a7afa8a`。M3.1 交付 6 commits：
- `4ceb0559` 插件初版（DSL 错，被推翻）
- `605b3746` 修 Chaquopy 15 DSL：`chaquopy { setup { python { version = "3.11" } } }`

**踩坑清单**（Chaquopy 15 API）：
- 没有 `PyModule` 类型，用 `PyObject = py.getModule(name)`
- 没有 `Python.useInstance` / `py.importModule`，只有 `Python.getInstance()`
- `PyObject.toString()` 返回 Python `str()` 的结果（不是 Java 的 `String` 转换）
- `PyException` 没有 `.value`，用 `.message`（含 trace）
- Chaquopy 15 DSL：`chaquopy { setup { python { version = "3.11" } } }`

**下一步**：M3.2（用户上传 AOSP avbtool.py 到 `app/src/main/python/`）。

---

## 2026-09-08

### 21:10 — 建 `/sdcard/Download/M32_upload/` 上传目录

用户上传 avbtool.py（见 09-09 21:17 条目）。

---

## 2026-09-08（M3 之前的历史）

M2 / M2.5 / M2.6+ 详细动作见 `dev-log/CHANGELOG.md`。
