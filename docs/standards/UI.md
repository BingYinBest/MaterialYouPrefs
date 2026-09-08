# UI 规范（MaterialYouPrefs 风格）

## 主题
- Material 3
- 动态配色：`dynamicColorScheme(isSystemInDarkTheme())`
- 支持 Android 12+ 材质；低版本 fallback 到默认色板
- 深浅色由系统决定，不做强制切换

## 布局
- 主页面：`NavigationBar` 底部 5 个 tab（对齐 5 个功能组）
- 每个 tab 内：LazyColumn + Material 卡片
- 表单：`OutlinedTextField` + `Button`；关键字段用 `Switch` / `DropdownMenuItem`

## 组件命名
- `AvbTopBar`
- `AvbCommandCard`
- `AvbOutputConsole`（黑色背景等宽字体展示 stdout/stderr）

## 交互
- 文件选择：`ActivityResultContracts.OpenDocument` / `CreateDocument`
- 输入参数：默认给合理值，允许清空（表示用默认）
- 危险操作（覆盖 vbmeta）二次确认

## 错误展示
- Python 异常 → 显示完整 stderr
- 退出码 ≠ 0 → Snackbar 提示，同时控制台保留
