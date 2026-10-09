# Study With Miku · Android 版

[![Android APK](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml/badge.svg)](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml)

基于 [shshouse/StudyWithMiku](https://github.com/shshouse/StudyWithMiku) 的原生 Android 移植，使用 Kotlin、Jetpack Compose 和 Material 3 Expressive。1.2.3 接入原项目的完整在线功能：网易云／QQ 音乐、全部推荐歌单、MikuMod 官方账号、学习数据与日历云同步、在线人数、管理员在线状态、实时聊天室、历史消息、头像、表情包、歌曲和倒数日分享、一言与 IP 地区。

番茄钟、倒计时、正计时、计划、记录、三段场景和十一首内置音乐可离线使用。网络功能沿用原站服务，无需修改后端。

## 下载 APK

打开 [Releases](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases)，选择最新的 `Android APK 1.2.3` 草稿，下载 `.apk`。支持 Android 8.0 及以上。仓库保持私有；草稿仅对有仓库写入权限的用户可见。

向 `main` 推送 Android 源码会自动编译、测试、运行 Lint 和验证 APK 签名，输出 APK、SHA-256 校验值和检查报告。也可在 Actions 中手动运行。由于账户的 Actions 产物存储额度已满，产物保存到私有 Release 草稿。

APK 使用每次构建自动生成的调试签名。已安装不同签名版本时，需要先卸载再安装；卸载会删除本机数据。工程不包含私有签名密钥，正式发布应配置固定发行签名。

## 使用账号与同步

在“社区 → 账号”进入 MikuMod 官方网页登录，完成登录后自动返回应用。认证与权限由原站处理。首次遇到本地／云端均有记录时，选择使用云端、上传本地或合并；合并采用原作的逐日最大值和 ID 去重规则。切换账号时分别保存各账号和访客记录。

“使用云端资料”会重新读取最新学习统计与日历，保存到本机后显示完成反馈；失败会保留本地资料与选择框，可直接重试。已接受的资料再次进入不重复弹窗；云端有更新而本地没有改动时自动读取，双方都有改动时再选择。1.2.3 统一网页与 Android 的资料比较，排除跨天、空记录、默认值和浮点格式造成的误报；真实冲突显示秒数与差异类别。

官方账号端到端登录、真实发言和云端读写仍需用户在手机上验收；不提供开发者代登录，不收集用户密码。具体验证范围见 [验证记录](android/VALIDATION.md)。

## 开发

Android Studio 打开 [android](android/)，使用 JDK 17、Android SDK 35：

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

功能映射、接口和原生适配见 [工程说明](android/README.md)。保留原作署名与 [GPL-3.0 许可](LICENSE)。
