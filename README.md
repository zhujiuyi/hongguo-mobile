# 红果手机版

基于 [N3urda/hongguoTV](https://github.com/N3urda/hongguoTV) 的非官方安卓手机客户端。原生 Kotlin / Android View 界面通过 Media3 播放，默认支持 Android 6.0 及以上。

- 首页、随心看、分类、搜索、排行榜和个人片单适配手机浏览。搜索使用系统键盘，推荐试播支持上下滑动换剧。
- 点按播放画面显示图标控件。可拖动进度、选集或打开竖向倍速菜单。点「全屏观看」可切换横屏。在播放画面上滑播放下一集，下滑播放上一集。
- 保留本机收藏和续播、账号登录与同步、评论和弹幕。第三方接口由平台维护，服务可用性会随平台变化。

## 本地构建

安装 JDK 17 与 Android SDK 36，在 `ANDROID_HOME` 或 `kotlin-tv/local.properties` 中配置 SDK 路径。从仓库根目录运行以下命令：

```sh
./kotlin-tv/gradlew -p kotlin-tv :app:assembleDebug
adb install kotlin-tv/app/build/platform/outputs/apk/debug/app-universal-debug.apk
```

Windows PowerShell 用 `./kotlin-tv/gradlew.bat` 替换第一条命令中的 wrapper。调试包使用 `com.hongguotv.mobile.debug`，正式包使用 `com.hongguotv.mobile`。正式 APK 需使用自己的签名。签名配置和测试命令见 [原生工程说明](kotlin-tv/README.md)。

## 来源与许可证

本项目派生自 hongguoTV 的 [0.16.8 源码](https://github.com/N3urda/hongguoTV/releases/tag/kotlin-v0.16.8)，保留上游 Git 历史。应用源码采用 [GPL-3.0](LICENSE)，第三方代码和资源见 [NOTICE](kotlin-tv/NOTICE.md)。本项目与红果短剧及其运营方无隶属关系。上游代码许可证的范围仅涵盖代码，内容和接口使用须遵守各自授权及平台条款。
