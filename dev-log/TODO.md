# TODO — 里程碑清单

> **图例**：✅ = 完成且 CI 绿；◐ = 部分完成；❌ = 未完成；🆕 = 新增子任务

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
- [ ] 未做的（明确留给 M3.3）：openssl subprocess → cryptography patch、`pip install cryptography`

### M3.3 patch openssl → cryptography ❌（下一步）

- [ ] `chaquopy { defaultConfig { pip { install("cryptography==43.0") } } }` 加依赖
- [ ] 遍历 avbtool.py 的 36 处 `subprocess.call(['openssl', ...])` 替换：
  - [ ] `openssl rsa -in <key> -modulus -noout`（读公钥模数）→ `cryptography.hazmat.primitives.asymmetric.rsa` 或 DER 解析
  - [ ] `openssl rsautl -sign -inkey <key> -raw`（RSA 私钥签名）→ `cryptography.hazmat.primitives.asymmetric.padding.PKCS1v15` + `rsa.sign`
  - [ ] `openssl rsautl -verify -pubin -inkey <der> -raw`（RSA 公钥验签）→ `rsa.verify` with raw padding
  - [ ] `openssl asn1parse -genconf`（DER 生成）→ `cryptography.hazmat.primitives.serialization.Encoding.DER`
- [ ] 维护 `patches/avbtool-android.patch`（`git diff` 形式）
- [ ] 本地或 CI 跑一次 `avbtool --help` 验证

### M3.4 Kotlin ↔ Python 桥完善 ❌

- [ ] `python_main.py` 加更多命令：`version` / `help` / 全部 argparse 子命令透传
- [ ] `fetchHelp` 真实现：Python 侧调 `avbtool <cmd> --help` 输出到 stdout，Kotlin 侧 `AvbHelpParser` 解析
- [ ] `stageInput` / `promoteToOutput` 通过 content URI 的 SAF bridge 接入

### M3.5 SAF 桥 + FEC ❌

- [ ] `SAFBRIDGE.md` 方案落地：`/saf/fd/<fd>` 虚拟路径
- [ ] `external/avb/libavb/libavb/src/fec/fec_rs.c` 编译 `libavbfec.so`
- [ ] CI workflow 加 NDK 工具链
- [ ] ctypes dlopen 胶水（Python 侧）

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

| 里程碑 | Tag | Commit |
|--------|-----|--------|
| M2.6+ | `m2.6-home-4cards` | `e223168` |
| M3.1 | （未单独 tag，见 M3.2） | `2a7afa8a` |
| M3.2 | `m3.2-avbtool-vendored` | `10f5802c` |
