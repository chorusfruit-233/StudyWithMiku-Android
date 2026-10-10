@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.chorusfruit233.studywithmiku

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as MikuApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by app.store.prefs.collectAsStateWithLifecycle()
            DisposableEffect(prefs.keepScreenOn) {
                if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            MikuTheme(prefs) { MikuApp(app) }
        }
    }
    override fun onStart() {
        super.onStart()
        app.chat.start(); app.extras.start()
        if (app.timer.state.value.running) ContextCompat.startForegroundService(this,Intent(this,FocusService::class.java))
    }
    override fun onStop() { app.timer.flush(); app.chat.stop(); app.extras.stop(); super.onStop() }
}

@Composable private fun MikuApp(app: MikuApplication) {
    val prefs by app.store.prefs.collectAsStateWithLifecycle()
    val timer by app.timer.state.collectAsStateWithLifecycle()
    val library by app.store.library.collectAsStateWithLifecycle()
    val logs by app.store.logs.collectAsStateWithLifecycle()
    val todos by app.store.todos.collectAsStateWithLifecycle()
    val music = remember { MusicConnection(app) }
    val playback by music.state.collectAsStateWithLifecycle()
    LaunchedEffect(library.songs,playback.connected) { music.syncLibrary(library.songs) }
    DisposableEffect(music) { onDispose { music.close() } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf(false) }
    var chatSheet by remember { mutableStateOf(false) }
    val chat by app.chat.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    LaunchedEffect(playback.error) { playback.error?.let { snackbar.showSnackbar(it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.timer.toggle() }
    fun toggleTimer() {
        if (!timer.running && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !app.getSharedPreferences("permissions",0).getBoolean("asked",false)) {
            app.getSharedPreferences("permissions",0).edit().putBoolean("asked",true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else app.timer.toggle()
    }
    val icons = listOf(Icons.Rounded.Timer,Icons.Rounded.Headphones,Icons.Rounded.Checklist,Icons.Rounded.BarChart,Icons.Rounded.Forum)
    val labels = listOf("专注","音乐","计划","记录","社区")
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp,vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp),color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Rounded.GraphicEq,null,Modifier.padding(9.dp).size(21.dp),tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("STUDY WITH MIKU",fontSize = 12.sp,fontWeight = FontWeight.ExtraBold,letterSpacing = 1.4.sp)
                        Text(if (chat.connected) "${chat.online} 人正在一起学习${if (chat.adminOnline) " · 管理员在线" else ""}" else "和 Miku 一起，慢慢变好",fontSize = 10.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { chatSheet = true }) { Icon(Icons.Rounded.ChatBubbleOutline,"打开聊天室") }
                IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Tune,"设置") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (playback.songId != null) MiniPlayer(library.songs.find { it.id == playback.songId },playback,music) { player = true }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation = 0.dp) {
                    labels.forEachIndexed { i,label ->
                        NavigationBarItem(selected = tab == i,onClick = { tab = i },icon = { Icon(icons[i],label) },
                            label = { Text(label,fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                    }
                }
            }
        }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            AnimatedContent(targetState = tab,transitionSpec = { fadeIn() togetherWith fadeOut() },label = "页面切换") { selected ->
                when (selected) {
                    0 -> FocusScreen(prefs,timer,logs,todos,app.store,app.timer,app.extras,::toggleTimer)
                    1 -> LibraryScreen(library,playback,app.store,music,app.onlineMusic,::message) { player = true }
                    2 -> TasksScreen(todos,app.store) { content -> if (!app.auth.state.value.loggedIn) app.openLogin() else if (app.chat.sendChat(content,app.extras.messageLocation())) { tab = 4; message("已分享到聊天室") } else message("连接成功后再分享") }
                    3 -> RecordsScreen(logs,todos)
                    else -> CommunityScreen(app,music,::message) { player = true }
                }
            }
        }
    }
    if (settings) SettingsSheet(prefs,app.store,app.timer) { settings = false }
    if (player) PlayerSheet(library,playback,app.store,music,onShare = { song ->
        if (!app.auth.state.value.loggedIn) app.openLogin()
        else if (app.chat.sendChat(StudyProtocol.songShare(song) ?: "",app.extras.messageLocation())) { player = false; tab = 4; message("已分享到聊天室") }
        else message("连接成功后再分享")
    }) { player = false }
    if (chatSheet) FocusChatSheet(onDismiss = { chatSheet = false }) {
        ChatPane(app,music,::message) { player = true }
    }
    SyncChoiceDialog(app)
}

@Composable fun PageHeading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 12.dp,bottom = 10.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow,color = MaterialTheme.colorScheme.primary,style = MaterialTheme.typography.labelMedium,letterSpacing = 2.sp)
        Text(title,style = MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it,style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
