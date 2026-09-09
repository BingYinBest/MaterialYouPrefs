# DEVLOG — 每日开发流水账

> **目的**：与 `CHANGELOG.md`（倒序动作记录）、`TODO.md`（里程碑 checklist）、`DECISIONS.md`（决策记录）并列，本文件是**按日期归档**的开发流水账。
>
> **写入规范**：
> - 每天一个二级标题 `## YYYY-MM-DD`
> - 每条以 `### HH:MM` 或 `### HH:MM – 短标题` 开头
> - 内容包含：动作、commit SHA、CI Run 号、遇到的问题、下一步
> - 不追求精炼，追求**可追溯**——半年后能查出来为什么这么做

---

## 2026-09-09

### 21:14 — M3.4 完成，Run 163 绿（`8eecab3`）

**fetchHelp 真实现**。两个 commit：
- `c48ff81` `python_main.py` 加 `__help__` 虚拟命令：`{"commandName": "__help__", "args": ["<subcmd>"]}` → dispatch 到 `<subcmd> --help`，返回 argparse help 文本
- `8eecab3` `AvbToolRunnerImpl.fetchHelp()` 从空实现改成真的调 `python_main.run` + `__help__` 命令，然后管道到 `AvbHelpParser.parse()` 返回 `List<CommandParam>`

**效果**：
- `CommandRepository.getByIdOrFetch()` 现在能真正从 avbtool 拉参数元数据
- Room 里 `paramsJson` 24h 后不再靠种子，而是从 --help 输出刷新
- 失败不抛异常，返回 `emptyList()` 让调用方 fallback

**下一步**：M3.3 v2（纯 Python RSA）或 M3.5（SAF 桥 + FEC）。

### 21:44 — M3.3 v1 失败复盘 + 签名固定完成，Run 151 绿

**当前 HEAD**：`931e7cf`。签名固定完成，M3.3 openssl→cryptography patch 因依赖不可用而**回滚**。

**本轮 11 个 commit**（含 Web UI 试错）：
- `6f37aa0` `pip { install("cryptography==43.0.1") }` 加进 build.gradle.kts
- `6507c8d` 用户 Web UI 上传 patched avbtool.py（cryptography 版）
- `f8237d6` 撤回 cryptography + 加固定 `signingConfig { devFixed }` 指向 `keystores/dev.keystore`
- 若干 Web UI 中间态（Create keystores / Delete keystores / 试错上传）
- `a601808` 用户回滚 avbtool.py 到原始版（openssl subprocess 版）
- `931e7cf` Delete dev.keystore（顶层误传，真正 keystore 在 `keystores/dev.keystore`）

**M3.3 v1 失败根因**（Run 122）：

```
Looking in indexes: https://pypi.org/simple, https://chaquo.com/pypi-13.1
Collecting cryptography==43.0.1
  Downloading cryptography-43.0.1.tar.gz (686 kB)
Preparing wheel metadata: finished with status 'error'
FileNotFoundError: [Errno 2] No such file or directory: 'maturin'
```

- `cryptography` 官方 PyPI 只有 manylinux wheel，**没有 Android arm64-v8a wheel**
- `https://chaquo.com/pypi-13.1/` **不 mirror** cryptography（curl 验证 HTTP 404）
- 只能拉 sdist，sdist 用 `maturin`（Rust）做 build backend，GitHub runner 没装

**教训**：Python 密码学库要优先评估「Android arm64 wheel 是否可用」。没有 wheel 就要考虑纯 Python 实现（`pow(a,d,n)` 就是可行的路径）。

**签名固定**：
- 新增 `keystores/dev.keystore`（2754 B, PKCS12, RSA-2048, `CN=AvbTool Dev`, 有效期 10000 天）
- `build.gradle.kts` 加 `signingConfigs { devFixed { storeFile = "keystores/dev.keystore" } }`，debug/release 都指向它
- 参数硬编码在 build.gradle.kts：store=`avbtool-dev-store` / alias=`avbtool-dev`
- **警告**：这是 dev keystore，公开到 repo。**将来 release 一定走 CI Secrets**，不能把 release keystore 提交到 repo

