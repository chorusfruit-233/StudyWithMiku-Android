@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.shshouse.studywithmiku

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.*
import java.time.format.DateTimeFormatter

fun MikuApplication.openLogin() = startActivity(Intent(this,LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
@Composable fun CommunityScreen(app: MikuApplication,music: MusicConnection,message: (String)->Unit,openPlayer: ()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val account by app.auth.state.collectAsStateWithLifecycle()
    val chat by app.chat.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp,vertical = 6.dp),verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("和正在学习的人相遇",style = MaterialTheme.typography.titleLarge)
                Text(if (chat.connected) "${chat.online} 人在线${if (chat.adminOnline) " · 站长在线" else ""}" else "正在连接学习空间…",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { if (!account.loggedIn) app.openLogin() else page = 1 }) { Icon(Icons.Rounded.AccountCircle,"账号与同步") }
        }
        Row(Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("聊天室","账号与同步","关于与更新").forEachIndexed { i,label -> FilterChip(selected = page == i,onClick = { page = i },label = { Text(label) }) }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> ChatPane(app,music,message,openPlayer)
                1 -> AccountPane(app)
                else -> AboutPane()
            }
        }
    }
}

@Composable fun ChatPane(app: MikuApplication,music: MusicConnection,message: (String)->Unit,openPlayer: ()->Unit) {
    val chat by app.chat.state.collectAsStateWithLifecycle()
    val account by app.auth.state.collectAsStateWithLifecycle()
    val profiles by app.chat.profiles.collectAsStateWithLifecycle()
    val onlineMusic by app.onlineMusic.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var stickers by remember { mutableStateOf(false) }
    var unread by remember { mutableIntStateOf(0) }
    var previousLast by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    fun latest() { unread = 0; scope.launch { if (chat.messages.isNotEmpty()) list.animateScrollToItem(chat.messages.size) } }
    LaunchedEffect(chat.messages.lastOrNull()?.id) {
        val last = chat.messages.lastOrNull()
        if (last != null && last.id != previousLast) {
            if (previousLast == null || atBottom || last.userId == account.userId) { list.scrollToItem(chat.messages.size); unread = 0 }
            else unread++
            previousLast = last.id
        }
    }
    LaunchedEffect(atBottom) { if (atBottom) unread = 0 }
    fun send() { if (app.chat.sendChat(input,app.extras.messageLocation())) input = "" }
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(state = list,modifier = Modifier.fillMaxSize(),contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "history") {
                    Box(Modifier.fillMaxWidth(),contentAlignment = Alignment.Center) {
                        if (chat.hasMore) TextButton(onClick = { app.chat.history() },enabled = !chat.loadingHistory) { Text(if (chat.loadingHistory) "正在加载历史消息…" else "加载更早的消息") }
                        else if (chat.messages.isNotEmpty()) Text("已经到顶啦",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text(if (chat.connected) "暂无消息，和大家打个招呼吧" else "正在连接聊天室…",style = MaterialTheme.typography.bodyMedium)
                    }
                }
                itemsIndexed(chat.messages,key = { _,m -> m.id }) { index,m ->
                    val prev = chat.messages.getOrNull(index-1)
                    val grouped = prev != null && (m.userId.ifBlank { m.username }).isNotBlank() && m.userId.ifBlank { m.username } == prev.userId.ifBlank { prev.username }
                    ChatBubble(m,profiles[m.userId],m.userId.isNotBlank() && m.userId == account.userId,grouped,
                        api = onlineMusic.config.api,onSong = { share ->
                            app.onlineMusic.load(app.onlineMusic.state.value.config.copy(platform = share.platform,id = share.playlistId,name = share.playlistName.ifBlank { "分享的歌单" })) { songs ->
                                val selected = songs.getOrNull(share.index)?.takeIf { share.name.isBlank() || it.title == share.name || it.webTitle == share.name }
                                    ?: songs.find { it.title == share.name || it.webTitle == share.name } ?: songs.getOrNull(share.index) ?: songs.firstOrNull()
                                selected?.let { music.play(songs,it.id); openPlayer() }
                            }
                        })
                }
            }
            if (!atBottom && chat.messages.isNotEmpty()) FilledTonalButton(onClick = { latest() },modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown,null); Text(if (unread > 0) "$unread 条新消息" else "最新消息")
            }
        }
        chat.error?.let { error ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),verticalAlignment = Alignment.CenterVertically) {
                Text(error,Modifier.weight(1f),color = MaterialTheme.colorScheme.error,style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { if (chat.connected && account.loggedIn && !chat.authenticated) app.openLogin() else app.chat.reconnect() }) { Text(if (chat.connected && account.loggedIn && !chat.authenticated) "重新登录" else "重连") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp,vertical = 6.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { stickers = true },enabled = chat.authenticated) { Icon(Icons.Rounded.SentimentSatisfiedAlt,"表情包") }
            OutlinedTextField(input,{ if (it.length <= 500) input = it },modifier = Modifier.weight(1f),shape = RoundedCornerShape(24.dp),
                enabled = account.loggedIn && chat.connected && chat.authenticated,placeholder = { Text(if (account.loggedIn) "输入消息…" else "登录后可以发言",fontSize = 13.sp) },
                maxLines = 3,keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),keyboardActions = KeyboardActions(onSend = { send() }))
            IconButton(onClick = { if (!account.loggedIn) app.openLogin() else send() },enabled = !account.loggedIn || chat.authenticated && input.isNotBlank()) {
                Icon(if (!account.loggedIn) Icons.Rounded.Login else Icons.Rounded.Send,if (!account.loggedIn) "登录" else "发送")
            }
        }
        if (account.loggedIn && chat.connected && !chat.authenticated) Text("正在验证聊天身份…",Modifier.padding(horizontal = 20.dp),style = MaterialTheme.typography.bodySmall)
    }
    if (stickers) AlertDialog(onDismissRequest = { stickers = false },title = { Text("给大家发一个 Miku") },text = {
        LazyVerticalGrid(columns = GridCells.Fixed(4),modifier = Modifier.heightIn(max = 340.dp),horizontalArrangement = Arrangement.spacedBy(6.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items((1..16).filter { it != 10 }) { id ->
                AsyncImage("file:///android_asset/sticker/$id.${stickerExtension(id)}","发送表情包 $id",
                    Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).clickable {
                        if (app.chat.sendChat(StudyProtocol.stickerMessage(id),app.extras.messageLocation())) { stickers = false; latest() }
                    },contentScale = ContentScale.Fit)
            }
        }
    },confirmButton = { TextButton(onClick = { stickers = false }) { Text("关闭") } })
}
fun stickerExtension(id: Int) = when (id) { 4,11,13,14 -> "jpg"; 12,15 -> "gif"; 16 -> "png"; else -> "webp" }

