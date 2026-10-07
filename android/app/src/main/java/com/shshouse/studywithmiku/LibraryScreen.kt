@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.shshouse.studywithmiku

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun LibraryScreen(library: Library,playback: PlaybackState,store: MikuStore,music: MusicConnection,
    online: OnlineMusic,message: (String) -> Unit,openPlayer: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("online") }
    val network by online.state.collectAsStateWithLifecycle()
    LaunchedEffect(online) { online.ensureLoaded() }
    var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Playlist?>(null) }
    var removing by remember { mutableStateOf<Song?>(null) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            importing = true
            var ok = 0; var failed = 0
            withContext(Dispatchers.IO) { uris.forEach { uri ->
                runCatching { store.importSong(uri) }.onSuccess { ok++ }.onFailure { failed++ }
            } }
            importing = false
            filter = "imported"
            message(if (failed == 0) "已导入 $ok 首音乐" else "已导入 $ok 首，$failed 个文件无法读取或不是有效音频")
        }
    }
    val options = listOf("online" to "在线歌单","all" to "全部音乐","builtin" to "内置精选","favorites" to "我的收藏","imported" to "我的导入") + library.playlists.map { it.id to it.name }
    val currentList = when (filter) {
        "builtin" -> library.songs.filter { it.builtin }
        "favorites" -> library.songs.filter { it.id in library.favorites }
        "imported" -> library.songs.filter { !it.builtin && !it.online }
        "online" -> network.ids.mapNotNull { id -> library.songs.find { it.id == id } }
        "all" -> library.songs
        else -> library.playlists.find { it.id == filter }?.songIds?.mapNotNull { id -> library.songs.find { it.id == id } } ?: library.songs
    }
    val visible = currentList.filter { it.title.contains(query,true) || it.artist.contains(query,true) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding = PaddingValues(start = 24.dp,end = 24.dp,bottom = 24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("A SOUNDTRACK FOR YOUR DAY","把世界调成\n喜欢的音量。","在线歌单与本地音乐，随时陪你进入状态。") }
        item { NetworkMusicPanel(network,online,onSelect = { filter = "online" }) }
        item {
            Surface(onClick = { if (playback.connected) { music.play(library.songs.filter { it.builtin }); openPlayer() } },
                shape = RoundedCornerShape(32.dp),color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Cover(library.songs.find { it.title.contains("MIKU休息") } ?: library.songs.firstOrNull(),Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
                    Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("MIKU'S SELECTION",fontSize = 10.sp,letterSpacing = 1.5.sp,color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("和未来一起喝茶",style = MaterialTheme.typography.titleLarge)
                        Text("内置精选 · 完全离线",style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.PlayCircle,"播放内置精选",Modifier.size(36.dp),tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { import.launch(arrayOf("audio/*")) },enabled = !importing,modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Rounded.FileUpload,null,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(if (importing) "正在导入…" else "导入音乐")
                }
                OutlinedButton(onClick = { editing = null; editor = true },modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd,null,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("新建歌单")
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                options.forEach { (id,name) -> FilterChip(selected = filter == id,onClick = { filter = id },label = { Text(name) },shape = CircleShape) }
            }
        }
        item {
            OutlinedTextField(query,{ query = it },modifier = Modifier.fillMaxWidth(),singleLine = true,shape = RoundedCornerShape(24.dp),
                placeholder = { Text("找一首歌，或一位音乐人",fontSize = 14.sp) },leadingIcon = { Icon(Icons.Rounded.Search,null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close,"清除搜索") } })
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Text("${options.find { it.first == filter }?.second ?: "全部音乐"} · ${visible.size}",modifier = Modifier.weight(1f),style = MaterialTheme.typography.titleMedium)
                library.playlists.find { it.id == filter }?.let { list ->
                    IconButton(onClick = { editing = list; editor = true }) { Icon(Icons.Rounded.Edit,"编辑歌单") }
                }
                IconButton(onClick = { music.play(visible) },enabled = visible.isNotEmpty() && playback.connected) { Icon(Icons.Rounded.PlayArrow,"播放此歌单") }
            }
        }
        if (visible.isEmpty()) item { EmptyState(if (filter == "online" && network.loading) "正在加载网络歌单…" else "还没有音乐在这里。",
            if (query.isNotBlank()) "试试换一个搜索词。" else if (filter == "online") "选择推荐歌单或输入歌单 ID，也可以切换到内置精选。" else "导入喜欢的音乐，或把一首歌加入收藏。",Icons.Rounded.MusicNote) }
        items(visible,key = { it.id }) { song ->
            val selected = playback.songId == song.id
            Surface(onClick = { if (selected) openPlayer() else music.play(visible,song.id) },shape = RoundedCornerShape(22.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(song,Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)))
                    Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(song.title,style = MaterialTheme.typography.titleMedium,maxLines = 1,overflow = TextOverflow.Ellipsis)
                        Text(song.artist,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant,maxLines = 1,overflow = TextOverflow.Ellipsis)
                    }
                    if (selected && playback.playing) Icon(Icons.Rounded.GraphicEq,null,Modifier.size(20.dp),tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { store.toggleFavorite(song.id) },modifier = Modifier.size(40.dp)) {
                        Icon(if (song.id in library.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (song.id in library.favorites) "取消收藏" else "收藏",Modifier.size(20.dp),tint = if (song.id in library.favorites) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!song.builtin && !song.online) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true },modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert,"更多") }
                            DropdownMenu(expanded = menu,onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("删除导入的音乐") },onClick = { removing = song; menu = false },leadingIcon = { Icon(Icons.Rounded.DeleteOutline,null) })
                            }
                        }
                    }
                }
            }
        }
        item { Text(if (filter == "online") "${network.config.name} · 在线歌曲需要网络；收藏和歌单保存在这台设备。" else "内置曲目和已导入音乐无需联网播放。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant,modifier = Modifier.padding(top = 8.dp)) }
    }
    if (editor) PlaylistEditor(library,editing,onSave = { name,ids -> store.savePlaylist(name,ids,editing?.id); editor = false },
        onDelete = editing?.let { { store.deletePlaylist(it.id); filter = "all"; editor = false } },onDismiss = { editor = false })
    removing?.let { song -> AlertDialog(onDismissRequest = { removing = null },title = { Text("删除这首音乐？") },
        text = { Text("“${song.title}”将从应用和歌单中移除，手机上的原文件会保留。") },
        confirmButton = { TextButton(onClick = { music.remove(song.id); store.removeSong(song.id); removing = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
}

@Composable private fun PlaylistEditor(library: Library,editing: Playlist?,onSave: (String,List<String>) -> Unit,onDelete: (() -> Unit)?,onDismiss: () -> Unit) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var chosen by remember(editing) { mutableStateOf(editing?.songIds?.toSet() ?: emptySet()) }
    AlertDialog(onDismissRequest = onDismiss,title = { Text(if (editing == null) "装进一个歌单" else "编辑歌单") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name,{ if (it.length <= 40) name = it },label = { Text("歌单名称") },singleLine = true)
            Text("选择音乐 · ${chosen.size} 首",style = MaterialTheme.typography.labelLarge)
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(library.songs,key = { it.id }) { song ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (song.id in chosen) chosen-song.id else chosen+song.id },verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(song.id in chosen,{ chosen = if (song.id in chosen) chosen-song.id else chosen+song.id })
                        Text(song.title,style = MaterialTheme.typography.bodyMedium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            onDelete?.let { TextButton(onClick = it) { Text("删除此歌单",color = MaterialTheme.colorScheme.error) } }
        } },confirmButton = { TextButton(onClick = { onSave(name,chosen.toList()) },enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable fun Cover(song: Song?,modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,song?.cover) {
        value = withContext(Dispatchers.IO) {
            runCatching { when {
                song?.cover?.startsWith("asset:///") == true -> context.assets.open(song.cover.removePrefix("asset:///")).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                song?.cover?.startsWith("http") == true -> RemoteCovers.get(song.cover)?.asImageBitmap()
                else -> null
            } }.getOrNull()
        }
    }
    Box(modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.tertiaryContainer))),contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it,null,Modifier.fillMaxSize(),contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Rounded.MusicNote,null,Modifier.fillMaxSize(0.45f),tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable fun MiniPlayer(song: Song?,state: PlaybackState,music: MusicConnection,open: () -> Unit) {
    Surface(onClick = open,shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp,vertical = 8.dp)) {
        Column {
            Row(Modifier.padding(start = 10.dp,end = 6.dp,top = 9.dp,bottom = 9.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cover(song,Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)))
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "本地音乐",style = MaterialTheme.typography.labelLarge,maxLines = 1,overflow = TextOverflow.Ellipsis)
                    Text(if (state.loading) "正在准备音乐…" else song?.artist ?: "",style = MaterialTheme.typography.bodySmall,maxLines = 1,overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { music.toggle() }) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if (state.playing) "暂停音乐" else "播放音乐") }
                IconButton(onClick = { music.next() }) { Icon(Icons.Rounded.SkipNext,"下一首") }
            }
            LinearProgressIndicator(progress = { (state.position.toFloat()/state.duration.coerceAtLeast(1)).coerceIn(0f,1f) },modifier = Modifier.fillMaxWidth().height(2.dp),trackColor = MaterialTheme.colorScheme.secondaryContainer)
        }
    }
}

