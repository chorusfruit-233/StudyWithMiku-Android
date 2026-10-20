原生 Android 1.2.13 调试构建，支持 Android 8.0 及以上，targetSdk 37。

更新 AGP、Gradle、Kotlin、Compose、Material 3、Media3、OkHttp、Coil、协程及测试依赖，保留网络歌单缓存、离线播放、学习资料同步和后台计时。迁移详情见 [构建依赖](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/DEPENDENCIES.md)。

本条目为验证用 Debug 草稿。APK 使用调试签名，无法覆盖固定签名正式版。面向用户的正式包见 [公开 Release](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/latest)。

报告包含单元测试、Lint、API 35 与 API 37 设备回归及按 API 保存的组件截图。测试使用合成音频、图片、本地 HTTP 服务和模拟套接字，不使用真实账号或向生产聊天室发言。实际结果与范围见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。
