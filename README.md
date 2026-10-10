# Study With Miku · Android 版

[![Android APK](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml/badge.svg)](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml)

基于 [shshouse/StudyWithMiku](https://github.com/shshouse/StudyWithMiku) 的原生 Android 移植，使用 Kotlin、Jetpack Compose 和 Material 3 Expressive。1.2.7 保留原项目的完整在线功能：网易云／QQ 音乐、全部推荐歌单、MikuMod 官方账号、学习数据与日历云同步、在线人数、管理员在线状态、实时聊天室、历史消息、头像、表情包、歌曲和倒数日分享、一言与 IP 地区。

番茄钟、倒计时、正计时、计划、记录、三段场景和十一首内置音乐可离线使用。网络功能沿用原站服务，无需修改后端。

## 下载 APK

打开 [Android Release APK 1.2.7 私有草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-2813a9700863c1d80418)，下载 `StudyWithMiku-Android-1.2.7-release.apk`。包名为 `io.github.chorusfruit233.studywithmiku`，支持 Android 8.0 及以上。仓库保持私有；草稿仅对有仓库写入权限的用户可见。

[正式包构建与发布指南](android/RELEASING.md)：配置四个 Actions Secrets 后，在 Actions 运行 **Android signed release**，或推送版本标签，生成固定签名、关闭调试的 Release APK，并保存为私有草稿。当前 GitHub 连接没有 Secrets 管理权限，需要仓库管理员在设置页面填写；本次正式包已在本地签名，经 Actions 再次验证并上传。

向 `main` 推送 Android 源码会自动构建调试包，并运行单元测试与 Android 模拟器交互回归、运行 Lint 和验证 APK 签名，输出 APK、SHA-256 校验值和检查报告。也可在 Actions 中手动运行。由于账户的 Actions 产物存储额度已满，产物保存到私有 Release 草稿。

1.2.4 更换包名，作为独立应用安装，可与旧版共存。本机资料不会自动迁移，登录同一账号可恢复云端学习资料。

1.2.7 起提供固定 StudyWithMiku 密钥签名的正式 Release 包；后续保持同签名、同包名并提高 versionCode，可覆盖升级。此前调试 APK 的签名不同，首次切换不能覆盖安装；卸载会删除本机资料。密钥与密码不进入代码仓库。

启动图标支持 Android 8.0 自适应形状和 Android 13 单色主题；主题图标需要启动器支持并启用。

## 使用账号与同步

在“社区 → 账号”进入 MikuMod 官方网页登录，完成登录后自动返回应用。认证与权限由原站处理。首次遇到本地／云端均有记录时，选择使用云端、上传本地或合并，再点击确认；合并采用原作的逐日最大值和 ID 去重规则。切换账号时分别保存各账号和访客记录。

选择“使用云端资料”并确认后，会重新读取最新学习统计与日历，保存到本机后显示完成反馈；失败会保留本地资料与选择框，可直接重试。已接受的资料再次进入不重复弹窗；云端有更新而本地没有改动时自动读取，双方都有改动时再选择。1.2.3 统一网页与 Android 的资料比较，排除跨天、空记录、默认值和浮点格式造成的误报；真实冲突显示秒数与差异类别。

官方账号端到端登录、真实发言和云端读写仍需用户在手机上验收；不提供开发者代登录，不收集用户密码。具体验证范围见 [验证记录](android/VALIDATION.md)。

## 开发

Android Studio 打开 [android](android/)，使用 JDK 17、Android SDK 35：

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

功能映射、接口和原生适配见 [工程说明](android/README.md)。保留原作署名与 [GPL-3.0 许可](LICENSE)。

1.2.6 修正同步弹窗与账号操作、统一关于入口，并改进计时、计划删除与容量限制、记录翻月、歌词加载、歌单平台识别和窄屏播放布局。

1.2.7 添加固定密钥 Release 签名、正式包证书与 debug 标志验证，以及独立发布工作流。
