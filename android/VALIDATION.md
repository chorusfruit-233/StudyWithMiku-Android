# 验证记录

## 公开发布状态（2026-10-10）

仓库已公开，[v1.2.7](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.7) 已正式发布。GitHub 元数据确认仓库为 `public`、该版本 `draft=false`；未登录的网页视图能打开正式 Release，下载链接返回 HTTP 206 与有效 APK 文件头。Release 提供 APK、文件校验值、公开证书资料和 Release 检查报告。

下文保留各版本真实构建、测试结果与验收范围。仓库公开不会自动发布历史 Debug 草稿；因此以草稿标识保留旧产物来源，不将其作为公众下载入口。本目录的回归报告与截图可随源码公开查看。

公开发行的签名正式包仍为 1.2.7；下方分别记录后续 1.2.9、1.2.8 源码适配和已发布版本的验证结果。

## 1.2.9 后台权限与耗电管理

- 计时 WakeLock 保持原有自动获取、超时续租与服务销毁时释放的方式；普通 `WAKE_LOCK` 权限已声明。新增 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，首次开始计时时申请系统电池优化豁免，并提示系统后台耗电管理设为“无限制”。
- 自动申请与提示各自保存在独立的本机 SharedPreferences，拒绝或系统页面不支持时不会反复中断计时，手动入口始终保留。返回系统界面后读取真实豁免状态；应用没有把豁免等同于厂商的“无限制”。
- 新增设备回归验证请求与提示跨重建的状态、拒绝后手动重试、已有豁免及撤销状态、厂商页面回退、两个设置按钮的独立行为，以及真实计时服务在不播放音乐时获取／释放 WakeLock。
- 版本 1.2.9、versionCode=12，包名和固定证书目标沿用正式版。本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，82 项单元测试全部通过、0 失败／错误／跳过，Lint 0 错误；设备测试 APK 编译成功，设备执行结果见下。
- [Debug Actions 38230310646](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38230310646) 构建与模拟器测试成功，下载报告确认 82 项 JVM 与 22 项 Android 35 测试通过，0 失败／错误／跳过，Debug Lint 0 错误。真实计时服务在不播放音乐时持有 `PARTIAL_WAKE_LOCK`，暂停后释放，通过 `dumpsys power` 核对；原有同步、歌单、任务与聊天滚动回归均通过。
- [设置组件截图与原始设备报告](validation/background-1.2.9/README.txt) 保留新增权限流程、设置入口、引导取消与 WakeLock 生命周期证据。权限状态使用合成值及独立 SharedPreferences，截图为生产组件测试窗口，不能作为厂商系统设置、所有主题或字体大小的验收。
- 构建应用源码提交 `0f9eb03203d7af7a3e3195a8599a5fd5f10b585e`；下载 Debug APK 的包名、1.2.9／versionCode=12、API 26／35、签名及 SHA-256 核对通过。SHA-256 为 `b03700ce97b6bf330538023fc7bcf22f1471947b9e4989c0b651e2f3bd89b26e`。Debug 设备结果不作为新签名 Release 真机验收。
- 本地 `assembleRelease testReleaseUnitTest lintRelease` 成功，82 项 Release 单元测试全部通过，Lint 0 错误。使用用户提供的 Base64 JKS，补齐缺失的开头 `/` 后，JKS 完整性和固定公钥证书均验证通过；签名仅在本地完成，临时 JKS 已删除，密码仅通过进程环境传给构建，没有写入 Git 或公开报告。
- 首次 [签名 Actions 38230323343](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38230323343) 与 [重跑 38230940929](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38230940929) 均未读取到 `ANDROID_KEYSTORE_BASE64`，在恢复密钥时失败，跳过 Release 构建与发布。GitHub 编辑 Secret 不会回显原值，输入框为空不能作为丢失依据；本记录不宣称仓库 Secrets 已修复。后续云端签名需按 [签名指南](RELEASING.md) 确认四项 Repository secrets。
- 正式签名 APK 包名正确、versionCode=12、`debuggable=false`，证书 SHA-256 与 1.2.7、1.2.8 一致：`e07a05095f2b19e1b7b37d48e67016da4b5cf78386a03020410a3a134e1f12ad`。APK 大小 129,368,044 字节，SHA-256 为 `886f7784238fb02dc7680578c86e5894798d1f3c03c72fa07422b657dcb248f7`。[1.2.9 Release 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-754b88639a1e551113cc) 已保存 APK、校验值、公开证书资料和 Release 报告，尚未公开发布。
- [Actions 38232372367](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38232372367) 成功核验已经签名的 APK、固定证书、包名、关闭调试和传送 SHA-256，并上传草稿；这是核验和上传，不是云端签名构建。临时分支仅包含 APK 及公开报告，已删除。重新下载的 APK、证书、SHA-256、源码提交和 82 项 Release 测试报告均核对通过。

