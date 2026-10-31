Study With Miku 非官方原生 Android 移植，支持 Android 8.0 及以上，保留 Material 3 Expressive。

- 对齐上游 `bcc6627`，同步学习资料、日历和用户资料的独立 API 地址 `https://mikumods.com`。
- 登录及账号设置继续使用 `https://mikumod.com`，接口调用保留原有 Bearer 与 Cookie 隔离规则。
- 删除工具链升级时临时添加的诊断导出工作流，保留验证、签名构建和核验发布。

下载 `StudyWithMiku-Android-1.2.15-release.apk`，沿用原包名与固定证书，可覆盖升级已有正式版。附带 SHA256SUMS、公开证书及检查报告。

[上游适配说明](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/UPSTREAM.md) · [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)

设备回归使用 Debug APK、合成资料与本地服务，真实账号操作未作端到端验收。感谢原作者 [SHSHOUSE](https://github.com/shshouse/StudyWithMiku)，移植遵循 GPL-3.0。
