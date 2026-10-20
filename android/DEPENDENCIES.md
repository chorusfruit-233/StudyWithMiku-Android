# 构建依赖与 Android 17 适配

2026-10-20 核对 Google Maven、Maven Central、Gradle 发布元数据和官方兼容说明。以下为应用 1.2.13 的固定版本，未使用动态版本或 SNAPSHOT。

| 项目 | 原版本 | 当前版本 |
| --- | --- | --- |
| Android Gradle Plugin | 8.9.2 | 9.4.1 |
| Gradle Wrapper | 8.11.1 | 9.6.0 |
| Kotlin／Compose Compiler | 2.1.20 | 2.4.21 |
| compileSdk／targetSdk | 35 | 37 |
| SDK Build Tools | 35.0.0 | 37.0.0 |
| Compose BOM | 2025.06.01 | 2026.09.00 |
| Material 3 Expressive | 1.4.0-alpha18 | 1.5.0-beta01 |
| Core KTX | 1.16.0 | 1.19.1 |
| Activity Compose | 1.10.1 | 1.13.0 |
| Lifecycle Compose | 2.9.1 | 2.11.0 |
| Media3 | 1.7.1 | 1.11.1 |
| OkHttp／MockWebServer | 4.12.0 | 5.5.0 |
| Coil | 2.7.0 | 3.6.3 |
| kotlinx.coroutines | 测试 1.8.1／运行时传递依赖 | 运行时与测试 1.11.0 |
| AndroidX Test Runner | 1.6.2 | 1.7.0 |
| AndroidX Espresso／JUnit 扩展 | Compose 传递依赖 | 3.7.0／1.3.0 |
| JVM 测试 org.json | 20240303 | 20260814 |

JDK 与字节码目标继续使用 17，minSdk 继续为 26。JUnit 4.13.2 已是该测试框架的稳定版；Material Icons Extended 已停止更新，显式保留其最后版本 1.7.8，避免依赖新版 BOM 提供版本。

## 工具链选择

[AGP 9.4 的官方兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes) 支持 API 37，要求 Gradle 9.6.0 与 JDK 17。本次采用其默认 Gradle 版本；Wrapper 使用 Gradle 官方分发包与 SHA-256 校验。Kotlin 2.4.21 的完整支持范围包括 Gradle 9.6.0，详见 [Kotlin Gradle 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)。

按 [AGP 内置 Kotlin 迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin) 移除 `org.jetbrains.kotlin.android` 和旧 `android.kotlinOptions`。在根构建脚本将 Kotlin Gradle 实现与 Compose Compiler 固定为 2.4.21，避免 AGP 的默认 Kotlin 与 Compose 插件不一致；JVM 目标从 Java compileOptions 的 17 派生。

AGP 9 默认只为受测构建类型生成单元测试组件。项目显式设置 `android.onlyEnableUnitTestForTheTestedBuildType=false`，继续为 Debug 和 Release 分别执行完整单元测试，沿用既有签名构建检查。

Material 3 是本次运行时依赖中的预发布例外：1.4.0 稳定版将 MaterialExpressiveTheme、MotionScheme 等现有 Expressive 接口设为内部，并不公开动态按钮 shapes 接口。使用 1.5.0-beta01 保留原有 MD3E 主题、动效与形状按钮；其余更新选择已发布的稳定版本。

## 图片与网络

按 [Coil 3 迁移指南](https://coil-kt.github.io/coil/upgrading_to_coil3/) 更新 Maven 坐标与 `coil3` 包名，应用实现 `SingletonImageLoader.Factory`。显式引入 `coil-network-okhttp`，保留头像、封面和更新配图的网络加载能力。API 28 以上使用 AnimatedImageDecoder，API 26–27 使用 GifDecoder，保留动态表情。

## Android 17 行为适配

核对 [Android 16 目标行为](https://developer.android.com/about/versions/16/behavior-changes-16) 与 [Android 17 目标行为](https://developer.android.com/about/versions/17/behavior-changes-17)。应用已使用 enableEdgeToEdge 与 Jetpack BackHandler，未固定屏幕方向或使用屏幕尺寸限制；继续通过系统媒体与计时前台服务运行。

Android 17 的局域网访问需要单独的运行时权限。在“添加网络歌单 → 音乐源设置”中提供“允许局域网访问”，仅在用户点击时请求，返回设置页后重新读取授权状态。此入口供自建 LAN 音乐源使用，原站公共服务使用原有网络流程。

`Android app` 工作流在 API 35 与 API 37 分别执行生产播放器、缓存、计时、聊天、同步与界面回归，并按 API 独立归档报告。新增图片测试通过生产 SingletonImageLoader 请求本地测试服务上的 PNG，并实际解码内置 GIF，检查动画能力。实际运行结果以 [VALIDATION.md](VALIDATION.md) 与对应 Actions 报告为准。

测试依赖显式升级至 Espresso 3.7.0 与 JUnit 扩展 1.3.0，避免 Compose 传递的旧版 Espresso 反射调用 Android 17 已移除的 InputManager 接口，也符合官方 [MessageQueue 兼容指南](https://developer.android.com/about/versions/17/changes/messagequeue) 的测试库要求。

模拟器采用官方支持的 `-gpu software` 模式，并将 `cmdline-tools/latest` 明确指向已安装的 23.0 工具。emulator-runner 会把此路径置于 PATH 开头，之前重新调用了 runner 预装的 12.0，无法正确识别 `37.0` 镜像元数据，并出现 SurfaceFlinger 断言崩溃；仅更新 setup-android 的版本或 GPU 模式不足以修正实际调用路径。启动前检查 AVD 的 `target=android-37.0`，设备脚本再检查真实 API、独立 XML 结果和完整测试数量，配置错误、安装失败或未执行测试会阻止发布。