## 1.2.8 原作 2026-10-09 更新适配

- 原作功能基线从 `159db717f02debf71a5138764ed8e068f4577f94` 更新至 `a3c4e5e0d4be48b2e21662ac85ff6022f8aa926d`，覆盖自定义网络歌单管理、名称获取、聊天室引用回复、一言及更新记录。
- 自定义网络歌单在曲目加载成功后保存平台、ID 与名称，平台＋ID 去重，名称失败使用 ID。保存列表与本地歌曲歌单及学习云同步分离。名称接口使用原作 Worker，四秒超时，不发送账号凭据。
- 回复沿用 `[reply:消息id]正文`，保留引用目标与输入直到发送成功，退出或切换账号清除目标；正文按表情、歌曲、倒数日或文本渲染，引用未加载时显示明确提示。500 字符限制包含回复前缀。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，82 项 Debug 单元测试通过、0 失败／错误／跳过，Lint 0 错误；新增回复协议、平台去重、名称失败和响应大小等回归测试。Android 测试 APK 编译成功。
- 实际匿名读取原作 `/playlist-name`，网易云默认歌单返回“StudyWithMiku初音在线自习室”，QQ 歌单 `9515739794` 返回“阿氏圆的专注音乐集”，均为 HTTP 200。未登录账号、发送聊天或写入云端学习资料。
- `quotes.json` 与 `updates.json` 来自该原作提交的实际 JavaScript 导出数组，分别为 47 条一言与 31 条更新记录，最新记录日期为 `2026-10-9`；不包含源文件中已注释停用的一言。
- 版本为 1.2.8、versionCode=11，包名与正式证书目标沿用 1.2.7。本地 `assembleRelease testReleaseUnitTest lintRelease` 成功，82 项 Release 单元测试全部通过、Lint 0 错误。签名 APK 的固定证书、包名、versionCode=11 和 `debuggable=false` 已验证，与 1.2.7 使用同一证书。
- [Debug Actions 38068345768](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38068345768) 完成构建与模拟器回归；下载报告确认 82 项 Debug 单元测试、16 项 Android 35 测试全部通过，0 失败／错误／跳过，Debug Lint 0 错误。新增设备用例覆盖网络歌单持久化、音乐与名称接口失败、引用回复与取消、引用未加载时的表情以及删除确认，原有同步与聊天室滚动回归仍通过。
- [回复组件截图与原始设备报告](validation/upstream-1.2.8/README.txt) 使用合成消息、独立 SharedPreferences 和本地 HTTP 测试服务，未使用真实账号或向聊天室发言；截图属于生产组件的测试窗口，未覆盖主界面所有主题或字体大小。设备结果属于 Debug 构建，不作为签名 Release 真机验收。
- 签名 Release 的应用源码提交为 `f7fee3b317893be255f63f43b441eb00ae698580`，APK 大小 129,367,996 字节，SHA-256 为 `f329513742350c750cfffc47cec2425b1f072f88d2a2b6bde6741600fbda5e53`。[1.2.8 Release 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-34ddd8ce8bc99e089ae3) 已保存 APK、校验值、公开证书和 Release 报告，仅维护者可见，尚未公开发布。

