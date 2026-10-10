# Study With Miku · Android

[![Release](https://img.shields.io/github/v/release/chorusfruit-233/StudyWithMiku-Android)](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/latest)
[![Android CI](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml/badge.svg)](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/workflows/android-offline.yml)
[![GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

和 Miku 一起学习、听歌，记录每一天的专注时间。

基于 [shshouse/StudyWithMiku](https://github.com/shshouse/StudyWithMiku) 的非官方原生 Android 移植，使用 Kotlin、Jetpack Compose、Media3 和 Material 3 Expressive。保留原项目的网络播放器、推荐歌单、账号同步与聊天室，同时提供离线计时、内置音乐和本地计划。网络功能沿用原站服务。

## 下载与安装

**[下载最新正式版](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/latest)** · [全部已发布版本](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases)

当前正式版为 **1.2.7**，可直接下载 [StudyWithMiku-Android-1.2.7-release.apk](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/download/v1.2.7/StudyWithMiku-Android-1.2.7-release.apk)。仓库、源码及已发布的 Release 均公开，下载无需仓库权限。

| 项目 | 信息 |
| --- | --- |
| 系统要求 | Android 8.0（API 26）及以上 |
| 包名 | `io.github.chorusfruit233.studywithmiku` |
| 正式签名 | 固定 StudyWithMiku 证书，关闭调试 |
| 安装包大小 | 1.2.7 约 129 MB，包含全部离线音乐与场景视频 |

Release 附带 `SHA256SUMS.txt`、`SIGNING-CERTIFICATE.txt` 和检查报告，可用于核对文件与签名。

正式版升级需保持同包名、同签名及更高的 `versionCode`。此前的 Debug APK 使用临时签名，首次切换不能覆盖安装；先保存需要的资料，卸载会删除本机资料。旧包名应用可与当前应用共存，本机资料不会自动迁移。

## 功能

| 页面 | 功能 |
| --- | --- |
| 专注 | 番茄钟、倒计时、正计时、短休息与长休息、三段 Miku 场景、静态模式、沉浸全屏和完成语音 |
| 音乐 | 网易云／QQ 网络播放、原作 10 个推荐歌单、自定义歌单链接、封面与歌词、队列与循环、11 首内置音乐、本地导入与收藏 |
| 计划与记录 | 待办、日期计划、倒数日、每日专注统计和月度热力日历 |
| 社区与账号 | MikuMod 官方网页登录、学习资料与日历云同步、在线人数、管理员状态、实时聊天与历史分页、头像、表情及歌曲／倒数日分享 |
| 外观与系统集成 | Material 3 Expressive、深浅主题、壁纸取色、自适应与单色主题图标、通知栏／锁屏媒体控制 |

番茄钟、计划、记录、三段场景和内置音乐可离线使用。网络歌曲、账号与社区功能需要联网；歌单缓存不代表歌曲已下载。

## 账号与同步

在 **社区 → 账号** 打开 MikuMod 官方登录页，完成认证后返回应用。登录沿用原站流程，应用不读取账号密码。

首次关联有差异的本地与云端资料时，选择“使用云端资料”“保留本地并上传”或“合并并同步”，再点击确认。合并按原作规则保留每天较大的学习值，并对待办、计划等按 ID 去重。

读取成功并保存后才关闭同步窗口；失败会保留本地资料与选择框。已接受且没有变化的资料不会重复要求选择，云端更新与本地修改同时出现时会再次核对。切换账号时，账号与访客资料分别保存。同步范围沿用原作；本地导入音乐、收藏、播放队列与主题等 Android 扩展保存在本机。

## 开发与构建

使用 Android Studio 打开 [android](android/) 目录，准备 JDK 17、Android SDK 35 和 Build Tools 35.0.0：

```bash
git clone https://github.com/chorusfruit-233/StudyWithMiku-Android.git
cd StudyWithMiku-Android/android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Debug APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`，构建 Debug 无需正式签名密钥。

正式发行见 [Release 构建与发布指南](android/RELEASING.md)。`Android app` 工作流负责 Debug 构建和 Android 模拟器回归；`Android signed release` 负责固定签名的 Release 构建。工作流先保存 Release 草稿，维护者点击 **Publish release** 后才向公众展示。历史 Debug 草稿仍仅供有仓库写入权限的维护者查看。

当前 1.2.7 已通过 70 项 Release 单元测试和 Release Lint。相同应用源码的 Debug 构建通过 10 项 Android 35 模拟器回归；这些结果不等同于正式包真机或真实账号端到端验收。具体验证范围见 [验证记录](android/VALIDATION.md)。

安装包体积主要来自 11 首音乐（约 65 MB）与 3 段视频（约 44 MB）。1.2.7 尚未启用 R8 代码裁剪及资源裁剪，音视频沿用原始编码。

## 文档与反馈

- [工程说明与原作功能映射](android/README.md)
- [签名、构建与发布流程](android/RELEASING.md)
- [更新记录](CHANGELOG.md)
- [测试结果与验证范围](android/VALIDATION.md)
- [提交问题或建议](https://github.com/chorusfruit-233/StudyWithMiku-Android/issues)

报告问题时，请说明应用版本、手机与 Android 版本、复现步骤，以及预期和实际结果。

## 致谢与许可

原项目由 **SHSHOUSE** 创作：[StudyWithMiku](https://github.com/shshouse/StudyWithMiku)。Android 移植保留原作署名，遵循 [GNU GPL v3](LICENSE)。

场景、音乐、完成语音、贴图、表情与推荐歌单沿用原项目。音乐来源包括和未来一起喝茶、Alice In Cradle 与 Snail’s House；资源与角色权利归各自权利人。依赖与资源说明见 [工程文档](android/README.md#资源与许可)。
