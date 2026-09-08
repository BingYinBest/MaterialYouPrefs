# claude.md — MaterialYouPrefs → AvbTool Android

> 本文件是本仓库的 **AI 与协作者入口索引**。
> 任何进入本仓库的 AI 助手 / 开发者，**必须先读本文件**，再读对应专题文档。

---

## 1. 项目定位

本仓库 `BingYinBest/MaterialYouPrefs` **改造为** 一个 Android App：

- **UI 复用** `MaterialYouPrefs` 已有的 Compose + Material 3 + Navigation 骨架（3 个 tab、Detail 页、Theme 全保留）
- **数据层重构** 从静态 `PrefData` 迁到 `ViewModel + Room`，预留从 `avbtool --help` 动态拉参数的接口
- **能力扩展** 集成 AOSP `external/avb` 的 `avbtool` 全量子命令，无 root、arm64-v8a
- **运行环境** Chaquopy 15 + Python 3.12 + patched `avbtool.py` + 原生 `libavbfec.so`

---

## 2. 仓库 / 分支策略

- 仓库：`BingYinBest/MaterialYouPrefs`（不改名，保留 commit 历史）
- 默认分支：`main`（当前 UI 骨架，冻结不再动）
- 工作分支：`feat/avbtool`（本任务的所有改动都在这里）
- 完成标准：`feat/avbtool` 上所有 CI 绿，APK 可安装运行，然后 PR 回 `main`

**里程碑签名 tag**：

| 里程碑 | Tag | Commit |
|--------|-----|--------|
| M2.6+ | `m2.6-home-4cards` | `e223168` |
| M3.2 | `m3.2-avbtool-vendored` | `10f5802c` |

---

## 3. 硬约束

| # | 约束 | 来源 |
|---|---|---|
| 1 | 不改 `MaterialYouPrefs` 现有 UI 组件（`PreferenceRow/GroupSection`、`Theme.kt`、`Color.kt`） | 用户确认 |
| 2 | 数据层用 `ViewModel + Room`（B 方案），预留从 `avbtool --help` 动态拉参数的接口 | 用户确认 |
| 3 | 目标 App 无 root 依赖，arm64-v8a | 项目需求 |
| 4 | avbtool 用 AOSP `android14-release` 分支（HEAD `386fb904`） | AOSP_PATCH.md |
| 5 | FEC 用原生 `libavbfec.so`，走 NDK 编译，LGPL 隔离 | ADR-002 |
| 6 | Python 用 Chaquopy 15.0.1（**不是 3.13，是 Python 3.12**） | ADR-001，见 PYTHON_RUNTIME.md |
| 7 | 文件读写走 SAF bridge（`content://` → `/saf/fd/<fd>`） | SAF_BRIDGE.md |
| 8 | GitHub Actions 构建，Release 产出 APK | 项目需求 |
| 9 | 远端写入走 GitHub MCP（`push_files` / `create_or_update_file`），本地无 push 凭证 | 项目约束 |
| 10 | 大文件（>100 KB）不通过 MCP 参数推，走 GitHub Web UI 手工上传 | 2026-09-09 实操 |

---

## 4. 已确认的技术事实

1. **MaterialYouPrefs 现状**：
   - Kotlin 2.0.20 + Compose BOM 2024.09.02 + AGP 8.5.2 + Gradle 8.7
   - `com.bingyin.materialyouprefs`，minSdk 26 / targetSdk 35
   - 已改为 4 卡片 Home + Terminal 独立路由（M2.6+）

2. **Chaquopy 15 的 API 与文档不同**（详见 `docs/tech/PYTHON_RUNTIME.md` 更新部分）：
   - `PyModule` 类**不存在**，用 `py.getModule(name)` 拿 `PyObject`
   - 无 `Python.useInstance()` / `py.importModule()` / `py.getPlatform()`
   - `PyException.value` 不存在，用 `.message`
   - `PyObject.asString()` 不存在，用 `.toString()`
   - DSL：`chaquopy { defaultConfig { version = "3.12"; pip { install(...) } } }`
   - `abiFilters` 在 `android.defaultConfig.ndk`，不在 chaquopy 块
   - 不写 `implementation(libs.chaquopy.python)`，插件自动注入 runtime AAR

