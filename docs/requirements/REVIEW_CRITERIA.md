# REVIEW_CRITERIA

> **AI 硬约束**：每个任务完成后必须**自查本文件**。
> 未通过的项目要么修复、要么写 TODO 说明为何暂时不做。

---

## 1. CI 门禁（每次 push 必过）

| # | 检查项 | 命令/工具 | 失败即拒绝合并 |
|---|---|---|---|
| 1 | Ktlint 通过 | `./gradlew ktlintCheck` | ✅ |
| 2 | Compose 稳定 API | Compose Compiler 报错 | ✅ |
| 3 | 单元测试通过 | `./gradlew testDebugUnitTest` | ✅ |
| 4 | Lint 无 error（warning 可容忍） | `./gradlew lintDebug` | ✅ |
| 5 | Debug APK 编译通过 | `./gradlew assembleDebug` | ✅ |
| 6 | APK 大小检查 | `apk_size_tool` | ⚠️ 超阈值写 TODO |
| 7 | Chaquopy 产物存在 | `app/build/intermediates/assets/debug/python/` | ✅ |

---

## 2. 运行时验收（每个子命令交付时手动跑）

对每个 `avbtool <subcmd>` 的 UI 卡片交付，必须完成：

| # | 场景 | 预期 |
|---|---|---|
| 1 | 无输入执行 | 弹出 "Missing required arguments: xxx" 并高亮对应字段 |
| 2 | 正常输入执行 | 显示 stdout/stderr + exit code；绿色/红色/黄色语义色 |
| 3 | 输出文件生成 | 通过 SAF 保存到用户指定目录，弹窗确认 |
| 4 | 长耗时命令 | 显示进度条 + 取消按钮；后台线程不阻塞 UI |
| 5 | Python 崩溃 | 捕获 `PyException`，显示用户友好错误 |
| 6 | FEC 加载失败（`libavbfec.so` 缺失） | 明确报错 "libavbfec.so not loaded"，不静默退化 |
| 7 | 参数错误 | `avbtool --help` 返回的参数签名与 UI 表单一致 |
| 8 | 无 root 尝试写分区 | 拦截并提示 "requires root" |

---

## 3. 代码审查 checklist

- [ ] 命名遵循 `standards/CODING_STANDARDS.md`（`AvbToolRunner` 不叫 `PythonRunner`）
- [ ] 无硬编码路径（用 SAF Uri，不用 `/sdcard/...`）
- [ ] 无 `Log.d` 遗留（只保留 `Log.e` 且带 tag）
- [ ] 所有 public 函数有 KDoc
- [ ] 无 `TODO` 无 owner 无日期
- [ ] 新增错误码在 `AvbToolError` 里定义
- [ ] 涉及分区写操作有明确 root 检查
- [ ] 新增子命令同步更新 `assets/commands_seed.json`

---

## 4. 文档同步（每个 M 阶段完成时）

- [ ] `dev-log/CHANGELOG.md` 加一条
- [ ] `dev-log/TODO.md` 划掉完成任务
- [ ] 新决策写入 `dev-log/DECISIONS.md`（含 ADR-ID）
- [ ] 新增/修改 UI 组件同步更新 `docs/standards/COMPONENT_GUIDELINES.md`
- [ ] 新增技术模块同步更新 `docs/tech/ARCHITECTURE.md` 的模块列表

---

## 5. 交付前最终验收

| # | 检查项 | 完成标志 |
|---|---|---|
| 1 | `./gradlew clean assembleDebug` 成功 | APK 存在 |
| 2 | 所有 30+ 子命令在 UI 可见 | 数量校验 |
| 3 | 抽样 5 个子命令运行时验收通过 | 手动测试记录 |
| 4 | dev-log 三个文件都有最新记录 | 人工检查 |
| 5 | 主流程无 crash（monkey 测试 30 秒无崩溃） | `adb shell monkey -p com.bingyin.materialyouprefs 100` |
| 6 | 权限清单与 README 声明一致 | 无多余权限 |

---

## 6. 未通过项处理规则

- **CI 门禁失败**：阻断合并，必须修复
- **运行时验收失败**：写 issue 到 GitHub，任务标为 partial
- **代码审查项遗漏**：本 PR 不合并，补完再提
- **文档不同步**：下一个 commit 内必须补齐
- **最终验收失败**：不发布 release，回到 M4 修复
