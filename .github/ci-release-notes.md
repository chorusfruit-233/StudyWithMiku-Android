原生 Android 1.2.14 调试构建，支持 Android 8.0 及以上，targetSdk 37。

同步上游 2026-10-27 的一言投稿，内置语录增至 48 条；更新适配基线与说明。变更范围见[上游适配记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/UPSTREAM.md)。

本条目为验证用 Debug 草稿。APK 使用调试签名，无法覆盖固定签名正式版。面向用户的正式包见 [公开 Release](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/latest)。

报告包含单元测试、Lint、API 35 与 API 37 设备回归及按 API 保存的组件截图。测试使用合成音频、图片、本地 HTTP 服务和模拟套接字，不使用真实账号或向生产聊天室发言。实际结果与范围见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。
