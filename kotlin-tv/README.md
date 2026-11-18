# 红果手机版原生工程

`:app` 使用 Android View 与 Media3，`:core` 提供共享的数据和播放逻辑。默认构建最低 Android 6.0（API 23）。Android 5 兼容构建使用 `-PlegacyAudio=true`，其 FFmpeg 工具链见 [兼容模块](decoder-ffmpeg/README.md)。

## 构建与检查

使用 JDK 17 和 SDK 36。CI 安装 Build Tools 36.0.0。从仓库根目录执行：

```sh
./kotlin-tv/gradlew -p kotlin-tv :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

本地改动可运行对应任务，按 `--tests` 指定测试类。默认 debug APK 位于 `kotlin-tv/app/build/platform/outputs/apk/debug/`。

## 签名

`assembleRelease` 生成正式变体。可通过未跟踪的 `kotlin-tv/keystore.properties` 配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，或设置 `HONGGUO_MOBILE_KEYSTORE`、`HONGGUO_MOBILE_STORE_PASSWORD`、`HONGGUO_MOBILE_KEY_ALIAS`、`HONGGUO_MOBILE_KEY_PASSWORD`。提供密钥配置后，使用同一证书签署后续更新。TV 版的发行记录和脚本用于追溯上游版本。
