# DECISIONS — 架构决策记录

## ADR-001：Python 运行时用 Chaquopy 3.13

- **状态**：已定
- **上下文**：需要在 Android APK 内跑 Python 3.13（AOSP avbtool 需要）。
- **决策**：用 Chaquopy。
- **理由**：同进程调用、无 subprocess、无 PyInstaller、APK 体积可控（~5MB 运行时）。
- **反方案**：PyInstaller（Android 不支持）、Termux deb（用户要 APK）、纯 NDK 重写（重复造轮子）。

## ADR-002：FEC 用原生 libavbfec.so

- **状态**：已定
- **上下文**：avbtool 依赖 `libavb` 的 FEC 实现，纯 Python 版性能差且混淆 LGPL。
- **决策**：从 AOSP `fec_rs.c` 提取，NDK 编 `libavbfec.so`，Python 侧 ctypes 加载。
- **理由**：性能、合规（LGPL 单独文件 + 用户可换实现）。

## ADR-003：无 root、无分区写入

- **状态**：已定
- **上下文**：avbtool 可写真实分区，但那是 root 工具范畴。
- **决策**：只做文件级读写，不写设备分区。

## ADR-004：仅 arm64-v8a

- **状态**：已定
- **上下文**：libavbfec.so 编译 + Chaquopy 支持架构。
- **决策**：只产出 arm64-v8a，不做 multi-ABI。
- **理由**：现代 Android 设备 >98% 是 arm64；multi-ABI 会 3 倍 APK 体积。

## ADR-005：仓库策略 — 保留 MaterialYouPrefs，用 feat/avbtool 分支

- **状态**：已定（2026-09-08）
- **上下文**：用户明确 MaterialYouPrefs 是目标仓库，非新建。
- **决策**：分支开发，PR 回 main。
- **理由**：保留 commit 历史、保留现有 UI 代码。

## ADR-006：UI 数据层用 ViewModel + Room（B 方案）

- **状态**：已定（2026-09-08）
- **上下文**：静态 PrefData 无法应对 avbtool 参数演进。
- **决策**：Room 表 + CommandRepository，`--help` 动态拉参数签名。
- **理由**：avbtool 是 AOSP 上游工具，参数会变；UI 自动跟随是关键。
- **反方案**：A 方案（静态 PrefData）—— 无法动态跟随、无状态持久化、无测试友好性。

## ADR-007：动态拉参数 + 24h 缓存

- **状态**：已定（2026-09-08）
- **上下文**：纯静态会过期，纯动态冷启动慢。
- **决策**：种子数据 + `--help` 覆盖，缓存 24h。
- **理由**：兼顾启动速度和数据新鲜度。

## ADR-008：CI 验证策略 + 无本地 SDK（C 方案）
- **状态**：已定（2026-09-09）
- **上下文**：开发环境是 Ubuntu（无 Android SDK），本地无法 `gradle assembleDebug`。用户明确选 C 方案：本地只写纯文本产出（依赖配置、Room 数据层、种子 JSON、parser），编译验证交给 GitHub Actions CI。"CI 绿了再进 M3"。
- **决策**：M1/M2 的 gradle + Room + seed + parser 代码全部写入工作区并 commit 到 `feat/avbtool`；push 触发 CI 后再评估是否进入 M3。
- **理由**：本地无 SDK 硬约束；把编译问题推迟到 CI 环境统一暴露，避免本地反复折腾；M3（15+ 处 subprocess/openssl 改写）风险高，需要 M1/M2 基线稳定后再动。
- **反方案**：A 方案（本地装 SDK，重）；B 方案（跳过 CI 直接合并，风险高）。
- **已知偏差**：DATA_LAYER.md 里的伪代码示例与实际 Entity/DAO 契约不完全一致（示例用 `tab` / `description` / `byTab` / `byName`，实际用 `id` 主键 + `group` + `iconKey` + `argsJson` + `paramsJson` + `observeByGroup` / `getById`）。种子 JSON 的 `tab` / `icon` / `sortOrder` 字段当前**不落库**（Entity 无对应列），留给 M2.5 阶段（改 Entity 加 `tab`/`sortOrder` 列 + 相应 DAO）统一。此处以**实际 Entity 契约为准**，文档更新留后续 ADR。

## ADR-009：CommandRepository 首版契约对齐
- **状态**：已定（2026-09-09）
- **上下文**：首版 Repository 凭 DATA_LAYER.md 的伪代码记忆编写，落盘前复读真实 CommandEntity / CommandDao / AvbToolRunner 源码后发现字段契约全面错位（`id` vs `name`、`group` vs `groupTitle`、`observeByGroup` vs `byTab`、无 `description` 列、无 `tab` 字段）。
- **决策**：推翻重写，Repository 完全对齐真实 Entity/DAO 契约；`ID_PREFIX = "avbtool."`、`CACHE_TTL_MS = 86400000`；`seedFromAssets` 有 `dao.count() > 0` 短路防重复灌入；`getByIdOrFetch` 缓存新鲜直接返回，否则 `runCatching { avbRunner.fetchHelp(name) }` 成功则更新 `paramsJson + fetchedAt` 并 upsert，失败保留旧数据不崩 tab。
- **理由**：避免把编译错误带到 CI；"先对齐既有代码再写新代码"是可复用的工程原则。

