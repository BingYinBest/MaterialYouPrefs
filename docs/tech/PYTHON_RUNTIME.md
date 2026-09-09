# Python 运行时（Chaquopy）

## 选型
Chaquopy（https://chaquo.com/chaquopy/），原因：
- 官方支持 Android arm64-v8a
- 支持 Python 3.13
- 无需自己交叉编译 CPython
- 支持 wheel 打包（`cryptography`, `pycryptodome` 等有 wheel）

## 版本
- Python 3.13.x
- Chaquopy 14.0+

## Gradle 配置（关键片段）
```kotlin
dependencies {
    implementation("com.chaquopy:python:3.13")
    implementation("com.chaquopy:cryptography:43.0")
    implementation("com.chaquopy:pyyaml:6.0")
}
python {
    version = "3.13"
}
```

## Python 依赖处理
| 依赖 | 用途 | 打包 |
|---|---|---|
| `cryptography` | 替代 avbtool 里的 `subprocess openssl` | Chaquopy wheel |
| `pyyaml` | 属性解析（可选） | Chaquopy wheel |
| 标准库 | `argparse`, `hashlib`, `struct`, `subprocess` 等 | Python 自带 |

## avbtool.py 的 OpenSSL 替换
AOSP 原版用 `subprocess.check_output(['openssl', ...])`，patched 版改用 `cryptography.hazmat.primitives.serialization` 完成 key 生成 / 提取 / 签名。

具体替换点：
- `dump_rsa_key` → RSA 私钥导出
- `generate_rsa_key` → `rsa.generate_private_key`
- `get_vbmeta_public_key_hash` → DER 编码 + SHA

## avbtool.py 放置
路径：`app/src/main/python/avbtool.py`
执行：`py.run("avbtool", ["-m", "avbtool"] + argv)`
