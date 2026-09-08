# CHANGELOG
> 倒序记录所有开发动作。

- **02:55** — Rebase feat/avbtool 到 origin/main：发现两个 main 的历史是独立分支（本地 main 从 `98c8125` 起、remote main 从 `b6831d5` 起），因此不能用普通 rebase，改用 `git rebase --onto origin/main 98c8125 feat/avbtool`，把 feat/avbtool 的 3 个 commit 移到 `dbc824e` 之上。docs + M1/M2 commit 自动合并干净；只有 CI 修复 commit 有 build.yml 冲突，手工合并：(a) 保留 remote 的 `android-actions/setup-android@v3` + `sdkmanager --licenses` + `sdkmanager "platforms;android-35"` 等 CI 修复步骤；(b) 保留本分支新增的 `'feat/**'` 分支触发器 + `--stacktrace` + `if: success()` 修复 upload-artifact 缩进错乱；(c) libs.versions.toml 自动合并保留 remote 显式 BOM 版本 + 本分支 room/chaquopy/coroutines/serialization/javax-inject。**额外决策**：发现 `chaquopy` 块的 `sourceDirs = src/main/python` 目前只有 `.gitkeep`（空目录），Chaquopy plugin 可能拒绝空 sourceDirs，因此在 M3 之前把 `chaquopy {}` 块整个注释掉，toml 里的 plugin/dep 定义保留、M3 打开即可。Rebase 完成后 HEAD = `bb8bdad`（feat/avbtool），工作树 clean。本地 `git push` 无 GitHub 凭证（`GIT_TERMINAL_PROMPT=0` 拿不到用户名）→ 改用 GitHub MCP `create_branch` + `push_files` 走 API 推送，不依赖本地 git 凭证。

## 2026-09-09
- **03:00** — 远端 push 完成（MCP create_branch + push_files）：
  - `6fb6ffb` — 6 核心配置（build.yml, root/app build.gradle.kts, libs.versions.toml, settings.gradle.kts, python/.gitkeep）
  - `dbca845` — Room 5 文件（AvbDatabase/CommandEntity/CommandDao/ExecutionEntity/ExecutionDao）
  - `4d93ede` / `cf6b16a` — CommandEntity 的 `group` 字段反引号修复（Kotlin 命名参数冲突）
  - `83f4613` — model/repository/seed 4 文件（引入 `denum` typo）
  - `88ccce2` — 修复 denum → enum + 恢复 CommandEntity.toModel/fromModel
  - `676a96c` — CommandRepository + AvbHelpParser（补 flow.map import）
  - `49f10d5` — commands_seed.json **占位符**（真实 JSON 因 MCP 网关 JSON 二次解析被拒，ADR-014；本地 blob 79903e5 保留）
  - `3239de8` — SECURITY/UI/BUILD_TEST/DAILY_LOOP/SAF_BRIDGE/AOSP_PATCH 6 文档
  - `0a418fb` — PRD + REVIEW_CRITERIA
  - `a245b52` — ARCHITECTURE/AVB_FEC/PYTHON_RUNTIME/EXECUTION_STEPS
  - `a61d9c1` — DATA_LAYER/TODO/claude.md
  - `1752c08` — CODING/GIT_CI（先前批次遗漏）
  - 本地 `79903e5` = rebase 后 HEAD；远端 `1752c08` = MCP push HEAD。两线内容等价、历史不同，后续如需合并以远端为准或 PR squash。
- **02:40** — CI 加固（本地静态自检发现 3 个阻断点）：
  1. `.github/workflows/build.yml` 原 YAML 缩进错乱（`- name: Build Debug APK` 顶格）→ 重写为规范 YAML；触发分支加 `'feat/**'` 让 feat/avbtool push 触发 CI。
  2. `CommandRepository.kt` 用了 `@Inject` / `@Singleton` 但依赖里没声明 `javax.inject` → `libs.versions.toml` 加 `javaxInject = "1.0"` + `javax-inject` library；`app/build.gradle.kts` 加 `implementation(libs.javax.inject)`。
  3. `AvbHelpParser.kt` 初版用了 `private var PendingEntry.description` 扩展属性 backing field 反模式（所有实例共享同一 backing field）→ 把 `description` 直接作为 `PendingEntry` 类的 `var` 字段。
  4. `AvbDatabase.kt` 原 `exportSchema = true` 会要求 schema JSON 导出目录 → 改为 `false`（后续加 migration 再打开）。
