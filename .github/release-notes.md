Study With Miku 的非官方原生 Android 移植，保留 Material 3 Expressive，支持 Android 8.0 及以上。

本版更新 Android 工具链和依赖：

- AGP 9.4.1、Gradle 9.6.0、Kotlin／Compose Compiler 2.4.21，迁移 AGP 内置 Kotlin。
- compileSdk／targetSdk 升至 Android 17（API 37），最低版本仍为 API 26，Build Tools 为 37.0.0。
- 更新 Compose BOM、Material 3 Expressive、Core、Activity、Lifecycle、Media3、OkHttp、协程及测试依赖。
- 迁移 Coil 3.6.3，保留网络头像／封面、更新配图与动态 GIF 表情。
- 自建局域网音乐源可在“添加网络歌单 → 音乐源设置”中申请 Android 17 的局域网访问权限。
- 构建工作流增加 API 37 设备回归，并保留 API 35 回归与各自原始报告。

依赖版本及迁移说明见 [DEPENDENCIES.md](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/DEPENDENCIES.md)，测试来源与结果见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。

下载 `StudyWithMiku-Android-1.2.13-release.apk`。包名 `io.github.chorusfruit233.studywithmiku` 与固定正式证书沿用，可覆盖升级已有正式版。附带 SHA256SUMS、公开证书和检查报告。网络歌单缓存、离线播放、切歌过渡、学习资料同步和后台计时继续保留。

设备回归使用 Debug APK、合成资料及本地服务；详细验收范围见验证记录。安装包保留全部内置音视频，尚未启用 R8 和资源裁剪。

感谢原作者 [SHSHOUSE / StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。移植遵循 GPL-3.0，媒体与角色资源权利归各自创作者。
