Study With Miku 非官方原生 Android 移植，支持 Android 8.0 及以上，保留 Material 3 Expressive。

- 跟进上游 main `981c9c9`（2026-11-15），覆盖 17 个新提交。
- 同步支持版本校验和并发合并，保留上传期间的新改动；部分上传失败后安全重试，兼容旧服务协议。
- 聊天新增持久发送队列、服务器确认、去重重试和失败草稿，断网后可继续发送。
- 退出登录会注销服务端当前凭据；失败时保留账号。
- 手动切歌快速淡出、淡入，保留静音；更新通知音量过渡。
- 增加 5 张表情及一言，更新原作日志。音乐模块开关、缓存与横屏适配继续保留。

`StudyWithMiku-Android-1.2.18-release.apk` 沿用原包名与固定证书，可覆盖升级已有正式版。附带 SHA256SUMS、公开证书及检查报告。

[更新记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/CHANGELOG.md) · [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)

Debug／Release 各 134 项单元测试、Android 15／17 各 59 项设备回归通过，Lint 0 错误。设备回归使用 Debug APK、合成资料与本地服务。感谢原作者 [SHSHOUSE](https://github.com/shshouse/StudyWithMiku)，移植遵循 GPL-3.0。
