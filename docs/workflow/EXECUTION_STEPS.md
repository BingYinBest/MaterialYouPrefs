# 执行步骤 — MaterialYouPrefs → AvbTool

## M0 文档体系 ✅

- [x] 建 `feat/avbtool` 分支
- [x] 迁移 tech/standards/workflow 到本仓库
- [x] 重写 claude.md / PRD / ARCHITECTURE / DATA_LAYER / EXECUTION_STEPS
- [x] 重建 dev-log（CHANGELOG/TODO/DECISIONS）

## M1 依赖集成

- [ ] 在 `gradle/libs.versions.toml` 加：
  - `com.chaquo.python` (AGP 8.5 对应版本)
  - `androidx.room:room-runtime`
  - `androidx.room:room-ktx`
  - `androidx.room:room-compiler` (KSP)
- [ ] 在 `app/build.gradle.kts` 引入 Chaquopy plugin、Room KSP
- [ ] 添加 `app/src/main/python/` 目录 + 最小 `python_main.py`
- [ ] `./gradlew assembleDebug` 通过

## M2 数据层改造

- [ ] 新建 `data/` 下：`CommandEntity.kt` / `CommandDao.kt` / `AppDatabase.kt` / `CommandRepository.kt` / `ExecutionEntity.kt` / `ExecutionDao.kt`
- [ ] 新建 `assets/commands_seed.json`（30+ 条命令种子）
- [ ] `Application.onCreate` 初始化 Room + 播种
- [ ] 新建 `viewmodel/HomeViewModel.kt`，从 Room 读数据
- [ ] `HomeScreen` 改数据源（不再读 `PrefData.homeGroups`）
- [ ] FeatureScreen / SettingsScreen 同处理
- [ ] 单元测试：CommandDaoTest（in-memory DB）

## M3 avbtool 集成

- [ ] 从 `/root/avb/avbtool.py` 复制到 `app/src/main/python/avbtool.py`，按 `AOSP_PATCH.md` 打补丁
- [ ] 编写 `python_main.py`：接收 `(cmd, args)` → 调 `avbtool.main` → 返回 JSON
- [ ] 从 AOSP `external/avb/libavb/libavb/src/fec/fec_rs.c` 提取，写 CMakeLists.txt，NDK 编出 `libavbfec.so`
- [ ] 放入 `app/src/main/jniLibs/arm64-v8a/libavbfec.so`
- [ ] 编写 `avb/AvbToolRunner.kt`：`py.run("python_main.execute")`
- [ ] 编写 `avb/SAFBridge.kt`：`content://` → `/saf/fd/<fd>`
- [ ] Chaquopy 里 `dlopen` 加载 libavbfec.so 的胶水代码

## M4 UI 接真实动作

- [ ] 改造 `DetailScreen.kt`：
  - 加载命令详情（描述 + 参数表单）
  - 参数表单按 `paramsJson` 动态渲染
  - 文件类型参数走 SAF picker
  - 执行按钮触发 `DetailViewModel.execute`
  - 输出展示：stdout / stderr / 输出文件列表
- [ ] `SettingsScreen` 加 FEC 开关、输出目录选择、缓存清理、运行时状态展示
- [ ] fallback：Room 失败时退回 `PrefData`
- [ ] 空态 / 加载态 / 错误态 UI
- [ ] 单元测试：DetailViewModelTest

## M5 CI + 发布

- [ ] 更新 `.github/workflows/build.yml`：
  - 装 NDK + 编 `libavbfec.so`
  - 装 Python 3.13（Chaquopy 会拉）
  - `./gradlew assembleRelease`
  - 上传 APK 到 GitHub Release
- [ ] lint 通过
- [ ] 装到 arm64 手机验证：
  - 3 tab 显示 30+ 命令
  - 点击 `generate_rsa_key` → 生成密钥
  - 点击 `add_hashtree_footer` → 加页脚
  - 点击 `create_vbmeta_image` → 签名
  - 点击 `verify_image` → 校验通过
- [ ] 按 REVIEW_CRITERIA.md 全过 → PR 回 main

## 完成

- [ ] PR merge 到 main
- [ ] 更新 CHANGELOG 打 tag `v1.0.0-avbtool`