- 本地附件上传返回 HTTP 401，改用临时 APK 传送分支与 [Actions 38068758745](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38068758745) 核验固定证书、包名、关闭调试和 SHA-256 后上传。签名在本地完成，Actions 仅核验与上传；未上传密钥或密码，临时分支已删除。从 GitHub 重新下载后，APK 签名和 SHA-256 均通过，Release 报告确认 82 项测试及源码提交一致。

## 1.2.7 固定密钥 Release 签名

- 使用项目正式发行的 StudyWithMiku JKS，别名 `StudyWithMiku`。Release APK 的证书 SHA-256 为 `e07a05095f2b19e1b7b37d48e67016da4b5cf78386a03020410a3a134e1f12ad`，与 JKS 公钥证书一致；APK 签名验证通过，合并 Manifest 与 APK 均确认 `debuggable=false`。版本 1.2.7、versionCode=10、包名 `io.github.chorusfruit233.studywithmiku`，最低 API 26、target API 35。
- 本地 `assembleRelease testReleaseUnitTest lintRelease` 成功，70 项 Release 单元测试通过、0 失败／错误／跳过，Release Lint 0 错误。未启用代码混淆；未改变运行时功能，首次正式签名以兼容性为先。
- 打包 Release 缺少签名参数会明确失败；实际检查任务已验证拒绝缺失凭据。发布校验脚本已验证接受固定证书正式包、拒绝旧调试证书 APK，并核对包名、版本与关闭调试标志。
- 新增 `Android signed release` 工作流，通过加密 Secrets 恢复临时 JKS，在任务结束后删除；版本标签与 APK 版本必须一致，已经发布的版本不会被覆盖。Debug 工作流保留，并改为从 APK 元数据读取文件名和标题中的版本，明确标记 Debug。
- 构建 1.2.7 时，GitHub 连接访问 Actions Secrets 返回 HTTP 403（Resource not accessible by integration），因此在本地完成签名；本记录不宣称该版本是在云端签名构建。后续 Actions 签名构建须配置 [RELEASING.md](RELEASING.md) 中的四项 Secrets。
- 本次没有对签名 Release 执行真机或模拟器测试；下方 1.2.6 的 10 项 Android 回归属于其 Debug 构建，应用功能源代码相同，不能作为新签名正式包设备验收。
- 正式包源码提交 `f5aa975c8552dbad02e01cb42e0b059cf60167dc`；[公开正式 Release v1.2.7](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.7) 已包含签名 APK、校验值、公开证书资料与 Release 检查报告。由于本地附件上传端点返回 HTTP 401，在当时的私有仓库中使用临时分支传送已签名 APK，[Actions 38059048025](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38059048025) 成功验证其固定签名、包名、关闭调试和传送校验值后上传；未传送签名密钥或密码，临时分支已删除。此工作流是上传与核验，不是云端签名构建。
- 从 GitHub 重新下载的正式 APK 签名与校验值均通过，SHA-256 为 `cf534a55f11a3cf83699bcd258e7d944148733091facc9bb22509d86a6717a32`；下载的检查报告确认 70 项 Release 单元测试通过，元数据的源码提交与本次构建一致。
- 该版本源码的 [Debug Actions 38058600855](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38058600855) 亦成功；下载报告确认 70 项 Debug 单元测试、10 项 Android 35 回归通过，Debug Lint 0 错误。这些设备测试属于 Debug 构建，不作为签名 Release 的设备验收。

## 1.2.6 设计与交互修正

