# TODO — 里程碑清单

## M0 文档体系 ✅
- [x] claude.md
- [x] PRD.md / REVIEW_CRITERIA.md
- [x] ARCHITECTURE.md / DATA_LAYER.md / PYTHON_RUNTIME.md / AVB_FEC.md / SAF_BRIDGE.md / AOSP_PATCH.md
- [x] CODING.md / UI.md / GIT_CI.md / SECURITY.md
- [x] EXECUTION_STEPS.md / DAILY_LOOP.md / BUILD_TEST.md
- [x] CHANGELOG.md / TODO.md / DECISIONS.md
- [x] `feat/avbtool` 分支

## M1 依赖集成 ✅ (CI 验证中)
- [x] libs.versions.toml 加 chaquopy + room + ksp + coroutines + serialization
- [x] 根 build.gradle.kts 应用对应插件
- [x] app/build.gradle.kts 引入（含 chaquopy 块、Room + KSP）
- [x] `app/src/main/python/` 目录预留（python_main.py 留 M3）
- [ ] gradle build 通过（本地无 SDK，等 GitHub Actions CI）

## M2 数据层 ✅ (CI 验证中)
- [x] Room entity/dao/db/repo（CommandEntity/CommandDao/ExecutionEntity/ExecutionDao/AvbDatabase/CommandRepository）
- [x] AvbToolRunner 接口契约（run/fetchHelp/aospHead/isFecLoaded/stageInput/promoteToOutput/cleanupTemp）
- [x] CommandParam / ParamType / CommandDefinition model
- [x] AvbExecutionRequest / AvbExecutionResult / OutputFile model
- [x] CommandSeedModels（kotlinx.serialization）
- [x] commands_seed.json（17 条，aospHead=386fb904）
- [x] AvbHelpParser（argparse --help 输出解析器）
- [ ] Application 初始化 Room + DI module（M2.5）
- [ ] HomeViewModel 接 Room（M2.5）
- [ ] HomeScreen/FeatureScreen/SettingsScreen 改数据源（M2.5）
- [ ] DAO 单元测试

## M2.5 UI 接数据（未启动）
- [ ] DI module（Hilt 或裸 Koin，或简单工厂）
- [ ] Application.onCreate 触发 seedFromAssets
- [ ] CommandRepository 单测 + AvbHelpParser 单测
- [ ] ViewModel：HomeViewModel / FeatureViewModel / SettingsViewModel / DetailViewModel

### 推送阻断任务（T-M2.5-seed）
- [ ] **T-M2.5-seed**：把本地磁盘上真实的 `app/src/main/assets/commands_seed.json`（9795B，17 条 avbtool 子命令，aospHead=386fb904，本地 commit `79903e5`）覆盖到远端 `feat/avbtool`。
  - **背景**：MCP `push_files` 网关会把 `files[].content` 中以 `{` 开头的字符串自动 parse 为 JSON object，任何合法 JSON 内容都推送失败（已验证 `[{"a":1}]` 也失败；YAML 注释占位符已成功推送）。
  - **当前状态**：远端 `commands_seed.json` 是 YAML 注释占位符（`d6c808f`）。
  - **恢复方案（任一）**：
    1. 用户本地 `cd /root/MaterialYouPrefs && git checkout feat/avbtool && git push --force-with-lease origin feat/avbtool`
    2. 用户在 GitHub Web UI：打开 https://github.com/BingYinBest/MaterialYouPrefs/blob/feat/avbtool/app/src/main/assets/commands_seed.json → Edit → 粘贴本地文件内容 → commit
    3. M2.5 加 `CommandRepository.seedFromAssets` fallback：文件首字符为 `#` 时改用 `CommandSeedModels.builtinCommands()`
  - **优先级**：M2.5 阻塞；CI 编译不受影响（占位符不参与编译）。

## M3 avbtool 集成（暂缓，等 M1/M2 CI 绿）
- [ ] 复制 avbtool.py + 打 patch（15+ 处 subprocess/openssl 改写）
- [ ] python_main.py
- [ ] libavbfec.so 编译 + 放入 jniLibs/arm64-v8a
- [ ] AvbToolRunner.kt 实现（Chaquopy 集成）
- [ ] SAFBridge.kt
- [ ] ctypes dlopen 胶水

## M4 UI 接动作
- [ ] DetailScreen 参数表单
- [ ] SAF picker 集成
- [ ] 执行 + 输出展示
- [ ] SettingsScreen 配置项
- [ ] fallback 到 PrefData
- [ ] 空态/错误态
- [ ] ViewModel 单元测试

## M5 CI + 发布
- [ ] Actions 更新（NDK + Gradle + Release）
- [ ] lint 通过
- [ ] 手机实机验证
- [ ] REVIEW_CRITERIA 全过
- [ ] PR → main
- [ ] tag v1.0.0-avbtool
