# 日常开发循环

## 每次开始前
1. 读 `claude.md` 第 2、3 节（硬约束 + 已确认事实）
2. 读 `dev-log/TODO.md` 找当前任务
3. 读相关的 `docs/*` 文件

## 开发中
- 遇到技术事实 → 先看 `/root/avb` 源码（`grep` / `read_file_part`）
- 涉及新方向 → 更新 `dev-log/DECISIONS.md`

## 提交前 checklist
- [ ] `./gradlew assembleDebug` 通过
- [ ] 相关 docs 更新
- [ ] `dev-log/CHANGELOG.md` 追加条目
- [ ] `dev-log/TODO.md` 状态更新
- [ ] commit message 符合 Conventional Commits
