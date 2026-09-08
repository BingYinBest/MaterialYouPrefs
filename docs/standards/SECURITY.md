# 安全规范

## 数据
- 本 App **不收集任何用户数据**
- 不联网上报（avbtool.py 本身不调用网络）
- 无 analytics / crash reporting（默认关）

## 密钥
- checked-in `keystore/debug.keystore` 仅用于本地调试，**不得**用于生产发布
- Release 密钥只放 GitHub Secrets
- 用户在 UI 生成的 avb 签名密钥保存在 SAF 文档，不写 app 私目录

## 依赖
- 只用官方 Chaquopy 仓库
- 每次新增依赖记录到 `docs/tech/PYTHON_RUNTIME.md`
- 定期跑 `pip-audit`（在开发机）

## 权限
- AndroidManifest 最小权限：无额外 runtime 权限
- SAF 不需要 MANAGE_EXTERNAL_STORAGE
- 无 INTERNET 权限（除非未来加更新检查）
