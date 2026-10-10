# 1.2.5 自适应与单色启动图标

- Manifest 的普通图标与 roundIcon 指向 mipmap。API 26 资源使用独立薄荷绿背景与 Miku 矢量前景；API 33 资源增加 monochrome。单色前景采用透明眼睛／笑脸镂空，由启动器着色，不用白色填充假装透明。
- 前景与单色图案在 108 dp 画布上以中心点缩放至 0.78，保留头像、双马尾和发饰，并适配中心 66 dp 安全区域。原矢量图移至 mipmap 作为默认回退，所有资源别名保持相同类型。
- [图标预览](validation/adaptive-icon-1.2.5/preview.png) 由资源中的矢量路径生成，检查圆形、圆角方形以及两组示例主题色下的完整性与镂空。预览为遮罩模拟，不是 Android 启动器设备截图；实际遮罩、主题颜色与动效取决于启动器，主题图标需启动器支持并启用。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功；58 项现有单元测试通过，0 失败／错误／跳过，Lint 0 错误。资源变更未新增单元测试；未执行设备测试或启动器端到端验收。
- 对本地 APK 中普通／圆形图标的 API 26 与 API 33 四份二进制 XML 做检查：均含 adaptive-icon、background、foreground，API 33 两份额外含 monochrome。[打包检查记录](validation/adaptive-icon-1.2.5/README.txt)

# 1.2.4 包名更换

- 应用标识与命名空间统一为 `io.github.chorusfruit233.studywithmiku`，Kotlin 主代码、单元测试与设备测试的 package 声明及目录同步迁移。版本 1.2.4、versionCode=7。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功；58 项单元测试通过，0 失败／错误／跳过，Lint 0 错误。测试报告类名为新命名空间。
- 本地 APK 的实际包名为 `io.github.chorusfruit233.studywithmiku`，APK 签名验证通过。合并清单中的 Application、入口与登录 Activity、播放器与计时服务均使用新命名空间；设备测试目标包名同步正确。
- 最终源码构建提交 `affa320ad4cf45b381aa07ddf8a7fd79a00a5d9c`；[GitHub Actions 38051501529](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/38051501529) 成功。下载云端报告确认 58 项测试、0 失败／错误／跳过，Lint 0 错误，测试类均使用新命名空间。
- 下载 APK 确认包名 `io.github.chorusfruit233.studywithmiku`、版本 1.2.4、versionCode=7、最低 API 26、target API 35，APK v2 签名验证通过。SHA-256 与 CI 校验文件一致：`1c7eaf9a221ded76d7e43ffdfde69d90c675377f368d7b59b5fc8b5a154dbdf9`。
- [私有 APK 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-73fc7649ae9e93388669) 提供 APK、SHA256SUMS.txt 和 Android-check-reports.zip。
- 本次未执行设备测试或真实账号端到端测试；设备测试 APK 只验证编译与目标包配置。
- 新包名作为独立应用安装，可与旧版共存；本机数据不自动迁移。云端学习资料可通过同一账号恢复，无需卸载旧包名应用。

# 1.2.3 一致资料反复弹窗修复

- 同步判断使用两端有效资料，忽略仅有日期变化、已有日记录对应的今日派生计数、空日记录／空计划、原作默认字段补齐及浮点编码差异。今日计数中尚未进入日历的学习值先投影到对应日期，避免忽略真实新增学习。
- 网页／Android 同步、首次关联访客账号使用同一规则；访客和云端资料相同时直接采用云端，不需要重复选择。云端只有午夜派生变化时保留本机离线改动。
- 学习时长按毫秒、设置时长按本机秒精度比较，不以截图中的整数分钟判断一致。真实时长、待办、计划、倒数日、设置、顺序和未知字段差异仍会被识别；弹窗列出差异类别，显示秒数。
- 小数秒／分钟导入改用四舍五入，修正 1.001 秒和 6.1 分钟因二进制浮点误差被截断的问题。生成同步快照时按 ID／日期保留原云端待办、计划、倒数日、日记录的未知字段。
- 新增三个协调器回归用例在旧实现下均失败（原 13 项通过），证明测试能捕捉错误；修复后通过。另增加七项资料比较测试，总计 58 项单元测试。所有资料和凭据均为合成测试值，未操作真实用户账号。[回归记录](validation/sync-equality-1.2.3/README.txt)
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，58 项测试通过、0 失败／错误／跳过，Lint 0 错误。APK 本地签名校验通过，版本 1.2.3、versionCode=6。
- 最终源码构建提交 `907f8bbde1f5c81eb0f95f5e6adb7de33c07941a`；[GitHub Actions 37989145381](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37989145381) 成功。下载云端报告确认 58 项测试、0 失败／错误／跳过，Lint 0 错误。
- 下载 APK 确认为 1.2.3、versionCode=6、最低 API 26、target API 35，APK v2 签名校验通过，SHA-256 与 CI 校验文件一致：`d101c099b1eacb5d4146f25bf1a967b57969875c135a3ab6b98b7a17f2763065`。
- [私有 APK 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-dd9f18dca140097d51cd) 提供 APK、SHA256SUMS.txt 和 Android-check-reports.zip。临时调试签名的升级限制见 README；卸载会清除本地资料。
- 本次未执行 Android 设备测试；原有设备测试和新增小数／未知字段持久化测试只进行编译。真实手机跨设备、实际账户云端同步尚未验收；本版不宣称已通过真实账号端到端测试。