@Composable private fun ChatBubble(m: ChatMessage,profile: UserProfile?,own: Boolean,grouped: Boolean,api: String,onSong: (SharedSong)->Unit) {
    val name = profile?.username?.ifBlank { m.username } ?: m.username.ifBlank { "游客" }
    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp),verticalAlignment = Alignment.Top) {
        if (!grouped) UserAvatar(profile?.avatar,name,Modifier.size(34.dp)) else Spacer(Modifier.width(34.dp))
        Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (!grouped) Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp),verticalAlignment = Alignment.CenterVertically) {
                Text(name,style = MaterialTheme.typography.labelLarge,color = if (own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,overflow = TextOverflow.Ellipsis,modifier = Modifier.weight(1f))
                if (m.location.isNotBlank()) Text(m.location,style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(runCatching { Instant.parse(m.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M.d HH:mm")) }.getOrDefault(""),
                    style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val sticker = StudyProtocol.stickerId(m.content)
            val song = StudyProtocol.sharedSong(m.content)
            val day = StudyProtocol.countdown(m.content)
            when {
                sticker != null -> AsyncImage("file:///android_asset/sticker/$sticker.${stickerExtension(sticker)}","表情包 $sticker",Modifier.size(130.dp),contentScale = ContentScale.Fit)
                song != null -> Surface(onClick = { onSong(song) },shape = RoundedCornerShape(20.dp),color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val cover = StudyProtocol.sharedCover(song,api)
                        if (cover.isNotBlank()) AsyncImage(cover,null,Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.LibraryMusic,null,Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) { Text(song.name.ifBlank { "分享的歌曲" },style = MaterialTheme.typography.titleSmall,maxLines = 2); Text(song.playlistName,style = MaterialTheme.typography.bodySmall,maxLines = 1) }
                        Icon(Icons.Rounded.PlayCircle,"播放分享的歌曲")
                    }
                }
                day != null -> Surface(shape = RoundedCornerShape(20.dp),color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(14.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Event,null)
                        Column { Text(day.title,style = MaterialTheme.typography.titleSmall); Text("${countdownLabel(day.date)} · ${day.date}",style = MaterialTheme.typography.bodySmall) }
                    }
                }
                else -> Surface(shape = RoundedCornerShape(18.dp),color = if (own) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    SelectionContainer { Text(m.content,Modifier.padding(12.dp),style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
@Composable fun UserAvatar(url: String?,name: String,modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Surface(modifier = modifier,shape = CircleShape,color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            if (!url.isNullOrBlank() && url.startsWith("http") && !failed) AsyncImage(url,"$name 的头像",Modifier.fillMaxSize(),contentScale = ContentScale.Crop,onError = { failed = true })
            else Text(name.ifBlank { "访" }.take(1),style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun AccountPane(app: MikuApplication) {
    val account by app.auth.state.collectAsStateWithLifecycle()
    val sync by app.sync.state.collectAsStateWithLifecycle()
    val profiles by app.chat.profiles.collectAsStateWithLifecycle()
    val onlineMusic by app.onlineMusic.state.collectAsStateWithLifecycle()
    val logs by app.store.logs.collectAsStateWithLifecycle()
    val location by app.extras.location.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(app.store.autoSync) }
    var shareLocation by remember { mutableStateOf(app.extras.shareLocation) }
    var logout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val profile = profiles[account.userId]
        val name = profile?.username?.ifBlank { account.username } ?: account.username
        Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UserAvatar(profile?.avatar,name,Modifier.size(54.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "欢迎来到学习空间" },style = MaterialTheme.typography.titleLarge)
                    Text(if (account.loggedIn) "MikuMod 账号已登录" else if (account.expired) "登录已过期" else "登录后同步网页端学习数据",style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!account.loggedIn) Button(onClick = { app.openLogin() },modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Login,null); Spacer(Modifier.width(8.dp)); Text("登录 MikuMod") }
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { app.sync.syncNow() },enabled = !sync.busy,modifier = Modifier.weight(1f)) { Text(if (sync.busy) "同步中…" else if (sync.ready) "立即上传同步" else "读取云端") }
                OutlinedButton(onClick = { app.sync.pull() },enabled = !sync.busy,modifier = Modifier.weight(1f)) { Text("读取云端") }
            }
            if (sync.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (sync.ready) "已同步 · ${Instant.ofEpochMilli(sync.lastSync).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}" else "尚未完成资料同步",style = MaterialTheme.typography.bodyMedium)
            sync.message?.let { Text(it,color = MaterialTheme.colorScheme.primary) }
            sync.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("自动同步"); Text("同步学习统计、待办、日历、倒计日与计时设置",style = MaterialTheme.typography.bodySmall) }
                Switch(auto,{ auto = it; app.store.autoSync = it })
            }
            val total = remember(logs, sync.lastSync) { app.store.studyTotals() }
            val today = logs[LocalDate.now().toString()] ?: DayLog()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("累计学习","${total.millis/60_000}","分钟",Icons.Rounded.Timer,Modifier.weight(1f),true)
                StatCard("累计番茄","${total.sessions}","个",Icons.Rounded.LocalFlorist,Modifier.weight(1f),false)
            }
            Text("今日学习 ${today.millis/60_000} 分钟 · ${today.sessions} 个番茄",style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { logout = true }) { Text("退出登录") }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("聊天时显示地区"); Text(location.ifBlank { "未获取到地区" },style = MaterialTheme.typography.bodySmall) }
            Switch(shareLocation,{ shareLocation = it; app.extras.shareLocation = it })
        }
        Text("地区根据 IP 获取，不请求手机定位权限。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { app.extras.refreshLocation(true) }) { Text("刷新地区") }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false },title = { Text("退出当前账号？") },text = { Text("该账号的本地学习资料会保留，退出后切回游客资料。") },
        confirmButton = { TextButton(onClick = { app.sync.logout(); logout = false }) { Text("退出") } },dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
}

@Composable fun SyncChoiceDialog(app: MikuApplication) {
    SyncChoiceDialog(app.sync)
}

@Composable internal fun SyncChoiceDialog(controller: CloudSync) {
    val sync by controller.state.collectAsStateWithLifecycle()
    val conflict = sync.conflict ?: return
    val differences = remember(conflict) { StudyComparison.differences(conflict.local,conflict.remote) }
    AlertDialog(onDismissRequest = { controller.cancelChoice() },title = { Text("选择学习资料的同步方式") },text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("本地：${StudyComparison.describe(conflict.local)}\n云端：${StudyComparison.describe(conflict.remote)}")
            if (differences.isNotEmpty()) Text("存在差异：${differences.joinToString("、")}",style = MaterialTheme.typography.bodySmall)
            if (sync.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在读取并保存学习资料…",style = MaterialTheme.typography.bodySmall)
            }
            sync.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            Text("合并会按原项目规则保留每日较大的学习值、合并待办与计划，并使用当前设备的计时设置。切换账号时会保存原资料备份。",style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { controller.resolve(1) },enabled = !sync.busy,modifier = Modifier.fillMaxWidth()) { Text("使用云端资料") }
            OutlinedButton(onClick = { controller.resolve(2) },enabled = !sync.busy,modifier = Modifier.fillMaxWidth()) { Text("保留本地并上传") }
        }
    },confirmButton = { TextButton(onClick = { controller.resolve(0) },enabled = !sync.busy) { Text("合并并同步") } },dismissButton = { TextButton(onClick = { controller.cancelChoice() },enabled = !sync.busy) { Text("暂不同步") } })
}

@Composable private fun AboutPane() {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val updates = remember { JSONArray(context.assets.open("updates.json").bufferedReader().use { it.readText() }).objects() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding = PaddingValues(24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Study With Miku",style = MaterialTheme.typography.headlineMedium)
            Text("Love by SHSHOUSE · 原生 Android 1.2.3",style = MaterialTheme.typography.bodyMedium)
            val since = Duration.between(LocalDate.of(2025,12,2).atStartOfDay(ZoneId.systemDefault()).toInstant(),Instant.now()).toDays()
            Text("原站已陪伴大家 $since 天",style = MaterialTheme.typography.bodySmall)
            listOf("原项目与源码" to "https://github.com/shshouse/StudyWithMiku","作者主页" to "https://space.bilibili.com/3493127123897196",
                "MikuMod 账号设置" to "$ACCOUNT_SITE/settings","B站项目介绍" to "https://www.bilibili.com/video/BV18TSWBzE4o","支持原作者" to "https://afdian.com/a/shshouse").forEach { (name,url) ->
                TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text(name) }
            }
            TextButton(onClick = {
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("StudyWithMiku QQ群","941108668"))
            }) { Text("复制 QQ 群号 941108668") }
            Text("原项目更新日志",style = MaterialTheme.typography.titleLarge)
        }
        items(updates,key = { it.optString("date") }) { update ->
            Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(18.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(update.optString("date"),style = MaterialTheme.typography.titleMedium,color = MaterialTheme.colorScheme.primary)
                    update.optJSONArray("content")?.strings()?.forEach { Text(it,style = MaterialTheme.typography.bodyMedium) }
                    update.optJSONArray("images")?.strings()?.forEach { image -> AsyncImage("$STUDY_SITE$image",null,Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(16.dp)),contentScale = ContentScale.Fit) }
                }
            }
        }
    }
}
fun countdownLabel(date: String): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(date))
    return if (days > 0) "还有 $days 天" else if (days == 0L) "就是今天" else "已过 ${-days} 天"
}