@Composable fun PlayerSheet(library: Library,state: PlaybackState,store: MikuStore,music: MusicConnection,onDismiss: () -> Unit) {
    val song = library.songs.find { it.id == state.songId }
    var drag by remember { mutableStateOf<Float?>(null) }
    var queue by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss,sheetState = sheet,containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("NOW PLAYING · ${if (song?.online == true) platformName(song.platform) else "本地播放"}",style = MaterialTheme.typography.labelMedium,letterSpacing = 2.sp,color = MaterialTheme.colorScheme.primary)
            Cover(song,Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(48.dp)))
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(song?.title ?: "选择一首音乐",style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = TextOverflow.Ellipsis)
                    Text(song?.artist ?: "",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { song?.let { store.toggleFavorite(it.id) } },enabled = song != null) {
                    Icon(if (song?.id in library.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,"收藏",tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column {
                Slider(value = drag ?: state.position.toFloat().coerceIn(0f,state.duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { drag = it },onValueChangeFinished = { drag?.let { music.seek(it.toLong()) }; drag = null },
                    valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),enabled = state.duration > 0)
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeLabel((drag ?: state.position.toFloat()).toLong()),style = MaterialTheme.typography.labelSmall)
                    Text(timeLabel(state.duration),style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { music.shuffle() }) { Icon(Icons.Rounded.Shuffle,"随机播放",tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { music.previous() },modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipPrevious,"上一首",Modifier.size(32.dp)) }
                Button(onClick = { music.toggle() },shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(if (state.playing) 28.dp else 44.dp),pressedShape = RoundedCornerShape(20.dp)),
                    modifier = Modifier.size(80.dp),contentPadding = PaddingValues(0.dp),enabled = song != null && state.connected) {
                    Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if (state.playing) "暂停" else "播放",Modifier.size(38.dp))
                }
                IconButton(onClick = { music.next() },modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipNext,"下一首",Modifier.size(32.dp)) }
                IconButton(onClick = { music.repeat() }) { Icon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    if (state.repeat == Player.REPEAT_MODE_ONE) "单曲循环" else if (state.repeat == Player.REPEAT_MODE_ALL) "列表循环" else "顺序播放",
                    tint = if (state.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(if (state.shuffle) "随机播放" else if (state.repeat == Player.REPEAT_MODE_ONE) "单曲循环" else if (state.repeat == Player.REPEAT_MODE_ALL) "列表循环" else "顺序播放",
                style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeDown,"音量")
                Slider(state.volume,{ music.volume(it) },modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.VolumeUp,null)
            }
            TextButton(onClick = { queue = !queue }) { Text("播放队列 · ${state.queueIds.size} 首") }
            if (queue) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(state.queueIds,key = { it }) { id ->
                    val queued = library.songs.find { it.id == id }
                    ListItem(headlineContent = { Text(queued?.title ?: "音乐",maxLines = 1,overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(queued?.artist ?: "",maxLines = 1) },
                        leadingContent = { Icon(if (id == state.songId) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,null) },
                        modifier = Modifier.clickable { music.select(id) })
                }
            }
            state.error?.let { Text(it,color = MaterialTheme.colorScheme.error,style = MaterialTheme.typography.bodyMedium) }
            LyricsPanel(song,state.position)
        }
    }
}
