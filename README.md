# Study With Miku · Android 离线版

[![Android APK](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml/badge.svg)](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml)

基于 [shshouse/StudyWithMiku](https://github.com/shshouse/StudyWithMiku) 的原生 Android 离线移植，使用 Kotlin、Jetpack Compose 和 Material 3 Expressive。

保留番茄钟、倒计时、正计时、Miku 场景、计划和学习记录，以及播放器、收藏、本地音乐导入和自建歌单。安装包内置 3 段视频场景和 11 首音乐，不申请网络权限，不包含在线人数、聊天室、登录或云同步。

## 下载 APK

打开 [GitHub Actions](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml)，选择成功的构建，在 **Artifacts** 中下载 `StudyWithMiku-offline-APK` 并解压。支持 Android 8.0 及以上。

向 `main` 推送 Android 源码会自动构建；也可以在 Actions 页面点击 **Run workflow** 手动构建。每次构建包含编译、单元测试、Android Lint 和 APK 签名校验，输出 APK、SHA-256 校验值和检查报告。

APK 使用固定的调试签名密钥，密钥仅保存在仓库 Secret `ANDROID_DEBUG_KEYSTORE_B64` 中，源码不包含签名密钥。该签名与首次本地交付的 APK 相同；后续构建可以覆盖安装。正式发布请使用自己的发行签名和发布流程。

## 开发

在 Android Studio 中打开 [android](android/) 目录，使用 JDK 17 和 Android SDK 35。

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

完整功能、架构和资源说明见 [Android 工程文档](android/README.md)，本地验证结果见 [验证记录](android/VALIDATION.md)。本仓库保留原作署名和 [GPL-3.0 许可](LICENSE)。