## ADR-010：javax.inject 只用注解，不接 DI 框架
- **状态**：已定（2026-09-09）
- **上下文**：`CommandRepository` 首版沿用了 Hilt 项目里常见的 `@Inject` / `@Singleton` 注解，但当前仓库未引入任何 DI 框架。若直接删注解会让依赖注入契约丢失。
- **决策**：`gradle/libs.versions.toml` 添加 `javax-inject:1.0`（纯注解库，无 DI 容器），`app/build.gradle.kts` 加 `implementation(libs.javax.inject)`。
- **理由**：javax.inject 是 JSR-330 标准、纯注解、500 字节；编译期无副作用。等 M2.5 若需要完整 DI，再评估 Hilt。
- **反方案**：(a) 引入 Hilt 增加 500+ 生成类；(b) 引入 Koin 增加 DSL 学习成本；(c) 直接删注解，后续再补。

## ADR-011：Room schema 暂不导出
- **状态**：已定（2026-09-09，临时）
- **决策**：`AvbDatabase` `exportSchema = false`。
- **理由**：M2 阶段 schema 未稳定，频繁变；`room { schemaDirectory(...) }` 需要额外 `room-gradle-plugin` 声明。schema 冻结后再开启导出并配 migration。
- **风险**：Room 会打 warning 但不阻断编译。

## ADR-012：workflow 监听 feat/** 分支
- **状态**：已定（2026-09-09）
- **决策**：`.github/workflows/build.yml` push 触发条件 `branches: [main, 'feat/**']` + `pull_request: branches: [main]`。
- **理由**：功能分支推上去即可触发 CI，不必合并到 main。

## ADR-013：PendingEntry description 字段用类内 var，不用扩展属性
- **状态**：已定（2026-09-09）
- **上下文**：AvbHelpParser 曾用 `private var PendingEntry.description: String by Delegates...` 表达累加逻辑，Kotlin 扩展属性无 backing field（每个实例共享同一份存储），会导致所有 PendingEntry 的 description 互相污染。
- **决策**：改为 `class PendingEntry(val optBlock: String, var description: String = "")` 类内 var。
- **理由**：避免共享可变状态的隐蔽 bug。

## ADR-014：MCP push_files 推送时的 hand-typing 风险
- **状态**：已定（2026-09-09）
- **上下文**：本地无 GitHub 凭证，采用 MCP `create_branch` + `push_files` 推送。JSON payload 中 `files[].content` 字段被手打入参，已发现 3 处手误：
  1. CommandEntity 的 `group` 字段初版推送漏了反引号（Kotlin `group=` 是保留语法）。
  2. CommandParam.kt 初版推送写成 `denum class ParamType`（typo 少个 e）。
  3. CommandRepository.kt 本地磁盘版缺 `import kotlinx.coroutines.flow.map`。
- **决策**：
  (a) 所有 MCP 手打推送的文件，推送后用 `get_file_contents` + 本地 `diff` 逐文件校验；
  (b) MCP 网关对 JSON 字符串有特殊行为（把以 `{` 开头的字符串自动 parse 为 object，导致合法 JSON 文件无法推），因此 `commands_seed.json` 改用 YAML 注释占位符，真实 JSON 保留在本地 commit `79903e5`，由用户在 M2.5 阶段手动覆盖。
- **理由**：明确区分"本地磁盘为准"与"远端为准"，避免后续基于错误远端继续开发。

## ADR-015：seed JSON 推送阻断的 fallback 方案
- **状态**：已定（2026-09-09）
- **上下文**：MCP 网关把 `files[].content` 中以 `{` 开头的字符串强行 parse 为 object，导致任何合法 JSON 内容都无法通过 `push_files` 上传（尝试过 `{"a":1}` 最小 JSON 也失败）；`[` 开头也会被 parse 为 array；前导空白/空白后 `{` 均失败。非 JSON 内容（YAML 注释、纯文本、数组字符串以外的普通字符串）正常。
- **决策**：`app/src/main/assets/commands_seed.json` 推一个 YAML 注释占位符（首字符 `#`）；真实 9795B JSON 保留在本地 `feat/avbtool` commit `79903e5`。
- **恢复方案**（任一）：
  1. 用户在 GitHub Web UI 上传（最简单）；
  2. 用户本地 `git push --force-with-lease`；
  3. M2.5 时 `CommandRepository.seedFromAssets` 加 fallback：文件是 YAML 注释 → 用 `CommandSeedModels.builtinCommands()` 静态 fallback。
- **理由**：阻断是网关层行为，本地无法规避；fallback 方案确保 M3 前 App 能跑起来。
