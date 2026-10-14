package io.github.chorusfruit233.studywithmiku

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable internal fun OfflineCachePanel(state: AudioCacheState,playlist: OfflinePlaylist,loading: Boolean,
    onDownload: () -> Unit,onPause: () -> Unit,onResume: () -> Unit,onClear: () -> Unit,onPlay: (OfflinePlaylist) -> Unit) {
    var clearing by remember { mutableStateOf(false) }
    val ready = state.readySongs(playlist).size
    val remote = playlist.songs.filter { it.online }
    val failed = remote.count { state.songs[it.cacheKey]?.status == AudioCacheStatus.FAILED }
    val needsDownload = remote.any { state.songs[it.cacheKey]?.status.let { status -> status == null || status == AudioCacheStatus.FAILED } }
    val complete = playlist.songs.isNotEmpty() && ready == playlist.songs.size
    val size = Formatter.formatShortFileSize(LocalContext.current,state.bytes)
    Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("离线缓存",style = MaterialTheme.typography.titleMedium)
            Text("当前歌单 $ready / ${playlist.songs.size} 首可离线播放 · 缓存占用 $size",style = MaterialTheme.typography.bodySmall)
            Text("缓存完整音频后可离线播放，下载使用当前网络。",style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.pending) {
                Text(when {
                    state.paused -> "缓存已暂停，可继续下载"
                    state.waitingForNetwork -> "等待网络连接后继续缓存"
                    else -> "正在缓存音乐…"
                },style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (failed > 0) Text("$failed 首缓存失败。可刷新歌单后重试，已完成的缓存会保留。",
                style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error)
            if (complete) Text("当前歌单已离线可用",color = MaterialTheme.colorScheme.primary,style = MaterialTheme.typography.labelLarge)
            else if (needsDownload) FilledTonalButton(onClick = onDownload,enabled = !loading && state.initialized) {
                Icon(Icons.Rounded.Download,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (failed > 0 || ready > 0) "补全当前歌单缓存" else "缓存当前歌单")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.pending) TextButton(onClick = if (state.paused) onResume else onPause) {
                    Text(if (state.paused) "继续全部缓存" else "暂停全部缓存")
                }
                if (state.songs.isNotEmpty() || state.playlists.isNotEmpty()) TextButton(onClick = { clearing = true },enabled = state.initialized) {
                    Text("清除全部缓存")
                }
            }
        }
    }
    if (state.playlists.isNotEmpty()) {
        Text("离线歌单",style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.playlists,key = { it.key }) { item ->
                val count = state.readySongs(item).size
                Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainer,modifier = Modifier.width(228.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.config.name,style = MaterialTheme.typography.titleMedium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                        Text("${platformName(item.config.platform)} · ${if (item.config.bitrate == "320") "标准音质" else "高音质"}",style = MaterialTheme.typography.bodySmall)
                        Text("$count / ${item.songs.size} 首可离线播放",style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { onPlay(item) },enabled = count > 0) {
                            Icon(Icons.Rounded.OfflinePin,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("离线播放")
                        }
                    }
                }
            }
        }
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false },title = { Text("清除全部音频缓存？") },
        text = { Text("将清除 $size 的网络歌曲音频和未完成的下载。歌单、收藏、内置音乐及导入音乐会保留；网络歌曲需要联网才能再次播放。") },
        confirmButton = { TextButton(onClick = { onClear(); clearing = false }) { Text("清除缓存") } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text("取消") } })
}
