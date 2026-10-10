Study With Miku 的非官方原生 Android 移植，使用 Material 3 Expressive，支持 Android 8.0 及以上。

正式 Release APK 使用固定 StudyWithMiku 证书，包名为 `io.github.chorusfruit233.studywithmiku`，关闭调试。仓库、源码和已发布的 Release 公开，附件可直接下载。

对齐原作 2026-10-09 更新记录与 10 月 10 日后续调整：

- 新增“我的网络歌单”，自定义歌单成功加载后保存平台、ID 与名称，支持重启后切换、去重和确认删除。
- 接入原作歌单名称接口，名称获取失败时显示 ID；曲目加载失败时保留上次有效歌单，输入支持键盘确认。
- 聊天室支持选择消息回复、引用摘要和取消回复，兼容原作 `[reply:消息id]正文` 格式。引用未加载时仍可阅读正文，回复中的表情与分享卡片正常显示。
- 同步 47 条当前一言及署名，内置原作更新记录增加到 31 条。

支持原作网络播放器、10 个推荐歌单、MikuMod 官方登录、学习／日历云同步、在线状态与聊天室。计时、计划、记录、三段 Miku 场景和 11 首内置音乐可离线使用。

下载 `*-release.apk` 安装。`SHA256SUMS.txt` 提供 APK 校验值，`SIGNING-CERTIFICATE.txt` 提供公开证书资料，`Android-release-check-reports.zip` 提供 Release 检查报告。安装包保留全部离线音视频，尚未启用 R8 和资源裁剪。

后续同包名、同签名且更高 `versionCode` 的正式版可覆盖升级。此前 Debug APK 的签名不同，首次切换不能直接覆盖；先保存需要的资料，卸载会清除本机资料。

构建通过 Release 单元测试、Lint、APK 签名、固定证书与包名检查。真实账号端到端与签名正式包真机验收尚未完成，详细范围见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。

感谢原作者 [SHSHOUSE / StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。移植遵循 GPL-3.0，媒体与角色资源权利归各自创作者。完整变化见 [更新记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/CHANGELOG.md)。