### 21:20 — M3.2 交付 + 文档体系完善

**签名 tag**：`m3.2-avbtool-vendored` @ `10f5802c`

**交付**：
- 用户上传 AOSP `avbtool.py`（200 KB, 4935 行, HEAD `386fb904`）到 `app/src/main/python/avbtool.py`
- `python_main.py` 更新为完整 dispatch：`version` 走本地处理，其他命令 `import avbtool` + argparse 桥
- CI Run 115 绿

**本轮文档改进**：
- 新增 `dev-log/DEVLOG.md`（本文件）：日期归档的开发流水账
- 新增 `docs/README.md`：docs 根入口，把 requirements/tech/standards/workflow 四个子目录串起来
- 更新 `claude.md`：补齐 DEVLOG 和 docs/README 索引；补 M3 的 5 个子里程碑清单；补「AI 助手工作法」的具体路径
- 更新 `dev-log/TODO.md`：勾掉 M2.5 / M2.6+ / M3.1 / M3.2；标注 T-M2.5-seed 已完成（M2.6+ 走 fallback）
- 更新 `dev-log/CHANGELOG.md`：追加 M3.1 + M3.2 完整动作

### 21:17 — M3.2 上传完成，CI 绿

用户通过 GitHub Web UI 上传 `avbtool.py`（201397 B, md5 `abff24c4ee8f696151432e6f2a4766c9`）。
我推送 `python_main.py` 完整 dispatch 版本，commit `10f5802c`，CI Run 115 绿。

### 21:10 — M3.1 完成，Chaquopy 15 API 踩坑总结

**Run 109 绿**，`2a7afa8a`。M3.1 交付 6 commits：
- `4ceb0559` 插件初版（DSL 错，被推翻）
- `605b3746` 修 Chaquopy 15 DSL：`chaquopy { defaultConfig { version = "3.12" } }`
- `86d1e703` 移除不存在的 `libs.chaquopy.python` 依赖
- `9873d7f3` `python_main.py` 入口
- `cf5230ef` `AvbToolRunnerImpl` 简化版
- `b32e1996` Application 切换 runner
- `2a7afa8a` 修 Chaquopy 15 API：`PyObject` 而非 `PyModule`，删除 `useInstance/importModule/getPlatform`

**Chaquopy 15 关键教训**：
1. `PyModule` 类不存在，用 `py.getModule(name)` 拿 `PyObject`
2. `Python.useInstance()` / `py.importModule()` / `py.getPlatform()` 不存在
3. `PyException.value` 不存在，用 `.message`
4. `PyObject.asString()` 不存在，用 `.toString()`
5. DSL：`chaquopy { defaultConfig { version = "3.12"; pip { install(...) } } }`
6. `abiFilters` 在 `android.defaultConfig.ndk`，不在 chaquopy 块
7. 不写 `implementation(libs.chaquopy.python)`，插件自动注入 runtime AAR

### 20:48 — M2.6+ 完成

Run 93 绿，`e223168`。Home 4 卡片（版本 / 终端入口 / 常用命令 / 运行时状态）+ Terminal 独立路由 + Feature 按 group 分组 + Room schema v2（加 tab 列 + fallbackToDestructiveMigration）。14 files, +1016/-115。

### 20:28 — TerminalScreen TopAppBar 缺 @OptIn 修复

`e223168` 修 CI Run 92 → 93 转绿。

---

## 2026-09-08

### 21:10 — 建 `/sdcard/Download/M32_upload/` 目录，放 avbtool.py 供用户下载

### 20:55 — Run 96 失败排错：Chaquopy 15 Python API 变了

### 20:48 — Run 95 失败：`libs.chaquopy.python` 别名解析失败

### 20:41 — Run 94 失败：Chaquopy 15 DSL 错误（`defaultVersion/sourceDirs/python/cryptography` 全部 Unresolved）

### 19:30 — M3.1 推送第一版（`4ceb0559`）

---

（更早的历史记录见 `CHANGELOG.md`）