- 同步弹窗采用同层级单选方案和统一确认按钮；选择方案本身不导入或上传。首次设置同步仅有一个入口，完成后区分上传本地与检查云端更新；状态区分有本地改动待上传与已核对。暂缓同步显示正常提示，读取失败后取消不会重复反馈。
- 关于信息移到设置里的单一入口，版本读取 BuildConfig。社区采用聊天室／账号两个导航页签；歌曲／倒数日分享后进入聊天室，从专注聊天室打开播放器时关闭旧弹层。
- 计时主页面与沉浸模式统一状态、操作和实际阶段时长；休息暂停显示继续休息，休息轮数归属刚结束的番茄轮。轮数单位为轮，时长保留可读小数，自定义长休息不再被短休息保存覆盖；每日目标上限与存储一致。
- 计划区分添加与编辑，非日期目标不显示日期。达到 200 项上限时禁用添加，新增失败不会修改上一条计划；快速添加仍生成唯一数字 ID。删除任务、倒数日与歌单先确认，取消保留数据。记录翻月同步更新所选日期，窄屏详情采用纵向布局。
- 音乐页区分歌词加载／空结果／失败；歌单失败保留上次成功结果，推荐卡片表示已载入。QQ 与网易分享链接按域名识别平台；空队列／未连接操作禁用，播放器窄屏布局保留触控区域，切歌清除旧拖动进度。
- 最终本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，70 项单元测试通过、0 失败／错误／跳过，Lint 0 错误；Android 交互测试包编译通过。[GitHub Actions 38056241528](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38056241528) 成功，下载报告确认 70 项 JVM 测试和 10 项 Android 35 模拟器回归全部通过、0 失败／错误／跳过，Lint 0 错误。
- 最终构建提交 `ec97ad0f4b28f098640e410176fb1a06a3b3d3e1`；云端 APK 确认为 1.2.6、versionCode=9、包名 `io.github.chorusfruit233.studywithmiku`，APK v2 签名与 SHA-256 校验通过：`8a58c770ece19ae8583d0ce07f39a9261a00ff91c0e9141e5495283a66e01345`。历史 Debug 草稿 `ci-ec97ad0f4b28`（未公开发布，仅维护者可见）。签名与上一份 1.2.5 不同，不能覆盖安装；卸载会清除本机资料。
- [同步弹窗截图](validation/design-1.2.6/sync-choice.png) 与 [编辑窗口截图](validation/design-1.2.6/task-edit.png) 来自云端 Android 模拟器离线测试，均已核对按钮层级、选中状态和文字布局。截图采用合成资料，背景为不含主界面 Scaffold 的测试窗口，不作为主页面全屏布局或全部字体大小的验收。原始设备 XML 与验证说明见 [记录](validation/design-1.2.6/README.txt)。
- Android 回归采用合成资料与独立 SharedPreferences，未使用真实用户账号；模拟器通过不等同于真机、全部字体大小或真实账号端到端验收。未修改后台保活或冻结策略。

## 1.2.5 自适应与单色启动图标

- Manifest 的普通图标与 roundIcon 指向 mipmap。API 26 资源使用独立薄荷绿背景与 Miku 矢量前景；API 33 资源增加 monochrome。单色前景采用透明眼睛／笑脸镂空，由启动器着色，不用白色填充假装透明。
- 前景与单色图案在 108 dp 画布上以中心点缩放至 0.78，保留头像、双马尾和发饰，并适配中心 66 dp 安全区域。原矢量图移至 mipmap 作为默认回退，所有资源别名保持相同类型。
- [图标预览](validation/adaptive-icon-1.2.5/preview.png) 由资源中的矢量路径生成，检查圆形、圆角方形以及两组示例主题色下的完整性与镂空。预览为遮罩模拟，不是 Android 启动器设备截图；实际遮罩、主题颜色与动效取决于启动器，主题图标需启动器支持并启用。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功；58 项现有单元测试通过，0 失败／错误／跳过，Lint 0 错误。资源变更未新增单元测试；未执行设备测试或启动器端到端验收。
- 最终构建提交 `fa4c6606fcd639b53a4064ed6cc3d4e146987f78`；[GitHub Actions 38053910158](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38053910158) 成功。下载云端报告确认 58 项测试、0 失败／错误／跳过，Lint 0 错误。
- 云端 APK 确认为 1.2.5、versionCode=8，包名 `io.github.chorusfruit233.studywithmiku`；APK v2 签名及 SHA-256 校验通过：`d9f603d122b30093f8fac74da25702c96e8abef47280cf85bc4ae9840c992c02`。云端 APK 的四份图标 XML 同样验证通过。
- 历史 Debug 草稿 `ci-fa4c6606fcd6`（未公开发布，仅维护者可见） 提供 APK、校验文件与检查报告。此 CI APK 的调试签名与上一份 1.2.4 不同，无法直接覆盖安装；卸载会清除本机资料，升级前须确认资料已保存。
- 对本地 APK 中普通／圆形图标的 API 26 与 API 33 四份二进制 XML 做检查：均含 adaptive-icon、background、foreground，API 33 两份额外含 monochrome。[打包检查记录](validation/adaptive-icon-1.2.5/README.txt)

