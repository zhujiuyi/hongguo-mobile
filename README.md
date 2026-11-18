# 红果手机版

红果手机版基于 N3urda 的 hongguoTV 改造，将原有 Android TV 客户端适配为便于手机竖屏浏览和触屏操作的安卓应用。项目使用原生 Kotlin、Android View 和 Media3。 [上游项目 N3urda/hongguoTV](https://github.com/N3urda/hongguoTV)

## 下载

正式版支持 Android 6.0 及以上，在 GitHub Releases 下载通用 APK 后安装。 [正式版下载](https://github.com/zhujiuyi/hongguo-mobile/releases/latest)

## 手机端操作

- 首页、随心看、分类、搜索、排行榜和个人片单适配手机浏览。搜索使用系统键盘，推荐试播支持上下滑动换剧。
- 点按播放画面显示图标控件。可拖动进度、选集或打开竖向倍速菜单。点「全屏观看」可切换横屏。在播放画面上滑播放下一集，下滑播放上一集。通过左上角的箭头返回上一级。
- 保留本机收藏和续播、账号登录与同步、评论和弹幕。第三方接口由平台维护，服务可用性会随平台变化。

## 本地构建

安装 JDK 17 与 Android SDK 36，在 `ANDROID_HOME` 或 `kotlin-tv/local.properties` 中配置 SDK 路径。从仓库根目录运行下面的命令即可构建和安装调试版。

```sh
./kotlin-tv/gradlew -p kotlin-tv :app:assembleDebug
adb install kotlin-tv/app/build/platform/outputs/apk/debug/app-universal-debug.apk
```

Windows PowerShell 用 `./kotlin-tv/gradlew.bat` 替换第一条命令中的 wrapper。调试包使用 `com.hongguotv.mobile.debug`，正式包使用 `com.hongguotv.mobile`。正式 APK 需使用自己的签名。签名配置和测试命令见 [原生工程说明](kotlin-tv/README.md)。

## 来源与许可证

手机版以 hongguoTV 0.16.8 为基础，保留上游 Git 历史。每个手机版 Release 的标签对应当次发布的源代码。 [上游 0.16.8 源码](https://github.com/N3urda/hongguoTV/tree/kotlin-v0.16.8) · [GPL-3.0](LICENSE) · [第三方许可与致谢](kotlin-tv/NOTICE.md)

本项目是非官方客户端。内容与接口的使用遵循各自的授权和平台条款。
