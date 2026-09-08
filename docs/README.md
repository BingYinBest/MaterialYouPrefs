# docs/ — 文档根入口

本目录承载 MaterialYouPrefs → AvbTool Android 的**全部项目文档**。按内容性质分成 4 个子目录，另有 `dev-log/` 目录承载开发流水（并列于 docs/，非本目录子目录）。

---

## 目录结构

```
docs/
├── README.md               # 本文件：入口 + 索引
├── requirements/           # 需求与验收标准
│   ├── PRD.md              # 产品需求：全量子命令、UI 映射、FEC 策略、非目标
│   └── REVIEW_CRITERIA.md  # 验收清单（CI + 运行时 + 实机）
├── tech/                   # 技术方案与设计
│   ├── ARCHITECTURE.md     # 三层架构（UI → ViewModel → Room + Chaquopy → libavbfec）
│   ├── PYTHON_RUNTIME.md   # Chaquopy 15 集成（DSL、Python API、踩坑教训）
│   ├── AOSP_PATCH.md       # AOSP avbtool.py → 移动端的 patch 清单
│   ├── AVB_FEC.md          # FEC（Reed-Solomon）原生实现，libavbfec.so
│   ├── SAF_BRIDGE.md       # 文件访问桥（content:// URI → 本地路径）
│   └── DATA_LAYER.md       # Room schema、DAO、CommandRepository
├── standards/              # 编码与协作规范
│   ├── CODING.md           # Kotlin / Python / Gradle 编码风格
│   ├── UI.md               # Material 3 / Compose / 主题规范
│   ├── GIT_CI.md           # 分支、commit、Actions workflow
│   └── SECURITY.md         # 密钥、APK 签名、依赖扫描
└── workflow/               # 执行与流程
    ├── EXECUTION_STEPS.md  # M0–M5 里程碑切分 + 每里程碑 exit criteria
    ├── DAILY_LOOP.md       # 每日工作闭环（晨会/编码/CI/收尾）
    └── BUILD_TEST.md       # 构建命令、测试矩阵、CI 排错手册
```

---

## 什么时候读哪份

| 场景 | 读什么 |
|------|--------|
| 首次接手 / 找入口 | 本文件 → `requirements/PRD.md` → `workflow/EXECUTION_STEPS.md` |
| 开工一个功能前 | `requirements/PRD.md` 找范围 + `standards/CODING.md` 找规范 |
| 改 Python / Chaquopy | `tech/PYTHON_RUNTIME.md`（Chaquopy 15 的坑都在这里） |
| 改 avbtool.py | `tech/AOSP_PATCH.md`（哪些行不能改、哪些要 patch） |
| 改文件读写 | `tech/SAF_BRIDGE.md` |
| 加 FEC 相关 | `tech/AVB_FEC.md` |
| 加 Room 表 / DAO | `tech/DATA_LAYER.md` |
| 写 UI / Compose | `standards/UI.md` |
| 提交 PR / 写 commit | `standards/GIT_CI.md` |
| 加依赖 / 密钥 | `standards/SECURITY.md` |
| 排查 CI 失败 | `workflow/BUILD_TEST.md` |
| 想知道「现在做到哪了」 | `dev-log/DEVLOG.md` + `dev-log/TODO.md` + `dev-log/CHANGELOG.md` |
| 想知道「当初为什么这么做」 | `dev-log/DECISIONS.md`（ADR） |

---

## 新增文档的流程

1. **决定归属**：先看本文件的目录结构，选合适的子目录
2. **命名**：`UPPERCASE_SNAKE.md`（例如 `AOSP_PATCH.md`）
3. **文件头固定结构**：
   ```markdown
   # 短标题
   
   > **一句话说明这份文档解决什么问题**
   > 
   > **读者**：谁会读（开发 / CI / 验收 / AI 助手）
   > 
   > **不解决**：明确列出边界，避免误用
   ```
4. **加入本索引**：在「目录结构」和「什么时候读哪份」表里各加一行
5. **PR 描述**：说明新文档填补了什么空白

---

## 与 `dev-log/` 的分工

`dev-log/` 是**流水账**（做了什么、为什么），`docs/` 是**知识**（怎么做、约定什么）。

| 属于 `dev-log/` | 属于 `docs/` |
|---|---|
| 今日改了什么 | 项目架构是什么 |
| 遇到什么问题、如何绕过 | 编码规范是什么 |
| 决策过程 | 决策结果（ADR） |
| 里程碑进度 | 里程碑设计（EXECUTION_STEPS） |
