# claude.md — MaterialYouPrefs → AvbTool Android

> 本文件是本仓库的 **AI 与协作者入口索引**。
> 任何进入本仓库的 AI 助手 / 开发者，**必须先读本文件**，再读对应专题文档。

---

## 1. 项目定位

本仓库 `BingYinBest/MaterialYouPrefs` **改造为** 一个 Android App：

- **UI 复用** `MaterialYouPrefs` 已有的 Compose + Material 3 + Navigation 骨架（3 个 tab、Detail 页、Theme 全保留）
- **数据层重构** 从静态 `PrefData` 迁到 `ViewModel + Room`，预留从 `avbtool --help` 动态拉参数的接口
- **能力扩展** 集成 AOSP `external/avb` 的 `avbtool` 全量子命令，无 root、arm64-v8a
- **运行环境** Chaquopy + Python 3.13 + patched `avbtool.py` + 原生 `libavbfec.so`

---

## 2. 仓库 / 分支策略

- 仓库：`BingYinBest/MaterialYouPrefs`（不改名，保留 commit 历史）
- 默认分支：`main`（当前 UI 骨架，冻结不再动）
- 工作分支：`feat/avbtool`（本任务的所有改动都在这里）
- 完成标准：`feat/avbtool` 上所有 CI 绿，APK 可安装运行，然后 PR 回 `main`

---

## 3. 硬约束

| # | 约束 | 来源 |
|---|---|---|
| 1 | 不改 `MaterialYouPrefs` 现有 UI 组件（`PreferenceRow/GroupSection`、`Theme.kt`、`Color.kt`） | 用户确认 |
| 2 | 数据层用 `ViewModel + Room`（B 方案），预留从 `avbtool --help` 动态拉参数的接口 | 用户确认 |
| 3 | 目标 App 无 root 依赖，arm64-v8a | 项目需求 |
| 4 | avbtool 用 AOSP `android14-release` 分支（HEAD `386fb904`） | AOSP_PATCH.md |
| 5 | FEC 用原生 `libavbfec.so`，走 NDK 编译，LGPL 隔离 | ADR-002 |
| 6 | Python 用 Chaquopy 3.13 | ADR-001 |
| 7 | 文件读写走 SAF bridge（`content://` → `/saf/fd/<fd>`） | SAF_BRIDGE.md |
| 8 | GitHub Actions 构建，Release 产出 APK | 项目需求 |

---

## 4. 已确认的技术事实

1. **MaterialYouPrefs 现状**：
   - Kotlin 2.0.20 + Compose BOM 2024.09.02 + AGP 8.5.2 + Gradle 8.7
   - `com.bingyin.materialyouprefs`，minSdk 26 / targetSdk 35
   - `PrefData.kt` 硬编码 3 组占位偏好（Wi-Fi/蓝牙/相机/主题…），需整体替换
   - `DetailScreen` 是空展示页，需接真实动作
   - 无 Chaquopy / Room / Retrofit 依赖

2. **avbtool 子命令总数**：约 30+（详见 PRD.md 分类表）

3. **AOSP 分支**：`android14-release`，HEAD `386fb90492db3bd6bc484a579bcde5b43a2a0292`

4. **FEC 实现**：`external/avb/libavb/libavb/src/fec/fec_rs.c` 已验证存在，走 NDK 编译成 `libavbfec.so`

5. **已排除的技术路线**：
   - ❌ PyInstaller（无法在 Android 上跑）
   - ❌ Termux deb 包（用户要 APK，不要 root）
   - ❌ 纯 NDK C++ 重写 avbtool（重复造轮子）
   - ❌ 纯 Python 重写 FEC（性能差 + LGPL 混淆）

---

## 5. 文档索引

**入口**（本文件）：
- `claude.md` — 项目定位、约束、索引

**需求**（`docs/requirements/`）：
- `PRD.md` — 全量子命令分类、UI 映射、FEC 策略、非目标
- `REVIEW_CRITERIA.md` — 验收清单（CI + 运行时）

**技术**（`docs/tech/`）：
- `ARCHITECTURE.md` — 三层架构（Compose UI → ViewModel → Room + Chaquopy Python → libavbfec.so）
- `PYTHON_RUNTIME.md` — Chaquopy 集成
- `AVB_FEC.md` — FEC 原生编译
- `SAF_BRIDGE.md` — 文件访问桥接
- `AOSP_PATCH.md` — AOSP 源码补丁清单
- `DATA_LAYER.md` — Room schema、CommandRepository、`avbtool --help` 动态拉取接口

**规范**（`docs/standards/`）：
- `CODING.md` · `UI.md` · `GIT_CI.md` · `SECURITY.md`

**流程**（`docs/workflow/`）：
- `EXECUTION_STEPS.md` — M0-M5 里程碑
- `DAILY_LOOP.md` — 每日工作闭环
- `BUILD_TEST.md` — 构建与测试

**日志**（`dev-log/`）：
- `CHANGELOG.md` — 倒序开发日志
- `TODO.md` — 里程碑清单
- `DECISIONS.md` — 决策记录（ADR）

---

## 6. 目录结构

```
MaterialYouPrefs/
├── app/                              # 现有 UI 骨架 + 新增 avbtool 能力
│   ├── build.gradle.kts              # 加 Chaquopy + Room 依赖
│   ├── src/main/
│   │   ├── java/com/bingyin/materialyouprefs/
│   │   │   ├── MainActivity.kt        # 保留
│   │   │   ├── ui/                    # 保留现有 UI 组件
│   │   │   ├── data/                  # 改造：PrefData → CommandEntity/Dao/Repo
│   │   │   ├── avb/                   # 新增：AvbRepository、AvbToolRunner、SAF bridge
│   │   │   └── viewmodel/             # 新增：Home/Detail ViewModels
│   │   ├── python/                    # 新增：avbtool.py + python_main.py
│   │   └── jniLibs/arm64-v8a/         # 新增：libavbfec.so
│   └── src/main/assets/               # 新增：commands_seed.json
├── docs/                             # 本仓库文档体系
├── dev-log/                          # 开发日志
└── claude.md                         # 本文件
```

---

## 7. AI 助手工作法

1. **开工前**：读 `claude.md` + `dev-log/TODO.md` + `dev-log/CHANGELOG.md` 最近 10 行
2. **改代码**：遵循 `docs/standards/CODING.md`；改 UI 不动 `PreferenceRow/GroupSection/Theme`
3. **收工前**：更新 `dev-log/TODO.md`、`dev-log/CHANGELOG.md`；有决策则追加 `dev-log/DECISIONS.md`
4. **写文件**：大文件用 `terminal` + heredoc（`cat > file << 'EOF'`）；小改动用 `edit_file`
5. **推送前**：跑一次 `./gradlew lint` 和 CI 里的构建

---

## 8. 外部资源

- AOSP avb 源码：`/root/avb`（本地），远端 `Android/android/external/avb`
- 参考仓库：`WASDDestroy/avbtool-android-compose`（技术选型参考，非 fork）
- avbtool 官方文档：https://android.googlesource.com/platform/external/avb/

---

## 9. 当前状态

- [x] M0 文档体系（本文件 + 全部 docs/* + dev-log/*）
- [x] 建 `feat/avbtool` 分支
- [ ] M1 集成 Chaquopy + Room 依赖
- [ ] M2 数据层改造（Room schema + CommandRepository + 种子数据）
- [ ] M3 集成 avbtool.py + libavbfec.so + SAF bridge
- [ ] M4 UI 接真实动作（DetailScreen 参数输入/输出展示）
- [ ] M5 Actions 构建 + APK 验证 + PR
