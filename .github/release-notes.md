Study With Miku 的非官方原生 Android 移植，使用 Material 3 Expressive，支持 Android 8.0 及以上。

正式 Release APK 使用固定 StudyWithMiku 证书，包名为 `io.github.chorusfruit233.studywithmiku`，关闭调试。仓库、源码和已发布的 Release 公开，附件可直接下载。

本版沿用 1.2.6 的功能与交互修正：同步弹窗采用三个同层级单选方案和一个确认操作；社区区分首次设置、上传本地和检查云端；关于集中到设置入口。保留计时、计划、记录、歌词、网络播放器、QQ／网易链接识别和窄屏布局修正。

支持原作网络播放器、10 个推荐歌单、MikuMod 官方登录、学习／日历云同步、在线状态与聊天室。计时、计划、记录、三段 Miku 场景和 11 首内置音乐可离线使用。

下载 `*-release.apk` 安装。`SHA256SUMS.txt` 提供 APK 校验值，`SIGNING-CERTIFICATE.txt` 提供公开证书资料，`Android-release-check-reports.zip` 提供 Release 检查报告。安装包保留全部离线音视频，尚未启用 R8 和资源裁剪。

后续同包名、同签名且更高 `versionCode` 的正式版可覆盖升级。此前 Debug APK 的签名不同，首次切换不能直接覆盖；先保存需要的资料，卸载会清除本机资料。

构建通过 Release 单元测试、Lint、APK 签名、固定证书与包名检查。真实账号端到端与签名正式包真机验收尚未完成，详细范围见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。

感谢原作者 [SHSHOUSE / StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。移植遵循 GPL-3.0，媒体与角色资源权利归各自创作者。完整变化见 [更新记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/CHANGELOG.md)。
