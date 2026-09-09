# Git / CI 规范

## 分支
- `main`：CI 通过才可合并
- `feat/*`：新功能
- `fix/*`：修复
- `chore/*`：文档/工具

## Commit
Conventional Commits：
- `feat: add xxx`
- `fix: xxx`
- `docs: xxx`
- `chore: xxx`
- `build: xxx`

## GitHub Actions

工作流：`.github/workflows/build.yml`

触发：
- `push` 到 `main` / `feat/*` / `fix/*`
- `pull_request` 到 `main`
- `workflow_dispatch` 手动

Job 步骤：
1. checkout
2. setup-java (17, temurin)
3. download Android SDK cmdline-tools + NDK r26
4. setup-python (3.13) — Chaquopy 需要
5. `./gradlew assembleRelease`
6. upload artifact `*.apk`
7. 如果 `on: tag: v*.*.*` → 创建 GitHub Release + 上传 APK

## 缓存
- Gradle wrapper
- ~/.gradle
- ~/.android
- ~/.m2

## 密钥
- Release 签名使用 GitHub Secrets：`KEYSTORE_B64`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`
- 未设置时回退到 `keystore/debug.keystore`（仅测试用）
