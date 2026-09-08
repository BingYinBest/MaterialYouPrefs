# 构建与测试

## 本地构建
```bash
cd /root/avbtool-android
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK
./gradlew lintDebug              # 静态检查
./gradlew testDebugUnitTest      # 单元测试
```

## 手动 smoke test
用 Android Studio Device Explorer 把 APK 安装到测试机，跑：
1. 生成 RSA key
2. 生成 vbmeta.img
3. dump_vbmeta_image 校验
4. 生成 hashtree（开启 FEC）
5. dump_hashtree_footer 看 fec_num_roots

## CI 构建
见 `docs/standards/GIT_CI.md`。Actions 里：
- 缓存 Gradle / SDK
- 下载 NDK r26
- 运行 `assembleRelease`
- 上传 artifact
- tag `v*.*.*` 时创建 Release
