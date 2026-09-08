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

## ADR-014：MCP 网关不支持 JSON 对象体直推，seed JSON 用注释占位符

- **状态**：已定（2026-09-09，临时）
- **上下文**：`push_files` 和 `create_or_update_file` 在 params JSON 解析阶段，会尝试把以 `{` 开头（或以 `[` 开头）的字符串值二次 parse 成对象/数组。因此任何 JSON 文件内容都无法直接通过 MCP 推送到远端。
- **决策**：本地 git blob 保留真实 seed JSON（9795 字节）；远端占位符为 C 风格注释块 + 恢复指引；AvbToolRunnerImpl 的 seedFromAssets 必须处理“asset 非 JSON”的降级情况（回退到 builtin CommandsSeedModels）。
- **理由**：避免因工具限制阻断整体 M0/M1/M2 上远端。
- **恢复路径**：(a) 用户在本地 `git push --force-with-lease origin feat/avbtool`；(b) 用户在 GitHub 网页手动编辑文件；(c) 提供 `avbtool --help` 实时抓取机制（M2.5+）。
- **相关 TODO**：T-M2.5-seed（`dev-log/TODO.md`）。
