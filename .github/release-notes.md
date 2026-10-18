Study With Miku 的非官方原生 Android 移植，使用 Material 3 Expressive，支持 Android 8.0 及以上。

本版对齐上游 `2a19db4`（2026-10-16），覆盖自上一基线新增的 9 个提交：

- 手动上一首／下一首、列表选择和队列切换采用 1.5 秒重叠过渡；自动切歌保留最多 6 秒淡入淡出，主解码器就绪后平滑交接。
- 用户音量与过渡增益独立保存，显式静音保留；暂停、拖动、失败和超时清理第二路音频。后台过渡使用原生媒体服务和单调时钟。
- 初始聊天历史与分页合并已有实时消息，按 ID 去重；连接中断或发送被拒绝时提示失败，保留输入、引用和表情选择。
- 过期歌单请求不覆盖新选择、写入旧配置或触发旧播放回调，切回缓存歌单同样受保护。
- 核对旧设置默认值补全、播放索引和音量保存，保留所有网络歌单音频缓存、离线播放及后台计时功能。
- README 已删除整个“下载与安装”章节。

原作账号认证、学习资料与日历接口未变。逐项适配见 [UPSTREAM.md](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/UPSTREAM.md)，测试来源及范围见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。

下载 `StudyWithMiku-Android-1.2.12-release.apk` 安装。固定签名及包名 `io.github.chorusfruit233.studywithmiku` 延续正式版，可覆盖升级已有正式版；`SHA256SUMS.txt` 提供校验值，`SIGNING-CERTIFICATE.txt` 为公开证书，检查报告随附。安装包保留全部内置音视频，尚未启用 R8 和资源裁剪。

设备回归使用 Debug APK、合成音频和本地服务；真实账号、音乐供应商与签名包真机验收另行确认。

感谢原作者 [SHSHOUSE / StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。移植遵循 GPL-3.0，媒体与角色资源权利归各自创作者。
