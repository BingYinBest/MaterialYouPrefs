# 架构 — MaterialYouPrefs + avbtool

## 1. 分层

```
┌──────────────────────────────────────────────────┐
│ Compose UI (保留)                                 │
│  HomeScreen / FeatureScreen / SettingsScreen      │
│  PreferenceGroup / PreferenceRow / DetailScreen   │
├──────────────────────────────────────────────────┤
│ ViewModel (新增)                                   │
│  HomeViewModel / FeatureViewModel / SettingsVM    │
│  DetailViewModel (参数表单 + 执行状态)              │
├──────────────────────────────────────────────────┤
│ Repository (新增)                                  │
│  CommandRepository → Room DB (命令元数据)          │
│  AvbToolRepository → Chaquopy Python 调用          │
│  HistoryRepository → Room DB (执行历史)            │
├──────────────────────────────────────────────────┤
│ 数据 + 运行时 (新增)                                │
│  Room DB · Chaquopy Python 3.13                   │
│  avbtool.py (patched AOSP) · libavbfec.so (LGPL)  │
│  SAF Bridge (content:// ↔ /saf/fd/<fd>)           │
└──────────────────────────────────────────────────┘
```

## 2. 模块边界

- `ui/` **不动**：所有 Compose 组件、Theme、Color 保持原样
- `data/` **改造**：`PrefData.kt` 保留为 fallback，主数据源换 Room
- `avb/` **新增**：`AvbToolRunner.kt`（Chaquopy 调用）、`SAFBridge.kt`
- `viewmodel/` **新增**
- `python/` **新增**：`avbtool.py` + `python_main.py`
- `jniLibs/arm64-v8a/` **新增**：`libavbfec.so`
- `assets/` **新增**：`commands_seed.json`

## 3. 数据流（一次命令执行）

```
用户点击 PrefRow
  → DetailScreen（itemId = command name）
    → DetailViewModel.loadCommandMeta()
      → CommandRepository (Room)
        → 若本地无元数据，触发 AvbToolRunner.fetchHelp(cmd) 动态拉取
    → 用户填参数 → 选 SAF 输入文件
    → DetailViewModel.execute(params, inputUri)
      → SAFBridge.toVirtualPath(inputUri) → /saf/fd/<fd>
      → AvbToolRunner.run(cmd, args)
        → Chaquopy: python_main.py execute(cmd, args)
          → avbtool.py <cmd> <args>
            → 需要 FEC 时加载 libavbfec.so
          → 返回 (exitCode, stdout, stderr, outputFiles)
      → 输出文件复制到 SAF 目录
    → 更新 HistoryRepository
  → UI 显示结果
```

## 4. 关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| UI 数据源 | Room + fallback PrefData | 用户选 B，见 ADR-005 |
| 命令元数据来源 | 混合：种子 + 动态拉取 | 冷启动快 + 抗 avbtool 版本变化 |
| Python 调用 | Chaquopy direct (`py.run()`) | 无需 subprocess，同进程 |
| FEC 加载 | ctypes + dlopen 系统 lib | LGPL 隔离 |
| 文件路径 | SAF 虚拟路径 `/saf/fd/<fd>` | 见 SAF_BRIDGE.md |