## 1.2.4 包名更换

- 应用标识与命名空间统一为 `io.github.chorusfruit233.studywithmiku`，Kotlin 主代码、单元测试与设备测试的 package 声明及目录同步迁移。版本 1.2.4、versionCode=7。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功；58 项单元测试通过，0 失败／错误／跳过，Lint 0 错误。测试报告类名为新命名空间。
- 本地 APK 的实际包名为 `io.github.chorusfruit233.studywithmiku`，APK 签名验证通过。合并清单中的 Application、入口与登录 Activity、播放器与计时服务均使用新命名空间；设备测试目标包名同步正确。
- 最终源码构建提交 `affa320ad4cf45b381aa07ddf8a7fd79a00a5d9c`；[GitHub Actions 38051501529](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38051501529) 成功。下载云端报告确认 58 项测试、0 失败／错误／跳过，Lint 0 错误，测试类均使用新命名空间。
- 下载 APK 确认包名 `io.github.chorusfruit233.studywithmiku`、版本 1.2.4、versionCode=7、最低 API 26、target API 35，APK v2 签名验证通过。SHA-256 与 CI 校验文件一致：`1c7eaf9a221ded76d7e43ffdfde69d90c675377f368d7b59b5fc8b5a154dbdf9`。
- 历史 Debug 草稿 `ci-affa320ad4cf`（未公开发布，仅维护者可见） 提供 APK、SHA256SUMS.txt 和 Android-check-reports.zip。
- 本次未执行设备测试或真实账号端到端测试；设备测试 APK 只验证编译与目标包配置。
- 新包名作为独立应用安装，可与旧版共存；本机数据不自动迁移。云端学习资料可通过同一账号恢复，无需卸载旧包名应用。

## 1.2.3 一致资料反复弹窗修复

- 同步判断使用两端有效资料，忽略仅有日期变化、已有日记录对应的今日派生计数、空日记录／空计划、原作默认字段补齐及浮点编码差异。今日计数中尚未进入日历的学习值先投影到对应日期，避免忽略真实新增学习。
- 网页／Android 同步、首次关联访客账号使用同一规则；访客和云端资料相同时直接采用云端，不需要重复选择。云端只有午夜派生变化时保留本机离线改动。
- 学习时长按毫秒、设置时长按本机秒精度比较，不以截图中的整数分钟判断一致。真实时长、待办、计划、倒数日、设置、顺序和未知字段差异仍会被识别；弹窗列出差异类别，显示秒数。
- 小数秒／分钟导入改用四舍五入，修正 1.001 秒和 6.1 分钟因二进制浮点误差被截断的问题。生成同步快照时按 ID／日期保留原云端待办、计划、倒数日、日记录的未知字段。
- 新增三个协调器回归用例在旧实现下均失败（原 13 项通过），证明测试能捕捉错误；修复后通过。另增加七项资料比较测试，总计 58 项单元测试。所有资料和凭据均为合成测试值，未操作真实用户账号。[回归记录](validation/sync-equality-1.2.3/README.txt)
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，58 项测试通过、0 失败／错误／跳过，Lint 0 错误。APK 本地签名校验通过，版本 1.2.3、versionCode=6。
- 最终源码构建提交 `907f8bbde1f5c81eb0f95f5e6adb7de33c07941a`；[GitHub Actions 37989145381](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37989145381) 成功。下载云端报告确认 58 项测试、0 失败／错误／跳过，Lint 0 错误。
- 下载 APK 确认为 1.2.3、versionCode=6、最低 API 26、target API 35，APK v2 签名校验通过，SHA-256 与 CI 校验文件一致：`d101c099b1eacb5d4146f25bf1a967b57969875c135a3ab6b98b7a17f2763065`。
- 历史 Debug 草稿 `ci-907f8bbde1f5`（未公开发布，仅维护者可见） 提供 APK、SHA256SUMS.txt 和 Android-check-reports.zip。临时调试签名的升级限制见 README；卸载会清除本地资料。
- 本次未执行 Android 设备测试；原有设备测试和新增小数／未知字段持久化测试只进行编译。真实手机跨设备、实际账户云端同步尚未验收；本版不宣称已通过真实账号端到端测试。

