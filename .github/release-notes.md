Study With Miku 的非官方原生 Android 移植，保留 Material 3 Expressive，支持 Android 8.0 及以上。

本版更新 Android 工具链和依赖：

- AGP 9.4.1、Gradle 9.6.0、Kotlin／Compose Compiler 2.4.21，迁移 AGP 内置 Kotlin。
- compileSdk／targetSdk 升至 Android 17（API 37），最低版本仍为 API 26，Build Tools 为 37.0.0。
- 更新 Compose BOM、Core、Activity、Lifecycle、Media3、OkHttp、协程及测试依赖；Material 3 使用 1.5.0-beta01 保留现有 Expressive 接口。
- 迁移 Coil 3.6.3，保留网络头像／封面、更新配图与动态 GIF 表情。
- 自建局域网音乐源可在“添加网络歌单 → 音乐源设置”中申请 Android 17 的局域网访问权限。
- 构建工作流增加 API 37 设备回归，并保留 API 35 回归与各自原始报告；升级 Espresso 3.7.0 和 JUnit 扩展 1.3.0，适配新版系统的测试接口。

依赖版本及迁移说明见 [DEPENDENCIES.md](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/DEPENDENCIES.md)，测试来源与结果见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。

Debug 与签名 Release 各 104 项单元测试、Android 15／17 各 41 项设备测试全部通过，0 失败／错误／跳过，Debug 与 Release Lint 均为 0 错误。原始设备报告和模拟器环境记录已归档。

下载 `StudyWithMiku-Android-1.2.13-release.apk`。包名 `io.github.chorusfruit233.studywithmiku` 与固定正式证书沿用，可覆盖升级已有正式版。附带 SHA256SUMS、公开证书和检查报告。网络歌单缓存、离线播放、切歌过渡、学习资料同步和后台计时继续保留。

设备回归使用 Debug APK、合成资料及本地服务；详细验收范围见验证记录。安装包保留全部内置音视频，尚未启用 R8 和资源裁剪。

感谢原作者 [SHSHOUSE / StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。移植遵循 GPL-3.0，媒体与角色资源权利归各自创作者。
