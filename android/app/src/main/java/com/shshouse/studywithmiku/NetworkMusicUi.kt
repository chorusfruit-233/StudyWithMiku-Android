@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.shshouse.studywithmiku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

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

@Composable fun NetworkMusicPanel(state: OnlineMusicState,online: OnlineMusic,onSelect: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
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
                        Text("${platformName(state.config.platform)} · ${state.ids.size} 首${if (state.cached) " · 上次加载" else ""}",style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onSelect(); online.load() },enabled = !state.loading) { Icon(Icons.Rounded.Refresh,"刷新当前歌单") }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2000" to "高音质","320" to "标准音质").forEach { (value,label) ->
                        FilterChip(selected = state.config.bitrate == value,onClick = { onSelect(); online.load(state.config.copy(bitrate = value)) },
                            enabled = !state.loading,label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { custom = true },modifier = Modifier.weight(1f)) { Text("自定义歌单") }
                    OutlinedButton(onClick = { onSelect(); online.load(MusicConfig(api = state.config.api,bitrate = state.config.bitrate)) },
                        modifier = Modifier.weight(1f)) { Text("默认歌单") }
                }
                TextButton(onClick = { runCatching { uriHandler.openUri(MusicProtocol.webUrl(state.config.platform,state.config.id)) } }) {
                    Text("打开歌单 · ID ${state.config.id}",style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        state.error?.let { error ->
            Surface(shape = RoundedCornerShape(20.dp),color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error,style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { onSelect(); online.retry() },enabled = !state.loading) { Text("重试") }
                }
            }
        }
        Text("推荐歌单",style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(online.recommendations,key = { it.name }) { item ->
                val selected = state.config.platform == item.platform && state.config.id == item.id
                Surface(onClick = { onSelect(); online.load(state.config.copy(platform = item.platform,id = item.id,name = item.name)) },
                    shape = RoundedCornerShape(24.dp),color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.width(210.dp).height(176.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (selected) Icons.Rounded.GraphicEq else Icons.Rounded.LibraryMusic,null,Modifier.size(20.dp))
                            Text(platformName(item.platform),style = MaterialTheme.typography.labelSmall)
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
        custom = false; onSelect(); online.load(it)
    }
}

@Composable private fun NetworkPlaylistDialog(config: MusicConfig,onDismiss: () -> Unit,onLoad: (MusicConfig) -> Unit) {
    var platform by remember { mutableStateOf(config.platform) }
    var input by remember { mutableStateOf(config.id) }
    var api by remember { mutableStateOf(config.api) }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,title = { Text("获取网络歌单") },text = {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("netease","tencent").forEach { value ->
                    FilterChip(selected = platform == value,onClick = { platform = value },label = { Text(platformName(value)) })
                }
            }
            OutlinedTextField(input,{ input = it; error = null },label = { Text("歌单 ID 或分享链接") },singleLine = true,
                supportingText = { Text("网易云 / QQ 音乐的公开歌单") })
            TextButton(onClick = { advanced = !advanced }) { Text("音乐源设置") }
            if (advanced) {
                OutlinedTextField(api,{ api = it; error = null },label = { Text("Meting 接口地址") },singleLine = true)
                TextButton(onClick = { api = DEFAULT_MUSIC_API }) { Text("恢复原站音乐源") }
            }
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        }
    },confirmButton = { TextButton(onClick = {
        val id = MusicProtocol.playlistId(input)
        if (id == null) error = "无法识别歌单 ID，请粘贴完整链接或数字 ID"
        else {
            val target = config.copy(api = api.trim(),platform = platform,id = id,name = "自定义歌单 $id")
            runCatching { MusicProtocol.validate(target) }.onSuccess { onLoad(target) }.onFailure { error = it.message }
        }
    }) { Text("获取歌单") } },dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable fun LyricsPanel(song: Song?,position: Long) {
    if (song?.lyrics.isNullOrBlank()) return
    var refresh by remember(song?.id) { mutableIntStateOf(0) }
    val state by produceState<Pair<List<LyricLine>,String?>>(emptyList<LyricLine>() to null,song?.lyrics,refresh) {
        value = emptyList<LyricLine>() to null
        try {
            val text = withContext(Dispatchers.IO) {
                song!!.lyrics.let { if (it.startsWith("[")) it else MusicHttp.text(it) }
            }
            value = MusicProtocol.lyrics(text) to null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = emptyList<LyricLine>() to "歌词暂时无法加载" }
    }
    val lines = state.first
    val current = lines.indexOfLast { it.timeMs <= position }
    Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow,modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("歌词",style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary)
            if (state.second != null) {
                Text(state.second!!,style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { refresh++ }) { Text("重新加载") }
            } else if (lines.isEmpty()) Text("暂无同步歌词",style = MaterialTheme.typography.bodyMedium)
            else for (i in (current-1).coerceAtLeast(0)..(current+2).coerceIn(0,lines.lastIndex)) {
                Text(lines[i].text,style = if (i == current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
