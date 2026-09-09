# TODO — 里程碑清单

> **图例**：✅ = 完成且 CI 绿；◐ = 部分完成；❌ = 未完成；🆕 = 新增子任务；↩️ = 回滚

---

## M0 文档体系 ✅

- [x] `claude.md`（入口索引）
- [x] `docs/README.md`（docs 根入口）
- [x] `docs/requirements/PRD.md` + `REVIEW_CRITERIA.md`
- [x] `docs/tech/` 全套：`ARCHITECTURE` / `DATA_LAYER` / `PYTHON_RUNTIME` / `AVB_FEC` / `SAF_BRIDGE` / `AOSP_PATCH`
- [x] `docs/standards/` 全套：`CODING` / `UI` / `GIT_CI` / `SECURITY`
- [x] `docs/workflow/` 全套：`EXECUTION_STEPS` / `DAILY_LOOP` / `BUILD_TEST`
- [x] `dev-log/` 全套：`CHANGELOG` / `TODO` / `DECISIONS` / `DEVLOG`
- [x] `feat/avbtool` 分支建立

## M1 依赖集成 ✅（Run 73 绿）

- [x] `libs.versions.toml` 加 chaquopy / room / ksp / coroutines / serialization / javax-inject
- [x] 根 `build.gradle.kts` 应用插件
- [x] `app/build.gradle.kts` 引入（含 chaquopy 块 + Room + KSP）
- [x] `app/src/main/python/` 目录预留

## M2 数据层 ✅（Run 73 绿）

- [x] Room entity / dao / db / repo 全套
- [x] `AvbToolRunner` 接口（7 方法：run / fetchHelp / aospHead / isFecLoaded / stageInput / promoteToOutput / cleanupTemp）
- [x] `AvbExecutionRequest` / `AvbExecutionResult` / `OutputFile` model
- [x] `CommandSeedModels`（kotlinx.serialization）
- [x] `AvbHelpParser`（argparse `--help` 解析器）
- [x] `CommandRepository`（seed + 24h 缓存 + fallback）

## M2.5 UI 接数据 ✅（Run 81 绿）

- [x] `MaterialYouPrefsApplication` 初始化 Room + DI（手工构造，不用 Hilt）
- [x] `AppState` 单例（暴露 db / repository / runner）
- [x] `HomeViewModel` / `FeatureViewModel` 接 Room
- [x] `HomeScreen` / `FeatureScreen` / `SettingsScreen` 改数据源
- [x] `T-M2.5-seed` 已解决：M2.6+ 走 Room 落库 + `fallbackToDestructiveMigration`，seed JSON 走代码内嵌 fallback（`CommandSeedModels.builtinCommands()`）

## M2.6+ 信息架构重构 ✅（Run 93 绿）

- [x] Room schema v2：`CommandEntity` 加 `tab` 列
- [x] `AvbDatabase` version 1→2 + `fallbackToDestructiveMigration()`
- [x] `CommandDao` 加 `observeByTab(tab)`
- [x] `CommandRepository.observeByTab()`
- [x] `AppState` 加版本状态 + `refresh()` 30s 防抖
- [x] Home 4 卡片（版本 / 终端入口 / 常用命令 / 运行时状态）
- [x] Terminal 独立路由 `terminal`（TopAppBar + OutlinedTextField + Run + monospace 输出）
- [x] Feature 按 group 分组（GROUP_TITLES 中文映射）
- [x] Terminal / Detail 路由隐藏底部导航
- [x] 签 tag `m2.6-home-4cards` @ `e223168`

## M3 avbtool 集成 🆕（拆为 5 个子里程碑）

### M3.1 Chaquopy 基础集成 ✅（Run 109 绿，`2a7afa8a`）

- [x] `app/build.gradle.kts` 解开 chaquopy 插件 + `chaquopy { defaultConfig { version = "3.12" } }`
- [x] `app/src/main/python/python_main.py`（JSON 契约，冒烟测试）
- [x] `data/repository/AvbToolRunnerImpl.kt`（Chaquopy PyObject 桥，Executor 单线程）
- [x] `MaterialYouPrefsApplication` 切 runner + `USE_CHAQUOPY_RUNNER` 开关 + Noop fallback
- [x] Chaquopy 15 DSL 踩坑修正（见 DEVLOG.md 2026-09-09 21:10）
- [x] Chaquopy 15 Python API 踩坑修正（`PyObject` / `toString` / `PyException.message`）

### M3.2 拷贝 avbtool.py ✅（Run 115 绿，`10f5802c`）

- [x] 用户通过 GitHub Web UI 上传 `app/src/main/python/avbtool.py`（201397 B, 4935 行, md5 `abff24c4...`）
- [x] `python_main.py` 更新：除 `version` 外全部命令 dispatch 到 `import avbtool` + argparse
- [x] 签 tag `m3.2-avbtool-vendored` @ `10f5802c`

### M3.2.5 固定签名 ✅（Run 151 绿，`931e7cf`）

- [x] 新增 `keystores/dev.keystore`（RSA-2048, PKCS12, `CN=AvbTool Dev`, 有效期 10000 天）
- [x] `app/build.gradle.kts` 加 `signingConfigs { devFixed { ... } }`，debug + release 都指向它
- [x] 之后 CI 生成的 APK 签名一致，可直接覆盖安装

### M3.3 patch openssl ✅（v2 纯 Python RSA，Run 全绿，tag `m3.3-pure-python-rsa` @ `4d62088`）