## 1.2.2 学习资料云端选择修复

- 选择“使用云端资料”后重新读取最新 `/api/study/data` 与 `/calendar`，完整返回后才应用。等待期间显示进度、禁止重复提交；成功显示“学习资料已保存到本机”，失败保留本地资料和选择框供重试。
- 修复每次进入只要本地有数据就弹出冲突框的问题。保存云端原始快照和本地应用后的快照，跟随账号隔离备份。云端未变化时保留本地资料；云端有更新且本地未改动时应用更新；双方不同且需要选择时仍提供选择框。
- 对比 JSON 值而非字段顺序与数字格式；本地今日派生计数跨午夜归零不视为新改动。旧 1.2.1 档案可使用已有云端快照判断是否需要再次选择。
- 上传尚未完成的本地选择／合并带有持久化标记，避免请求失败后重启被误认为已经同步并覆盖。请求完成前退出账号不会把旧结果应用到访客资料。
- 本地单元测试 48 项通过（原 33 项，新增同步生命周期 13 项、快照比较 2 项），覆盖最新云端选择、重启、旧档案、双方修改、失败重试、重复点击、退出／过期账号与上传失败后重启。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，Lint 0 错误。新增 Android 设备测试包编译通过，但未完成设备验收。所有新增测试使用合成资料；未使用真实账号，也未上传用户学习资料。
- Android 15／API 35 无硬件加速模拟器启动期间出现系统服务重启；安装本地 1.2.2 成功，确认 versionCode=5。三次设备测试尝试均因测试进程启动超时退出，Android 记录 reason=6（ANR，failed to complete startup），未取得实际按钮点击或持久化测试通过结果。[原始尝试记录](validation/cloud-choice-1.2.2/README.txt) 保留失败与退出原因，不作为通过证据。
- 最终源码构建提交 `4c83d7812009aaaa86e5c871ab2f78674475be7d`；[GitHub Actions 37784273212](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37784273212) 成功。云端报告确认 48 项测试、0 失败／错误、0 跳过，Lint 0 错误；CI 编译设备测试 APK，设备执行结果单独说明。
- 下载的 APK 为 1.2.2（versionCode=5），最低 API 26、target API 35；SHA-256 与 CI 校验值一致，APK v2 签名验证通过。SHA-256：`e8d6cd964e91c92214e78784d4cd0c4062e397d55d95a00bf9fa29e5c9189c47`。
- 历史 Debug 草稿 `ci-4c83d7812009`（未公开发布，仅维护者可见） 包含 APK、SHA256SUMS.txt 与 Android-check-reports.zip；该历史 Debug 草稿仍未公开发布。真实账号端到端验收边界与调试签名升级限制见 README。

## 1.2.1 专注页聊天室滚动修复