3. **avbtool 子命令总数**：约 30+（详见 PRD.md 分类表）

4. **AOSP 分支**：`android14-release`，HEAD `386fb90492db3bd6bc484a579bcde5b43a2a0292`

5. **FEC 实现**：`external/avb/libavb/libavb/src/fec/fec_rs.c` 已验证存在，走 NDK 编译成 `libavbfec.so`

6. **avbtool.py 已 vendor**：`app/src/main/python/avbtool.py`（201397 B, 4935 行, md5 `abff24c4...`），未打 patch（M3.3）

7. **已排除的技术路线**：
   - ❌ PyInstaller（无法在 Android 上跑）
   - ❌ Termux deb 包（用户要 APK，不要 root）
   - ❌ 纯 NDK C++ 重写 avbtool（重复造轮子）
   - ❌ 纯 Python 重写 FEC（性能差 + LGPL 混淆）

---

## 5. 文档索引

> 先看 `docs/README.md` 有场景 → 文件的完整对照表。这里给全景。

**入口**：
- `claude.md` — 本文件
- `docs/README.md` — docs 根入口索引

**需求**（`docs/requirements/`）：
- `PRD.md` — 全量子命令分类、UI 映射、FEC 策略、非目标
- `REVIEW_CRITERIA.md` — 验收清单（CI + 运行时）

**技术**（`docs/tech/`）：
- `ARCHITECTURE.md` — 三层架构（Compose UI → ViewModel → Room + Chaquopy Python → libavbfec.so）
- `PYTHON_RUNTIME.md` — Chaquopy 15 集成（**包含 2026-09-09 踩坑教训**）
- `AVB_FEC.md` — FEC 原生编译
- `SAF_BRIDGE.md` — 文件访问桥接
- `AOSP_PATCH.md` — AOSP 源码补丁清单（M3.3 落地 openssl→cryptography）
- `DATA_LAYER.md` — Room schema、CommandRepository、`avbtool --help` 动态拉取接口

**规范**（`docs/standards/`）：
- `CODING.md` · `UI.md` · `GIT_CI.md` · `SECURITY.md`

**流程**（`docs/workflow/`）：
- `EXECUTION_STEPS.md` — M0-M5 里程碑（M3 已拆 5 个子里程碑）
- `DAILY_LOOP.md` — 每日工作闭环
- `BUILD_TEST.md` — 构建与测试

**日志**（`dev-log/`）：
- `DEVLOG.md` — 日期归档的开发流水账（**日常写这里**）
- `CHANGELOG.md` — 倒序动作记录（重要节点摘要）
- `TODO.md` — 里程碑 checklist（**跟踪进度**）
- `DECISIONS.md` — 决策记录（ADR，为什么这么做）

---

## 6. 目录结构

```
MaterialYouPrefs/
├── app/
│   ├── build.gradle.kts              # Chaquopy 15 + Room + Compose
│   ├── src/main/java/com/bingyin/materialyouprefs/
│   │   ├── MaterialYouPrefsApplication.kt   # runner 切换 + DI
│   │   ├── AppState.kt                        # 单例暴露
│   │   ├── ui/                                # Compose UI（Home/Feature/Settings/Terminal/Detail）
│   │   └── data/
│   │       ├── db/                            # Room entity + dao
│   │       ├── model/                         # AvbExecution* + CommandParam
│   │       ├── repository/                    # AvbToolRunner (接口) + Impl (Chaquopy)
│   │       └── parser/                        # AvbHelpParser
│   ├── src/main/python/
│   │   ├── avbtool.py                       # AOSP vendored (200 KB, 未 patch)
│   │   └── python_main.py                   # JSON dispatch 入口
│   └── src/main/assets/
│       └── commands_seed.json             # seed（占位符，fallback 到代码内嵌）
├── docs/                               # 文档体系，详见 docs/README.md
│   ├── README.md                         # 入口索引
│   ├── requirements/                     # PRD + REVIEW_CRITERIA
│   ├── tech/                             # 6 份技术方案
│   ├── standards/                        # 4 份规范
│   └── workflow/                         # 3 份流程
├── dev-log/                            # 开发日志
│   ├── DEVLOG.md                         # 日期归档（日常写这里）
│   ├── CHANGELOG.md                      # 重要节点倒序
│   ├── TODO.md                           # 里程碑 checklist
│   └── DECISIONS.md                      # ADR
└── claude.md                            # 本文件
```

