Study With Miku 非官方原生 Android 移植，支持 Android 8.0 及以上，保留 Material 3 Expressive。

- 设置 → 功能模块新增“音乐模块”总开关，默认开启。
- 关闭后隐藏音乐页、迷你播放器和播放面板，停止播放与缓存下载，取消未完成的歌单加载。
- 聊天歌曲卡片仍可阅读，关闭播放入口与封面加载。
- 保留歌单、收藏、队列和缓存；重新开启后等待手动播放或继续下载。
- 开关仅作用于本机，重启、同步和切换账号不会重置；计时与完成语音独立保留。

下载 `StudyWithMiku-Android-1.2.17-release.apk`，沿用原包名与固定证书，可覆盖升级已有正式版。附带 SHA256SUMS、公开证书及检查报告。

[更新记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/CHANGELOG.md) · [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)

Debug／Release 各 105 项单元测试、Android 15／17 各 55 项设备回归通过，Lint 0 错误。设备回归使用 Debug APK、合成资料与本地服务。感谢原作者 [SHSHOUSE](https://github.com/shshouse/StudyWithMiku)，移植遵循 GPL-3.0。