- 修复列表到底后继续上滑／甩动时，未使用的距离与速度传给 ModalBottomSheet 而触发回弹的问题。只在聊天面板内容边界消耗向上的剩余滚动，保留向下阅读和下拉关闭；社区页的普通消息列表不受影响。
- 应用、Android 手势测试 APK 编译通过；33 项现有单元测试通过，Android Lint 0 错误。
- Android 15／API 35 离线 Compose 手势测试 2 项通过：滚至最后一条后反复快速上滑，逐帧检查面板位置（3 次手势、135 帧、误差不超过 1 像素）；再检查可以反向阅读历史，以及在首条消息处下拉关闭面板。测试使用 50 条合成消息，不向真实聊天室发言。
- 对照实验使用原来的 ModalBottomSheet 实现执行相同测试，捕捉到顶部从 814 像素跳至 655 像素（159 像素位移）；修复版通过相同的位置稳定性检查。对照实现仅存在于临时测试包，未保留在工程中。
- 软件模拟器首次启动测试进程出现启动超时；重试后实际完成上述两项测试。手势测试通过不等同于所有 Android 版本或真机的完整视觉验收。
- [手势测试原始报告](validation/chat-scroll-1.2.1/README.txt) 保留修复版通过记录和旧实现预期失败记录；旧实现的失败是对照实验结果。
- 最终源码构建提交 `e9e5e3d8039461546c78a5514a2a3feb65e40e4b`；[GitHub Actions 37691896786](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37691896786) 成功。下载后的报告确认 33 项测试、0 失败／错误，Lint 0 错误；CI 编译手势测试包，设备运行结果为上述本地测试。
- 下载的云端 APK 为 1.2.1（versionCode=4），最低 API 26、target API 35；SHA-256 与 CI 校验值一致，APK v2 签名验证通过。调试签名升级限制仍见 README。
- 历史 Debug 草稿 `ci-e9e5e3d80394`（未公开发布，仅维护者可见） 包含 APK、SHA256SUMS.txt 与 Android-check-reports.zip；该历史 Debug 草稿仍未公开发布。

## 1.2.0 全部在线功能验证记录

- 原作功能、API 字段与 WebSocket 消息结构逐项对照；接口不需要后端修改。
- 本地 JUnit 共 33 项全部通过：原有 16 项，加 10 项登录回调／凭据声明／聊天排序去重／表情／歌曲与倒数日分享／同步合并／小数时长与轮次测试，以及 7 项 MockWebServer 账号 HTTP 测试。
- HTTP 测试验证 GET 两份资料、Bearer 头、JSON PUT、204 空响应、空账号与异常数据区分、读失败不上传、401、禁止带凭据跟随重定向、公开头像请求无 Bearer。
- 2026-10-07，真实聊天室 WebSocket 握手 HTTP 101；收到 history（50 条，hasMore=true）和 count／adminOnline 消息。仅匿名读取，未向真实聊天室发送消息。
- 官方登录页可获取。当时的构建环境访问账号资料与公开 profiles 接口返回 HTTP 403，补充请求亦有超时；这属于真实接口验收限制，未通过伪造登录或绕过服务访问限制处理。
- 真实账号登录、服务端发言、云端 PUT、跨设备同步、自动账号过期和头像均未做真实账号端到端验收。测试覆盖客户端协议与错误处理，不代表这些真实账号操作已经通过。
- Android 15／API 35 软件模拟器安装本地 1.2.0 APK 成功，确认 versionCode=3、versionName=1.2.0；应用在前台启动，专注页、社区页及账号页可通过 UIAutomator 导航与读取布局，聊天室错误／重连提示正常出现。
- 模拟器截图输出全黑，未能完成新版视觉验收；模拟器中的真实聊天室连接未建立。媒体淡入淡出、后台切歌和完整网络界面仍需目标设备验收。上述模拟器测试使用 cf2c512 的本地构建，最终 1eb0bab 仅修复同步完成时的账号隔离，无 UI 变更。下方 1.0.0 的设备结果只属于历史版本。
- 最终源码构建提交 `1eb0babf072e6d5970a895e6b926a8dd4091bbdd`；[GitHub Actions 37687726989](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37687726989) 成功。云端报告确认 33 项测试、0 失败／错误，Lint 0 错误。本地最终编译、测试和 Lint 同样通过。
- 最终补丁修复选择同步方式后的任务跟踪，并检查请求完成时的账号归属，防止旧账号同步结果写入访客或新账号档案。
- 云端 APK 为 1.2.0（versionCode=3），最低 API 26、target API 35；下载后的 SHA-256 与 CI 校验值一致，APK v2 签名验证通过。安装包含全部 16 张历史／现行表情、10 个推荐歌单、50 条本地语录和 30 条原作更新记录。
- 历史 Debug 草稿 `ci-1eb0babf072e`（未公开发布，仅维护者可见） 包含 APK、SHA256SUMS.txt 与 Android-check-reports.zip；该历史 Debug 草稿仍未公开发布。
- 补充真实 WebSocket 分页探测：匿名请求 load_history，成功取得下一批 50 条 history_chunk，before 毫秒游标校验通过；未发送真实聊天消息。

