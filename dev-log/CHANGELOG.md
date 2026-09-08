# CHANGELOG
> 倒序记录所有开发动作（重要节点）。日常日常写 `DEVLOG.md`。

## 2026-09-09

### 21:25 — 文档体系完善

推送 4 个文档 commit：
- `af94d54` 新增 `dev-log/DEVLOG.md`（日期归档开发流水账，与 CHANGELOG/TODO/DECISIONS 并列）
- `aa3cc8a` 新增 `docs/README.md`（docs 根入口，把 requirements/tech/standards/workflow 4 个子目录串起来 + 场景→文件对照表）
- `4cbae96` 更新 `dev-log/TODO.md`（勾选 M2.5/M2.6+/M3.1/M3.2，M3 拆 5 个子里程碑，T-M2.5-seed 标注已解决）
- `978577a` 重写 `claude.md`（新文档体系索引 + Chaquopy 15 踩坑教训 + AI 工作法 7 节）

**签名 tag**：`m3.2-avbtool-vendored` @ `10f5802c`

### 21:17 — M3.2 完成（Run 115 绿）

- 用户通过 GitHub Web UI 上传 `app/src/main/python/avbtool.py`（201397 B, 4935 行, md5 `abff24c4ee8f696151432e6f2a4766c9`, AOSP HEAD `386fb904`）
- 推送 `10f5802c`：`python_main.py` 更新为完整 dispatch，除 `version` 外全部命令 `import avbtool` + argparse
- **遗留**：openssl subprocess 未 patch，真实命令会因缺 openssl 二进制失败，属预期，M3.3 处理

### 21:10 — M3.1 完成（Run 109 绿，`2a7afa8a`）

Chaquopy 15 基础集成。6 个 commit 链：
- `4ceb0559` 插件初版（DSL 错）
- `605b3746` 修 Chaquopy 15 DSL：`chaquopy { defaultConfig { version = "3.12" } }`
- `86d1e703` 删除不存在的 `libs.chaquopy.python` 依赖
- `9873d7f3` `python_main.py` 入口（JSON 契约）
- `cf5230ef` `AvbToolRunnerImpl` Chaquopy 桥
- `b32e1996` Application 切 runner（`USE_CHAQUOPY_RUNNER` 开关 + Noop fallback）
- `2a7afa8a` 修 Chaquopy 15 Python API：`PyObject` 而非 `PyModule`，删 `useInstance/importModule/getPlatform`

### 20:48 — M2.6+ 完成（Run 93 绿，`e223168`）

信息架构重构。14 files, +1016/-115。交付：
- Room schema v2（`CommandEntity` 加 `tab` 列，`fallbackToDestructiveMigration`）
- `CommandDao.observeByTab` + `CommandRepository.observeByTab`
- `AppState` 加版本状态 + `refresh()` 30s 防抖
- Home 4 卡片（版本 / 终端入口 / 常用命令 / 运行时状态）
- Terminal 独立路由 `terminal`（TopAppBar + OutlinedTextField + Run + monospace 输出框）
- Feature 按 group 分组（GROUP_TITLES 中文映射）
- Terminal / Detail 路由隐藏底部导航
- 签 tag `m2.6-home-4cards`

### 20:28 — TerminalScreen TopAppBar 缺 @OptIn 修复

`e223168` 修 CI Run 92 → 93 转绿。

---

### 03:20 — M0 文档体系 + M1/M2 首推（10 commit）

通过 GitHub MCP `create_branch` + `push_files` 把 feat/avbtool 全部推上远端（不依赖本地 git 凭证）。共 10 个 commit：`6fb6ffb`（6 个 gradle/CI 配置）→ `dbca845`（Room 5 文件）→ `4d93ede` + `cf6b16a`（修 CommandEntity 的 `group` 反引号）→ `83f4613` + `88ccce2`（model/runner/seed 4 文件，修 `denum`→`enum` typo）→ `676a96c`（CommandRepository + AvbHelpParser，补 `import flow.map`）→ `49f10d5`（seed JSON 因 MCP 网关把以 `{` 开头的字符串二次解析为对象而受阻，改用 C 注释占位符；TODO T-M2.5-seed 追踪）→ `3239de8` + `0a418fb` + `1f8a55f`（docs/standards + requirements + tech + workflow 全部）→ `claude.md` + `CHANGELOG.md` + `DECISIONS.md` + `TODO.md` 尾批。发现手误已修 3 处：CommandEntity 反引号、CommandParam denum→enum、CommandRepository 缺 flow.map import。

### 02:55 — Rebase feat/avbtool 到 origin/main

发现两个 main 的历史是独立分支（本地 main 从 `98c8125` 起、remote main 从 `b6831d5` 起），因此不能用普通 rebase，改用 `git rebase --onto origin/main 98c8125 feat/avbtool`，把 feat/avbtool 的 3 个 commit 移到 `dbc824e` 之上。docs + M1/M2 commit 自动合并干净；只有 CI 修复 commit 有 build.yml 冲突，手工合并：(a) 保留 remote 的 `android-actions/setup-android@v3` + `sdkmanager --licenses` + `sdkmanager "platforms;android-35"` 等 CI 修复步骤；(b) 保留本分支新增的 `'feat/**'` 分支触发器 + `--stacktrace` + `if: success()` 修复 upload-artifact 缩进错乱；(c) libs.versions.toml 自动合并保留 remote 显式 BOM 版本 + 本分支 room/chaquopy/coroutines/serialization/javax-inject。**额外决策**：发现 `chaquopy` 块的 `sourceDirs = src/main/python` 目前只有 `.gitkeep`（空目录），Chaquopy plugin 可能拒绝空 sourceDirs，因此在 M3 之前把 `chaquopy {}` 块整个注释掉，toml 里的 plugin/dep 定义保留、M3 打开即可。

### 02:40 — CI 加固（3 个阻断点）

1. `.github/workflows/build.yml` 原 YAML 缩进错乱（`- name: Build Debug APK` 顶格）→ 重写为规范 YAML；触发分支加 `'feat/**'` 让 feat/avbtool push 触发 CI。
2. `CommandRepository.kt` 用了 `@Inject` / `@Singleton` 但依赖里没声明 `javax.inject` → `libs.versions.toml` 加 `javaxInject = "1.0"` + `javax-inject` library；`app/build.gradle.kts` 加 `implementation(libs.javax.inject)`。
3. `AvbHelpParser.kt` 初版用了 `private var PendingEntry.description` 扩展属性 backing field 反模式（所有实例共享同一 backing field）→ 把 `description` 直接作为 `PendingEntry` 类的 `var` 字段。
4. `AvbDatabase.kt` 原 `exportSchema = true` 会要求 schema JSON 导出目录 → 改为 `false`（后续加 migration 再打开）。

---

## 2026-09-08

### 17:20 — 项目启动

修正任务理解：MaterialYouPrefs 是目标仓库，非从零搭。建 `feat/avbtool` 分支。完成技术选型：Chaquopy + Python + libavbfec.so + SAF bridge。AOSP avb 源码 clone 到 `/root/avb`（HEAD `386fb904`）。