**v1 失败复盘**（Run 122）：
- 尝试 `pip { install("cryptography==43.0.1") }`，但 chaquo.com/pypi-13.1 **不 mirror** cryptography，PyPI 也没有 Android arm64-v8a wheel，sdist 需要 `maturin` 但 CI 没装
- 完整日志见 `docs/tech/AOSP_PATCH.md`「为什么 v1 用 cryptography 失败」
- 回滚 commit：`f8237d6`（撤回 cryptography 依赖）+ `a601808`（恢复 avbtool.py 原始版）

**v2 已实施**：
- [x] `app/src/main/python/avb_rsa.py`（241 行，零外部依赖）：
  - [x] `pow(a,d,n)` 签名/验签
  - [x] 手写 PKCS1 v1.5 padding（由 avbtool 传入，avb_rsa 只负责 pow）
  - [x] 手写 DigestInfo DER 编码（SHA-256/512）
  - [x] 手写 ASN.1 SEQUENCE 解析从 key blob 取 n/e/d（PKCS#8 + 传统 RSAPrivateKey + SubjectPublicKeyInfo + AVB raw）
- [x] patch avbtool.py 的 4 处 openssl subprocess 调用：`RSAPublicKey.__init__` / `sign()` / `verify_vbmeta_signature()` + `import avb_rsa`
- [x] 生成 `patches/avbtool-android.patch`（137 行 unified diff，4 处 hunk）
- [x] 本地互验：openssl genrsa 2048 生成的签名 == `avb_rsa.rsa_sign_raw` 输出（逐字节一致）；篡改 1 字节正确拒绝
- [x] 签 tag `m3.3-pure-python-rsa` @ `4d62088`

### M3.4 Kotlin ↔ Python 桥完善 ✅（Run 163 绿，`8eecab3`）

- [x] `python_main.py` 加 `__help__` 虚拟命令：`{"commandName": "__help__", "args": ["<subcmd>"]}` → dispatch 到 `<subcmd> --help`
- [x] `fetchHelp` 真实现：Kotlin 侧 `AvbToolRunnerImpl.fetchHelp()` 调 `__help__` 命令 → 管道 `AvbHelpParser.parse()` → 返回 `List<CommandParam>`（commit `8eecab3`）
- [ ] `stageInput` / `promoteToOutput` 的 SAF bridge（留 M3.5.2）
- [x] 签 tag `m3.4-fetchhelp-real` @ `8eecab3`

### M3.5.1 FEC 编码 ✅（纯 Python RS，`ae2a28a`）

- [x] `app/src/main/python/avb_fec.py`（203 行，纯 Python RS(255,253) GF(256) GF(0x11d) 多项式除法，零外部依赖）
- [x] `calc_fec_data_size()` 替换：直接套 libfec `ecc.h` 公式 `rounds * roots * 4096 + 4096`
- [x] `generate_fec_data()` 替换：`open().read()` + `avb_fec.encode_fec_buffer()` + 60 字节 footer 校验（`<LLLLLQ32s`，magic=0xFECFECFE）
- [x] patch avbtool.py 2 处 FEC subprocess 调用：P1 `import avb_fec` + P2 `calc_fec_data_size` + P3 `generate_fec_data`
- [x] `patches/avbtool-android.patch` 从 4 hunk 扩到 **6 hunk**（M3.3 RSA 4 处 + M3.5 FEC 2 处）
- [x] `AvbToolRunnerImpl.kt` `FEC_LOADED = true`
- [x] 集成测试：`add_hashtree_footer --fec_num_roots 2` 对 1MB 随机镜像跑通，`info_image` 完整解析
- [ ] 打 tag `m3.5-fec-pure-python`（等 CI 绿）
- [ ] 真机验证 FEC 编码布局与 libfec 逐字节一致（首次写入分区后跑 `verify_image`）

### M3.5.2 SAF 桥 ❌（未开始，依赖 M4 UI 才能真机验）

- [ ] `SAF_BRIDGE.md` 方案落地：`/saf/fd/<fd>` 虚拟路径
- [ ] Python 侧 monkey-patch `builtins.open` 识别 SAF 前缀
- [ ] Kotlin `registerSafFd` 通道（Python ↔ Kotlin fd 传递）
- [ ] `stageInput` / `promoteToOutput` 真正读写 content URI（当前 M3.2 版已能拷贝文件，但没有 Python 侧透明桥）
- [ ] Python 侧 `tempfile` 落到 app cache dir（不用系统 `/tmp`）

## M4 UI 接动作 ❌

- [ ] DetailScreen 参数表单（按 `CommandParam` 动态渲染）
- [ ] SAF picker 集成（输入文件 / 输出 URI）
- [ ] 执行 + 输出展示（stdout / stderr 双 pane）
- [ ] 常用命令卡切到 `execution_history` 真实历史
- [ ] 空态 / 错误态
- [ ] ViewModel 单元测试

## M5 CI + 发布 ❌

- [ ] Actions 加 Release 变体构建 + 签名
- [ ] `lint` 通过
- [ ] 手机实机验证（arm64-v8a）
- [ ] `REVIEW_CRITERIA.md` 全过
- [ ] PR `feat/avbtool` → `main`
- [ ] tag `v1.0.0-avbtool`

---

## 里程碑签 tag 一览

| ���程碑 | Tag | Commit |
|--------|-----|--------|
| M2.6+ | `m2.6-home-4cards` | `e223168` |
| M3.1 | （未单独 tag，见 M3.2） | `2a7afa8a` |
| M3.2 | `m3.2-avbtool-vendored` | `10f5802c` |
| M3.2.5 | （未 tag） | `931e7cf` |
| M3.3 v2 | `m3.3-pure-python-rsa` | `4d62088` |
| M3.4 | `m3.4-fetchhelp-real` | `8eecab3` |
| M3.5.1 | `m3.5-fec-pure-python`（待打） | `ae2a28a` |