## 1.1.0 网络音乐验证记录

- 编译、单元测试和 Android Lint 通过；16 项单元测试通过，Lint 0 错误。
- 新增 9 项测试：原站请求参数、网易云和 QQ 两种数据字段、不同音质的稳定歌曲 ID、原站译名、无效链接与重复条目、歌单分享链接、接口校验、多时间戳及偏移歌词、HTTP 重定向/错误/响应大小限制。
- 2026-10-07，使用 Android 工程编译出的 `MusicHttp` / `MusicProtocol` 在 JVM 上请求原站真实接口：网易云默认歌单 124 首、QQ 古典歌单 56 首、站长歌单 83 首，三种歌单的封面与歌词全部获取成功。曲目数量以服务实时返回为准。
- 对默认歌单首曲进行真实 Range 请求，音频响应为 HTTP 206、`audio/mpeg`，CDN 位于 `m801.music.126.net`；封面与歌词均返回成功。
- 新版 APK 权限含 `INTERNET` 与 `ACCESS_NETWORK_STATE`；不含广泛存储读取权限。
- 本次的网络请求、解析和错误处理已验证；由于软件模拟器进程断开，尚未进行新版 Android 网络播放、歌词界面与后台恢复的设备验收。下方的模拟器记录属于此前 1.0.0，不作为新版网络音乐的设备验证。

## 1.0.0 历史验证记录

- `assembleDebug testDebugUnitTest lintDebug`：通过。
- JUnit：7 项全部通过，覆盖后台延迟、暂停恢复、四轮长休息、休息不计专注、正计时、倒计时和重复回调不重复记账。
- Android Lint：0 错误。17 条提示为依赖版本和 Kotlin 扩展写法建议。
- APK 签名：`apksigner verify` 通过。APK 为调试签名，最低 Android 8.0。
- 最终 APK 权限清单：没有 `INTERNET`、`ACCESS_NETWORK_STATE`、广泛存储或媒体读取权限。
- 资源检查：3 段场景视频、11 首曲目、11 张封面和完成语音全部包含在安装包中。
- Android 15 / API 35 模拟器，Wi-Fi 和移动数据关闭：应用成功启动；专注页和音乐页布局已通过实际画面检查。
- 后台计时：用户启动的 `FocusService` 成功进入 `specialUse` 前台服务；回到应用后暂停，保存了 83,453 毫秒专注时间，计时剩余量与已计入时间一致，状态为暂停。
- 离线后台播放：系统媒体会话显示 `PLAYING`，歌单大小为 11，已播放位置向前推进；系统媒体暂停命令可用。

模拟器使用无硬件加速的软件执行，启动和动画速度不代表真机性能；尚未进行 Android 8–14 真机兼容性测试。文件选择器导入和通知外观仍需在目标手机上核对。
