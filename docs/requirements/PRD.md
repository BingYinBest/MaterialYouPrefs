# PRD — MaterialYouPrefs → AvbTool Android

## 1. 定位

把已有的 `MaterialYouPrefs` App（Compose + M3 骨架）**改造为** Android 上的 avbtool 全量子命令前端：

- 无 root、arm64-v8a
- UI 复用现有骨架（3 tab + Detail + Theme 不动）
- 数据层迁到 Room + ViewModel，预留动态拉参数的接口
- Python 侧通过 Chaquopy 调 patched `avbtool.py`，FEC 走原生 `libavbfec.so`

## 2. 目标用户

- Android 系统开发者 / ROM 开发者，需要在本机手机本地做 AVB 镜像的密钥管理、hashtree 计算、vbmeta 签名、镜像校验
- 高级用户（root 机、刷机机），但不依赖 root 权限

## 3. 功能范围（P0 必做，P1 加分）

### 3.1 avbtool 子命令分类

按现有 3 个 tab 映射：

| Tab | 现有分组名 | 改造后分组 | 包含子命令 |
|---|---|---|---|
| Home (首页) | — | **密钥管理** | `generate_rsa_key` `generate_ecdsa_p256_key` `generate_fec_key` `convert_rsa_to_pkcs1` `convert_pkcs1_to_pem` `rsa_util_sign` `rsa_util_verify` |
| Home | — | **Hashtree** | `add_hashtree_footer` `addvbmeta_footer` `removevbmeta_footer` `getvbmeta_footer_info` |
| Home | — | **vbmeta** | `create_vbmeta_image` `load_vbmeta_image` `extract_vbmeta_from_image` `dump_vbmeta_image` |
| Feature (功能) | — | **验证** | `verify_image` `verify_vbmeta` `verify_vbmeta_from_image` |
| Feature | — | **元数据操作** | `get_metadata` `get_image_size` `get_hashtree_offset` `get_header_block_size` `get_footer_offset` `get_auth_block_offset` `get_offset_of_vbmeta_image` `get_footer_block_size` |
| Feature | — | **算法工具** | `alg_md5` `alg_sha1` `alg_sha256` `alg_sha384` `alg_sha512` `alg_rfc3161` |
| Settings (设置) | — | **配置** | FEC 开关、输出目录、Python 运行时状态、缓存清理 |
| Settings | — | **关于** | avbtool 版本、AOSP HEAD、许可证、贡献 |

### 3.2 UI 映射

- 现有 `PrefData.kt` 的 3 组静态数据 → Room 表里的 avbtool 命令元数据
- 每个命令对应一条 `PrefItem`（id = 子命令名，icon 用 Material Icons 匹配语义）
- Detail 页展示：
  - 命令描述（从 `avbtool --help <cmd>` 抓取）
  - 参数表单（从 argparse help 解析出参数签名）
  - 输入文件选择（走 SAF）
  - 执行按钮 → 输出（stdout/stderr）→ 输出文件保存到 SAF 目录

### 3.3 FEC 策略

- 默认开启（`--generate_fec`）
- 提供 `--do_not_generate_fec` 开关
- FEC 强度参数在 UI 可选（默认 60）

### 3.4 文件访问

- 输入：用户通过 SAF 文档选择器选文件 → `content://` URI
- Bridge：Kotlin 把 `content://` 转成 `/saf/fd/<fd>` 虚拟路径（见 SAF_BRIDGE.md）
- 输出：写到 App 私有目录 + SAF 目录（默认 `Documents/AvbTool/<timestamp>`）

## 4. 非目标

- ❌ 不写真实分区（不刷 vbmeta / boot / system 到设备）
- ❌ 不做 vbmeta 校验绕过（不做 root 相关操作）
- ❌ 不支持 armv7 / x86（仅 arm64-v8a）
- ❌ 不 fork avbtool（用官方源码 + 最小 patch）

## 5. 验收

见 `REVIEW_CRITERIA.md`。