# 1.2.2 学习资料云端选择修复

- 选择“使用云端资料”后重新读取最新 `/api/study/data` 与 `/calendar`，完整返回后才应用。等待期间显示进度、禁止重复提交；成功显示“学习资料已保存到本机”，失败保留本地资料和选择框供重试。
- 修复每次进入只要本地有数据就弹出冲突框的问题。保存云端原始快照和本地应用后的快照，跟随账号隔离备份。云端未变化时保留本地资料；云端有更新且本地未改动时应用更新；双方不同且需要选择时仍提供选择框。
- 对比 JSON 值而非字段顺序与数字格式；本地今日派生计数跨午夜归零不视为新改动。旧 1.2.1 档案可使用已有云端快照判断是否需要再次选择。
- 上传尚未完成的本地选择／合并带有持久化标记，避免请求失败后重启被误认为已经同步并覆盖。请求完成前退出账号不会把旧结果应用到访客资料。
- 本地单元测试 48 项通过（原 33 项，新增同步生命周期 13 项、快照比较 2 项），覆盖最新云端选择、重启、旧档案、双方修改、失败重试、重复点击、退出／过期账号与上传失败后重启。
- 本地 `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 成功，Lint 0 错误。新增 Android 设备测试包编译通过，但未完成设备验收。所有新增测试使用合成资料；未使用真实账号，也未上传用户学习资料。
- Android 15／API 35 无硬件加速模拟器启动期间出现系统服务重启；安装本地 1.2.2 成功，确认 versionCode=5。三次设备测试尝试均因测试进程启动超时退出，Android 记录 reason=6（ANR，failed to complete startup），未取得实际按钮点击或持久化测试通过结果。[原始尝试记录](validation/cloud-choice-1.2.2/README.txt) 保留失败与退出原因，不作为通过证据。
- 最终源码构建提交 `4c83d7812009aaaa86e5c871ab2f78674475be7d`；[GitHub Actions 37784273212](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37784273212) 成功。云端报告确认 48 项测试、0 失败／错误、0 跳过，Lint 0 错误；CI 编译设备测试 APK，设备执行结果单独说明。
- 下载的 APK 为 1.2.2（versionCode=5），最低 API 26、target API 35；SHA-256 与 CI 校验值一致，APK v2 签名验证通过。SHA-256：`e8d6cd964e91c92214e78784d4cd0c4062e397d55d95a00bf9fa29e5c9189c47`。
- [私有 APK 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-d0848dd84fc131a1f994) 包含 APK、SHA256SUMS.txt 与 Android-check-reports.zip；保持私有草稿，不公开发布。真实账号端到端验收边界与调试签名升级限制见 README。

# 1.2.1 专注页聊天室滚动修复

- 修复列表到底后继续上滑／甩动时，未使用的距离与速度传给 ModalBottomSheet 而触发回弹的问题。只在聊天面板内容边界消耗向上的剩余滚动，保留向下阅读和下拉关闭；社区页的普通消息列表不受影响。
- 应用、Android 手势测试 APK 编译通过；33 项现有单元测试通过，Android Lint 0 错误。
- Android 15／API 35 离线 Compose 手势测试 2 项通过：滚至最后一条后反复快速上滑，逐帧检查面板位置（3 次手势、135 帧、误差不超过 1 像素）；再检查可以反向阅读历史，以及在首条消息处下拉关闭面板。测试使用 50 条合成消息，不向真实聊天室发言。
- 对照实验使用原来的 ModalBottomSheet 实现执行相同测试，捕捉到顶部从 814 像素跳至 655 像素（159 像素位移）；修复版通过相同的位置稳定性检查。对照实现仅存在于临时测试包，未保留在工程中。
- 软件模拟器首次启动测试进程出现启动超时；重试后实际完成上述两项测试。手势测试通过不等同于所有 Android 版本或真机的完整视觉验收。
- [手势测试原始报告](validation/chat-scroll-1.2.1/README.txt) 保留修复版通过记录和旧实现预期失败记录；旧实现的失败是对照实验结果。
- 最终源码构建提交 `e9e5e3d8039461546c78a5514a2a3feb65e40e4b`；[GitHub Actions 37691896786](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37691896786) 成功。下载后的报告确认 33 项测试、0 失败／错误，Lint 0 错误；CI 编译手势测试包，设备运行结果为上述本地测试。
- 下载的云端 APK 为 1.2.1（versionCode=4），最低 API 26、target API 35；SHA-256 与 CI 校验值一致，APK v2 签名验证通过。调试签名升级限制仍见 README。
- [私有 APK 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-1676b2b22506684fadbf) 包含 APK、SHA256SUMS.txt 与 Android-check-reports.zip；保持私有草稿，不公开发布。

# 1.2.0 全部在线功能验证记录

- 原作功能、API 字段与 WebSocket 消息结构逐项对照；接口不需要后端修改。
- 本地 JUnit 共 33 项全部通过：原有 16 项，加 10 项登录回调／凭据声明／聊天排序去重／表情／歌曲与倒数日分享／同步合并／小数时长与轮次测试，以及 7 项 MockWebServer 账号 HTTP 测试。
- HTTP 测试验证 GET 两份资料、Bearer 头、JSON PUT、204 空响应、空账号与异常数据区分、读失败不上传、401、禁止带凭据跟随重定向、公开头像请求无 Bearer。
- 2026-10-07，真实聊天室 WebSocket 握手 HTTP 101；收到 history（50 条，hasMore=true）和 count／adminOnline 消息。仅匿名读取，未向真实聊天室发送消息。
- 官方登录页可获取。当前构建环境访问账号资料与公开 profiles 接口返回 HTTP 403，补充请求亦有超时；这属于真实接口验收限制，未通过伪造登录或绕过服务访问限制处理。
- 真实账号登录、服务端发言、云端 PUT、跨设备同步、自动账号过期和头像均未做真实账号端到端验收。测试覆盖客户端协议与错误处理，不代表这些真实账号操作已经通过。
- Android 15／API 35 软件模拟器安装本地 1.2.0 APK 成功，确认 versionCode=3、versionName=1.2.0；应用在前台启动，专注页、社区页及账号页可通过 UIAutomator 导航与读取布局，聊天室错误／重连提示正常出现。
- 模拟器截图输出全黑，未能完成新版视觉验收；模拟器中的真实聊天室连接未建立。媒体淡入淡出、后台切歌和完整网络界面仍需目标设备验收。上述模拟器测试使用 cf2c512 的本地构建，最终 1eb0bab 仅修复同步完成时的账号隔离，无 UI 变更。下方 1.0.0 的设备结果只属于历史版本。
- 最终源码构建提交 `1eb0babf072e6d5970a895e6b926a8dd4091bbdd`；[GitHub Actions 37687726989](https://github.com/chorusfruit-233/StudyWithMiku-Android/actions/runs/37687726989) 成功。云端报告确认 33 项测试、0 失败／错误，Lint 0 错误。本地最终编译、测试和 Lint 同样通过。
- 最终补丁修复选择同步方式后的任务跟踪，并检查请求完成时的账号归属，防止旧账号同步结果写入访客或新账号档案。
- 云端 APK 为 1.2.0（versionCode=3），最低 API 26、target API 35；下载后的 SHA-256 与 CI 校验值一致，APK v2 签名验证通过。安装包含全部 16 张历史／现行表情、10 个推荐歌单、50 条本地语录和 30 条原作更新记录。
- [私有 APK 草稿](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/untagged-9e9d9d05ae6ba4735918) 包含 APK、SHA256SUMS.txt 与 Android-check-reports.zip；保持草稿与私有仓库，不公开发布。
- 补充真实 WebSocket 分页探测：匿名请求 load_history，成功取得下一批 50 条 history_chunk，before 毫秒游标校验通过；未发送真实聊天消息。

# 1.1.0 网络音乐验证记录

- 编译、单元测试和 Android Lint 通过；16 项单元测试通过，Lint 0 错误。
- 新增 9 项测试：原站请求参数、网易云和 QQ 两种数据字段、不同音质的稳定歌曲 ID、原站译名、无效链接与重复条目、歌单分享链接、接口校验、多时间戳及偏移歌词、HTTP 重定向/错误/响应大小限制。
- 2026-10-07，使用 Android 工程编译出的 `MusicHttp` / `MusicProtocol` 在 JVM 上请求原站真实接口：网易云默认歌单 124 首、QQ 古典歌单 56 首、站长歌单 83 首，三种歌单的封面与歌词全部获取成功。曲目数量以服务实时返回为准。
- 对默认歌单首曲进行真实 Range 请求，音频响应为 HTTP 206、`audio/mpeg`，CDN 位于 `m801.music.126.net`；封面与歌词均返回成功。
- 新版 APK 权限含 `INTERNET` 与 `ACCESS_NETWORK_STATE`；不含广泛存储读取权限。
- 本次的网络请求、解析和错误处理已验证；由于软件模拟器进程断开，尚未进行新版 Android 网络播放、歌词界面与后台恢复的设备验收。下方的模拟器记录属于此前 1.0.0，不作为新版网络音乐的设备验证。

# 1.0.0 历史验证记录

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
