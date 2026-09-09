# 编码规范

## Kotlin
- Kotlin 2.0+
- 全部用 KtLint 默认规则
- 命名：`camelCase` 变量、`UpperCamelCase` 类、`SCREAMING_SNAKE` 常量
- 禁止 `Any` / `dynamic`，必要时用 sealed class
- 全部使用 `lateinit` / `by lazy` 明确生命周期

## Compose
- Material 3 组件优先
- 颜色/字体从 `MaterialTheme` 取，禁止硬编码色值
- 状态：单向数据流（UI → Intent → ViewModel → StateFlow → UI）
- 每个 screen 独立 file，命名 `XxxScreen.kt`

## Python
- 保留 AOSP `avbtool.py` 原始风格
- Patch 部分用 `# PATCH-Android:` 注释标记
- 不引入外部 Python 包（除 chaquopy 已声明的）

## C/C++
- 仅用于 `libavbfec.so`
- 命名 `fec_*` 前缀
- 无全局变量
- 用 CMake + NDK r26 编译

## 提交前
- `./gradlew lintDebug`
- `./gradlew testDebugUnitTest`
- 手动跑一次 P0 子命令 smoke