- **02:35** — 发现 remote main 已推进 15 个 commit（本地 main 冻结在 `98c8125`，remote 是 `dbc824e`）：remote 修改了 libs.versions.toml（BOM 冲突修复）+ CI workflow + AndroidManifest + HomeScreen dp import。feat/avbtool 目前基于旧 main，下次操作需先 rebase 到 origin/main（本地无 push 凭证，需用户手动 `git fetch origin && git rebase origin/main`）。
- **02:30** — `git commit 02ad7a2`：M1 + M2 全部落地（20 files, 1137 insertions）；工作树 clean。
- **02:20** — M1 依赖集成落地
- **02:15** — M2 数据层落地（8 个 Kotlin 文件 + 1 个 seed JSON）：
  - `data/model/CommandParam.kt` — `CommandParam` / `ParamType` / `CommandDefinition`
  - `data/model/AvbExecutionRequest.kt` — `AvbExecutionRequest` / `AvbExecutionResult` / `OutputFile`
  - `data/db/CommandEntity.kt` + `CommandDao.kt` — Room entity + DAO（主键 `id`，`group` 反引号转义）
  - `data/db/ExecutionEntity.kt` + `ExecutionDao.kt` — 执行历史
  - `data/db/AvbDatabase.kt` — 单库双表，version=1
  - `data/repository/AvbToolRunner.kt` — 7 方法接口契约（run / fetchHelp / aospHead / isFecLoaded / stageInput / promoteToOutput / cleanupTemp），实现留 M3
  - `data/repository/CommandRepository.kt` — `seedFromAssets` + `getByIdOrFetch`（24h 缓存 + fetchHelp 失败静默回退旧缓存）
  - `data/seed/CommandSeedModels.kt` — `CommandSeed` / `CommandSeedEntry` / `CommandSeedParam`（kotlinx.serialization）
  - `data/parser/AvbHelpParser.kt` — argparse `--help` 输出解析器（options/positional/续行/choices/default/required）
- **02:10** — 写 `assets/commands_seed.json`：17 条 avbtool 子命令（version / extract_public_key / make_vbmeta_image / add_hash_footer / append_vbmeta_image / add_hashtree_footer / erase_footer / zero_hashtree / resize_image / extract_vbmeta_image / info_image / verify_image / print_partition_digests / calculate_vbmeta_digest / calculate_kernel_cmdline / set_ab_metadata / generate_test_image），分组 KEY/VBMETA/HASHTREE/VERIFY/META/ALGO/CONFIG/ABOUT，aospHead=386fb904。用 python3 json.load 验证合法。
- **02:05** — 修正 `CommandRepository.kt` 首版：DATA_LAYER.md 里给的伪代码用的字段（`tab` / `description` / `byTab` / `byName`）与实际 Entity/DAO 契约不匹配，重写为对齐真实 Entity（`id` 主键 + `group` + `iconKey` + `argsJson` + `paramsJson`）+ 真实 DAO（`observeAll` / `observeByGroup` / `observeGroups` / `getById` / `count` / `upsertAll` / `upsert` / `update` / `clearAll` / `delete`）的版本。
- **02:00** — 勘察 AOSP `avbtool.py`：确认 15+ 处 `subprocess` / `openssl` 调用、20+ 个子命令定义。评估 M3 风险高，按"CI 绿了再进 M3"原则暂不进入。

## 2026-09-08
- **17:20** — 修正任务理解：MaterialYouPrefs 是目标仓库，非从零搭。建 `feat/avbtool` 分支。
- **17:15** — 用户确认 UI 数据层用 B 方案（ViewModel + Room，预留 --help 动态拉参数）。
- **17:10** — 修正仓库策略：保留 `BingYinBest/MaterialYouPrefs`，不新建仓库。
- **17:06** — 迁移 docs/tech/standards/workflow 到 MaterialYouPrefs 仓库。
- **17:05** — 确认 M0 文档体系在 `/root/avbtool-android/` 完成（18 个文件）。
- **17:00** — 建立 M0 文档体系初版（PRD/ARCHITECTURE 等）。
- **16:30** — AOSP avb 源码已 clone 到 `/root/avb`（HEAD `386fb904`）。
- **16:00** — 完成技术选型调研：Chaquopy + Python 3.13 + libavbfec.so（原生） + SAF bridge。

## 2026-09-07
- **21:00** — 确认项目硬约束：APK / AOSP android14-release / GitHub / Actions / 无 root / 全量子命令 / FEC。
