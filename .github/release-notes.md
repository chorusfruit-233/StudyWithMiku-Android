Study With Miku 的非官方原生 Android 移植，保留 Material 3 Expressive，支持 Android 8.0 及以上，targetSdk 37。

本版同步上游 [2bb2379](https://github.com/shshouse/StudyWithMiku/commit/2bb2379cfa0b04d413f1406ecf5f70aa494dddad)（2026-10-27）：

- 新增一言“水洼间轻盈跃动,有如水黾般自如”，出处为《夜の踊り子》。
- 内置语录由 47 条增至 48 条，保留原文、署名与顺序。
- 审阅上一基线之后的 11 个上游提交，更新[适配记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/UPSTREAM.md)，记录网页 npm 依赖更新的适用范围。

Debug 与签名 Release 各 104 项单元测试、Android 15／17 各 41 项设备测试全部通过，0 失败／错误／跳过，Lint 0 错误。已重新下载签名 APK，核对全部一言、固定证书、版本及文件校验值。

下载 `StudyWithMiku-Android-1.2.14-release.apk`。包名 `io.github.chorusfruit233.studywithmiku` 与固定正式证书沿用，可覆盖升级已有正式版。附带 SHA256SUMS、公开证书和 Release 检查报告。

构建与测试结果见[验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。设备回归使用 Debug APK、合成资料与本地服务。网络播放器、推荐歌单、离线缓存、学习资料同步、聊天室和后台计时继续保留。

感谢原作者 [SHSHOUSE / StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。移植遵循 GPL-3.0，媒体与角色资源权利归各自创作者。
