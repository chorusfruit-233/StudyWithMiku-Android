原生 Android 1.2.12 调试构建，支持 Android 8.0 及以上。

对齐上游 2a19db4（2026-10-16）：手动切歌重叠、自动过渡交接及音量隔离；聊天历史保留实时消息、断线或拒发提示；过期歌单请求保护，旧设置默认值和音乐状态持久化。保留网络歌单音频缓存、离线播放及后台计时。README 整段删除“下载与安装”。

本条目为验证用 Debug 草稿。APK 使用调试签名，无法覆盖固定签名正式版。面向用户的固定签名正式包见 [公开 Release](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/latest)。

检查报告包含单元测试、Lint、Android 35 播放交接与界面回归；使用合成音频、本地 HTTP 服务和模拟套接字，不使用真实账号或向生产聊天室发言。详细范围见 [验证记录](https://github.com/chorusfruit-233/StudyWithMiku-Android/blob/main/android/VALIDATION.md)。
