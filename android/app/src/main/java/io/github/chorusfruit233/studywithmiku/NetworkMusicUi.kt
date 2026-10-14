@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.chorusfruit233.studywithmiku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.net.URI

fun platformName(platform: String) = if (platform == "tencent") "QQ 音乐" else "网易云音乐"

object RemoteCovers {
    private val cache = object : LruCache<String,Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String,value: Bitmap) = value.byteCount
    }
    fun get(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val bytes = MusicHttp.bytes(url,5 * 1024 * 1024)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        var sample = 1
        while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample })
        if (bitmap != null) cache.put(url,bitmap)
        return bitmap
    }
}

@Composable fun NetworkMusicPanel(state: OnlineMusicState,online: OnlineMusic,onSelect: () -> Unit,onMessage: (String) -> Unit,
    audioCache: AudioCache = (LocalContext.current.applicationContext as MikuApplication).audioCache,
    onOfflinePlay: (List<Song>) -> Unit = {}) {
    var custom by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedNetworkPlaylist?>(null) }
    val savedPlaylists by online.savedPlaylists.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudQueue,null,Modifier.size(28.dp),tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.loading) "正在获取${state.loadingName}…" else state.config.name,
                            style = MaterialTheme.typography.titleMedium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                        Text(if (state.loading && state.ids.isNotEmpty()) "当前歌单仍可播放，加载完成后切换" else "${platformName(state.config.platform)} · ${state.ids.size} 首${if (state.cached) " · 上次加载" else ""}",style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onSelect(); online.load(forceRefresh = true) },enabled = !state.loading) { Icon(Icons.Rounded.Refresh,"刷新当前歌单") }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2000" to "高音质","320" to "标准音质").forEach { (value,label) ->
                        FilterChip(selected = state.config.bitrate == value,onClick = { onSelect(); online.load(state.config.copy(bitrate = value)) },
                            enabled = !state.loading,label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { custom = true },enabled = !state.loading,modifier = Modifier.weight(1f)) { Text("添加网络歌单") }
                    OutlinedButton(onClick = { onSelect(); online.load(MusicConfig(api = state.config.api,bitrate = state.config.bitrate)) },
                        enabled = !state.loading,modifier = Modifier.weight(1f)) { Text("恢复默认歌单") }
                }
                TextButton(onClick = {
                    runCatching { uriHandler.openUri(MusicProtocol.webUrl(state.config.platform,state.config.id)) }
                        .onFailure { onMessage("无法打开歌单网页，请检查是否已安装浏览器") }
                }) {
                    Text("打开歌单网页",style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        val cacheState by audioCache.state.collectAsStateWithLifecycle()
        val offlinePlaylist = remember(state) { online.currentPlaylist() }
        OfflineCachePanel(cacheState,offlinePlaylist,state.loading,
            onDownload = { runCatching { audioCache.download(online.currentPlaylist()) }.onFailure { onMessage("无法开始缓存，请重试") } },
            onPause = { audioCache.pause() },onResume = { audioCache.resume() },onClear = { audioCache.clear() },
            onPlay = { playlist ->
                onSelect(); online.openOffline(playlist,readyOnly = true,cache = cacheState)
                onOfflinePlay(cacheState.readySongs(playlist))
            })
        state.error?.let { error ->
            Surface(shape = RoundedCornerShape(20.dp),color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error,style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onErrorContainer)
                    if (state.ids.isNotEmpty()) Text("仍显示上次成功加载的歌单：${state.config.name}",style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { onSelect(); online.retry() },enabled = !state.loading) { Text("重试") }
                }
            }
        }
        Text("我的网络歌单",style = MaterialTheme.typography.titleLarge)
        if (savedPlaylists.isEmpty()) Text("获取自定义歌单后会保存在这里。缓存音频后可离线播放。",
            style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(savedPlaylists,key = { "${it.platform}:${it.id}" }) { item ->
                SavedNetworkPlaylistCard(item,selected = state.ids.isNotEmpty() && state.config.platform == item.platform && state.config.id == item.id,
                    enabled = !state.loading,onLoad = { onSelect(); online.load(state.config.copy(platform = item.platform,id = item.id,name = item.name)) },
                    onDelete = { deleting = item })
            }
        }
        Text("推荐歌单",style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(online.recommendations,key = { it.name }) { item ->
                val selected = state.ids.isNotEmpty() && state.config.platform == item.platform && state.config.id == item.id
                Surface(onClick = { onSelect(); online.load(state.config.copy(platform = item.platform,id = item.id,name = item.name)) },
                    enabled = !state.loading,
                    shape = RoundedCornerShape(24.dp),color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.width(210.dp).height(176.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.LibraryMusic,null,Modifier.size(20.dp))
                            Text("${platformName(item.platform)}${if (selected) " · 已载入" else ""}",style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.name,style = MaterialTheme.typography.titleMedium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                        Text(item.description.ifBlank { "为专注挑选一段旋律" },style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,overflow = TextOverflow.Ellipsis,modifier = Modifier.weight(1f))
                        Text(item.tags.joinToString(" · "),style = MaterialTheme.typography.labelSmall,maxLines = 1,overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (custom) NetworkPlaylistDialog(state.config,onDismiss = { custom = false }) {
        custom = false; onSelect(); online.load(it,saveCustom = true)
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { deleting = null },title = { Text("删除网络歌单？") },text = {
            Text("将从我的网络歌单中移除“${item.name}”。")
        },confirmButton = { TextButton(onClick = { online.deleteSavedPlaylist(item); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

@Composable internal fun SavedNetworkPlaylistCard(item: SavedNetworkPlaylist,selected: Boolean,enabled: Boolean,
    onLoad: () -> Unit,onDelete: () -> Unit) {
    Surface(onClick = onLoad,enabled = enabled,shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(228.dp).height(164.dp)) {
        Column(Modifier.padding(start = 16.dp,end = 8.dp,top = 8.dp,bottom = 16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${platformName(item.platform)}${if (selected) " · 已载入" else ""}",style = MaterialTheme.typography.labelSmall,modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline,"删除网络歌单 ${item.name}",Modifier.size(20.dp)) }
            }
            Text(item.name,style = MaterialTheme.typography.titleMedium,maxLines = 2,overflow = TextOverflow.Ellipsis,modifier = Modifier.weight(1f))
            Text("ID: ${item.id}",style = MaterialTheme.typography.bodySmall,maxLines = 1,overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun NetworkPlaylistDialog(config: MusicConfig,onDismiss: () -> Unit,onLoad: (MusicConfig) -> Unit) {
    var platform by remember { mutableStateOf(config.platform) }
    var input by remember { mutableStateOf(config.id) }
    val linkedPlatform = remember(input) { playlistLinkPlatform(input) }
    var api by remember { mutableStateOf(config.api) }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val id = MusicProtocol.playlistId(input)
        if (id == null) error = "无法识别歌单 ID，请粘贴完整链接或数字 ID"
        else {
            val target = config.copy(api = api.trim(),platform = linkedPlatform ?: platform,id = id,name = "自定义歌单 $id")
            runCatching { MusicProtocol.validate(target) }.onSuccess { onLoad(target) }.onFailure { error = it.message }
        }
    }
    AlertDialog(onDismissRequest = onDismiss,title = { Text("获取网络歌单") },text = {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("netease","tencent").forEach { value ->
                    FilterChip(selected = (linkedPlatform ?: platform) == value,onClick = { platform = value; error = null },
                        enabled = linkedPlatform == null,label = { Text(platformName(value)) })
                }
            }
            OutlinedTextField(input,{ input = it; playlistLinkPlatform(it)?.let { detected -> platform = detected }; error = null },
                label = { Text("歌单 ID 或分享链接") },singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),keyboardActions = KeyboardActions(onDone = { submit() }),
                supportingText = { Text(if (linkedPlatform != null) "已根据链接识别为${platformName(linkedPlatform)}" else "公开歌单；输入数字 ID 时，请先选择平台") })
            TextButton(onClick = { advanced = !advanced }) { Text("音乐源设置") }
            if (advanced) {
                OutlinedTextField(api,{ api = it; error = null },label = { Text("Meting 接口地址") },singleLine = true)
                TextButton(onClick = { api = DEFAULT_MUSIC_API }) { Text("恢复原站音乐源") }
            }
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        }
    },confirmButton = { TextButton(onClick = { submit() }) { Text("获取并保存") } },dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

internal fun playlistLinkPlatform(input: String): String? {
    val address = Regex("https?://[^\\s]+",RegexOption.IGNORE_CASE).find(input)?.value ?: input.trim()
    val host = runCatching { URI(if (address.contains("://")) address else "https://$address").host?.lowercase() }.getOrNull() ?: return null
    return when {
        host == "music.163.com" || host.endsWith(".music.163.com") -> "netease"
        host == "y.qq.com" || host.endsWith(".y.qq.com") || host == "music.qq.com" -> "tencent"
        else -> null
    }
}

private data class LyricsLoadState(val lines: List<LyricLine> = emptyList(),val error: String? = null,val loading: Boolean = true)

@Composable fun LyricsPanel(song: Song?,position: Long) {
    if (song?.lyrics.isNullOrBlank()) return
    var refresh by remember(song?.id) { mutableIntStateOf(0) }
    val state by produceState(LyricsLoadState(),song?.lyrics,refresh) {
        value = LyricsLoadState()
        try {
            val text = withContext(Dispatchers.IO) {
                song!!.lyrics.let { if (it.startsWith("[")) it else MusicHttp.text(it) }
            }
            value = LyricsLoadState(lines = MusicProtocol.lyrics(text),loading = false)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = LyricsLoadState(error = "歌词暂时无法加载",loading = false) }
    }
    val lines = state.lines
    val current = lines.indexOfLast { it.timeMs <= position }
    Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow,modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("歌词",style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary)
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(24.dp),strokeWidth = 2.dp)
                Text("正在加载歌词…",style = MaterialTheme.typography.bodySmall)
            } else if (state.error != null) {
                Text(state.error!!,style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { refresh++ }) { Text("重新加载") }
            } else if (lines.isEmpty()) Text("暂无同步歌词",style = MaterialTheme.typography.bodyMedium)
            else for (i in (current-1).coerceAtLeast(0)..(current+2).coerceIn(0,lines.lastIndex)) {
                Text(lines[i].text,style = if (i == current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