---

## 7. AI 助手工作法

### 7.1 每日开工前（3 分钟）

按顺序读：

1. `dev-log/DEVLOG.md` 最近 100 行 — 知道昨天做到哪
2. `dev-log/TODO.md` — 找当前里程碑的具体子任务
3. `dev-log/DECISIONS.md` 最近 3 条 ADR — 别推翻已定方案

### 7.2 改代码时

- **遵守** `docs/standards/CODING.md`（Kotlin/Python/Gradle 风格）
- **改 UI** 遵守 `docs/standards/UI.md`；不动 `PreferenceRow/GroupSection/Theme`/`Color.kt`
- **改 Python / Chaquopy** 先看 `docs/tech/PYTHON_RUNTIME.md`（**必读踩坑清单**）
- **改 avbtool.py** 先看 `docs/tech/AOSP_PATCH.md`（M3.3 才做）
- **改文件读写** 先看 `docs/tech/SAF_BRIDGE.md`
- **提交** 遵守 `docs/standards/GIT_CI.md`（commit message 规范：`type(scope): subject`）

### 7.3 收工前（必做）

- 追加 `dev-log/DEVLOG.md` 当日小节（动作 + commit SHA + CI Run + 遗留）
- 更新 `dev-log/TODO.md` 对应里程碑勾选
- 如有架构决策，追加 `dev-log/DECISIONS.md` 新 ADR
- 重要节点更新 `dev-log/CHANGELOG.md`

### 7.4 推送规则

- **默认**：`mcp-github-com-missionsquad-mcp-github:create_or_update_file` 或 `push_files`（MCP token 有权限）
- **例外 1**：单文件 > 100 KB → 让用户通过 GitHub Web UI 手工上传
- **例外 2**：JSON 内容以 `{` 开头的字符串会被 MCP 网关二次解析 → 用 Web UI 或 YAML 注释占位符
- **验证**：每次 push 后 `visit_web` 到 Actions API 等 Run 结果

### 7.5 失败时的排错流程

1. 拉最新 log：让用户发 `8_Build Debug APK.txt` 附件
2. `grep_code` 搜 `FAILED|error:|e: |BUILD FAILED|What went wrong|Could not`
3. 定位到具体行，读源码修
4. 修完立刻 MCP 推送 → 等 CI 结果
5. 失败教训**必须**追加到 `docs/tech/PYTHON_RUNTIME.md` 或对应专题

---

## 8. 外部资源

- AOSP avb 源码：本地 `/root/avb`，远端 `android.googlesource.com/platform/external/avb`
- 参考仓库：`WASDDestroy/avbtool-android-compose`（技术选型参考，非 fork）
- Chaquopy 官方：`chaquo.com/chaquopy/doc/current/`
- AOSP avbtool 文档：`android.googlesource.com/platform/external/avb/`-avbtool README

---

## 9. 当前状态

- [x] M0 文档体系（本文件 + 全部 docs/* + dev-log/*）
- [x] M1 依赖集成（Chaquopy 15 + Room + KSP + coroutines + serialization + javax-inject）
- [x] M2 数据层（Room schema + CommandRepository + AvbHelpParser + seed fallback）
- [x] M2.5 UI 接数据（Application + AppState + HomeViewModel/FeatureViewModel）
- [x] M2.6+ 信息架构重构（Home 4 卡片 + Terminal 路由 + Feature 分组 + Room v2，`e223168`）
- [x] M3.1 Chaquopy 基础集成（`2a7afa8a`，Run 109 绿）
- [x] M3.2 拷贝 avbtool.py（`10f5802c`，Run 115 绿，tag `m3.2-avbtool-vendored`）
- [ ] **M3.3** patch openssl → cryptography（下一步）
- [ ] M3.4 Kotlin ↔ Python 桥完善（fetchHelp 真实现）
- [ ] M3.5 SAF 桥 + FEC
- [ ] M4 UI 接动作（DetailScreen 参数表单 + SAF picker）
- [ ] M5 Actions 构建 + APK 验证 + PR + tag v1.0.0
