@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.shshouse.studywithmiku

import android.view.LayoutInflater
import android.os.Looper
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import kotlin.math.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val sceneNames = listOf("午后咖啡馆","深夜工作室","安静的书桌")
private val sceneResources = listOf(R.drawable.scene_1,R.drawable.scene_2,R.drawable.scene_3)

@Composable fun FocusScreen(prefs: Preferences, clock: FocusClock, logs: Map<String,DayLog>,
    todos: List<Todo>,store: MikuStore,engine: FocusEngine,extras: OnlineExtras,toggle: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<TimerMode?>(null) }
    var fullScene by rememberSaveable { mutableStateOf(false) }
    var sceneControls by remember { mutableStateOf(true) }
    LaunchedEffect(fullScene,sceneControls) { if (fullScene && sceneControls) { delay(3000); sceneControls = false } }
    val quote by extras.quote.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val daily = logs[today.toString()] ?: DayLog()
    val todayTodos = todos.filter { it.date == today.toString() || (it.date.isEmpty() && it.pinned) }.sortedBy { it.done }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading("MAKE ROOM FOR YOURSELF","专注当下，\n未来慢慢发光。")
        Box(Modifier.fillMaxWidth().aspectRatio(1.82f).clipShape(28.dp)) {
            MikuScene(prefs.scene,prefs.animateScene,Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha = 0.65f)))))
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MIKU'S ROOM",fontSize = 10.sp,letterSpacing = 2.sp,color = Color.White.copy(alpha = 0.85f))
                    Text(sceneNames[prefs.scene],color = Color.White,fontWeight = FontWeight.Bold,fontSize = 18.sp)
                }
                IconButton(onClick = { store.updatePreferences(prefs.copy(scene = (prefs.scene+1)%3)) }) {
                    Icon(Icons.Rounded.Cached,"切换场景",tint = Color.White)
                }
                IconButton(onClick = { fullScene = true }) { Icon(Icons.Rounded.Fullscreen,"沉浸场景",tint = Color.White) }
            }
        }
        Surface(shape = RoundedCornerShape(36.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimerMode.entries.forEach { mode ->
                        val selected = clock.mode == mode
                        Surface(onClick = {
                            if (mode != clock.mode) {
                                if (clock.running || clock.elapsedMs > 0) pendingMode = mode
                                else engine.reset(mode,Phase.FOCUS)
                            }
                        },shape = CircleShape,color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)) {
                            Text(mode.label,Modifier.padding(vertical = 13.dp),textAlign = TextAlign.Center,
                                fontSize = 14.sp,fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(174.dp),contentAlignment = Alignment.Center) {
                    FlowerProgress(clock,prefs,Modifier.size(174.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (clock.mode == TimerMode.POMODORO) clock.phase.label else if (clock.running) "正在专注" else "准备开始",
                            style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary)
                        Text(timeLabel(if (clock.mode == TimerMode.STOPWATCH) clock.elapsedMs else clock.remainingMs),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = if (clock.elapsedMs > 3_600_000L) 54.sp else 68.sp),
                            maxLines = 1)
                        Text(if (clock.mode == TimerMode.POMODORO) "第 ${(clock.round+1).toString().padStart(2,'0')} 轮 · ${prefs.focusSeconds / 60.0} / ${prefs.breakSeconds / 60.0} 分钟"
                            else if (clock.mode == TimerMode.STOPWATCH) "按自己的节奏，向前一点点" else "只做眼前这一件事",
                            style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp),verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { confirmReset = true },modifier = Modifier.size(60.dp)) { Icon(Icons.Rounded.RestartAlt,"重置计时") }
                    Button(onClick = toggle,shapes = ButtonDefaults.shapes(shape = CircleShape,pressedShape = RoundedCornerShape(20.dp)),
                        modifier = Modifier.weight(1f).height(60.dp),enabled = true) {
                        Icon(if (clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (clock.running) "暂停一下" else if (clock.elapsedMs > 0) "继续专注" else if (clock.phase == Phase.FOCUS) "开始专注" else "开始休息",fontSize = 17.sp)
                    }
                }
                if (clock.phase != Phase.FOCUS) TextButton(onClick = { engine.skipBreak() }) { Text("跳过休息，回到专注") }
            }
        }
        if (prefs.showHitokoto) Surface(onClick = { extras.refreshQuote() },shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(quote.text,style = MaterialTheme.typography.bodyLarge)
                Text(quote.source,style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("今日专注","${daily.millis / 60_000}","分钟",Icons.Rounded.AutoAwesome,Modifier.weight(1f),true)
            StatCard("完成番茄","${daily.sessions}","个",Icons.Rounded.LocalFlorist,Modifier.weight(1f),false)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                Text("一点点，靠近今日目标",style = MaterialTheme.typography.labelMedium)
                Text("${daily.millis / 60_000} / ${prefs.dailyGoalMinutes} 分钟",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { (daily.millis / (prefs.dailyGoalMinutes * 60_000f)).coerceIn(0f,1f) },modifier = Modifier.fillMaxWidth().height(8.dp))
        }
        if (todayTodos.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp)) {
                    Text("今天想完成的事",style = MaterialTheme.typography.titleMedium)
                    todayTodos.take(3).forEach { todo ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(todo.done,{ store.toggleTodo(todo.id) })
                            Text(todo.text,style = MaterialTheme.typography.bodyMedium,modifier = Modifier.weight(1f),maxLines = 2)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.miku_sticker),null,Modifier.size(58.dp),contentScale = ContentScale.Fit)
            Column {
                Text("不着急，我会一直陪着你。",style = MaterialTheme.typography.bodyMedium)
                Text("一点点进步，也很了不起。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (confirmReset || pendingMode != null) AlertDialog(onDismissRequest = { confirmReset = false; pendingMode = null },
        icon = { Icon(Icons.Rounded.RestartAlt,null) },title = { Text(if (pendingMode != null) "切换计时方式？" else "重新开始这一轮？") },
        text = { Text("已专注的时间会保留在学习记录中，本轮计时将重新开始。") },
        confirmButton = { TextButton(onClick = { engine.reset(pendingMode ?: clock.mode,if (pendingMode != null) Phase.FOCUS else clock.phase); confirmReset = false; pendingMode = null }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { confirmReset = false; pendingMode = null }) { Text("继续当前计时") } })
    if (fullScene) Dialog(onDismissRequest = { fullScene = false },properties = DialogProperties(usePlatformDefaultWidth = false,decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { sceneControls = !sceneControls } }) {
            MikuScene(prefs.scene,prefs.animateScene,Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = prefs.overlayOpacity),Color.Transparent,Color.Black.copy(alpha = (prefs.overlayOpacity+.25f).coerceAtMost(1f))))))
            if (sceneControls) IconButton(onClick = { fullScene = false },modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)) { Icon(Icons.Rounded.Close,"退出沉浸",tint = Color.White) }
            if (!prefs.hidePomodoroOnIdle || clock.running || sceneControls) Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(32.dp),horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clock.phase.label,color = Color.White)
                Text(timeLabel(if (clock.mode == TimerMode.STOPWATCH) clock.elapsedMs else clock.remainingMs),style = MaterialTheme.typography.displayLarge,color = Color.White)
                if (sceneControls) FilledTonalButton(onClick = toggle) { Icon(if (clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null); Text(if (clock.running) "暂停" else "开始") }
            }
        }
    }
}

fun Modifier.clipShape(radius: androidx.compose.ui.unit.Dp) = this.then(Modifier.background(Color.Transparent,RoundedCornerShape(radius))).then(Modifier.clip(RoundedCornerShape(radius)))

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable fun MikuScene(scene: Int, animate: Boolean,modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    Box(modifier) {
        Image(painterResource(sceneResources[scene]),null,Modifier.fillMaxSize(),contentScale = ContentScale.Crop)
        if (animate) {
            var exo by remember(scene) { mutableStateOf<ExoPlayer?>(null) }
            LaunchedEffect(scene) {
                var created: ExoPlayer? = null
                try {
                    // Show the poster first; class loading/player construction need not block a frame.
                    withContext(Dispatchers.Default) {
                        created = ExoPlayer.Builder(context).setLooper(Looper.getMainLooper()).build()
                    }
                    created?.apply {
                        volume = 0f; repeatMode = Player.REPEAT_MODE_ONE
                        setMediaItem(MediaItem.fromUri("asset:///scenes/${scene+1}.mp4")); prepare()
                        playWhenReady = lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    }
                    exo = created
                    awaitCancellation()
                } finally { exo = null; created?.release() }
            }
            exo?.let { player ->
                DisposableEffect(player,lifecycle) {
                    val observer = LifecycleEventObserver { _,event ->
                        if (event == Lifecycle.Event.ON_START) player.play()
                        if (event == Lifecycle.Event.ON_STOP) player.pause()
                    }
                    lifecycle.lifecycle.addObserver(observer)
                    onDispose { lifecycle.lifecycle.removeObserver(observer) }
                }
                key(scene) {
                    AndroidView(factory = { LayoutInflater.from(it).inflate(R.layout.scene_player,android.widget.FrameLayout(it),false) as PlayerView },
                        modifier = Modifier.fillMaxSize(),update = { it.player = player })
                }
            }
        }
    }
}

@Composable private fun FlowerProgress(clock: FocusClock,prefs: Preferences,modifier: Modifier) {
    val container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val duration = FocusClock.duration(clock.mode,clock.phase,prefs)
    val progress = if (clock.mode == TimerMode.STOPWATCH) (clock.elapsedMs % 60_000) / 60_000f
        else 1f - clock.remainingMs.toFloat() / duration.coerceAtLeast(1)
    val animated by animateFloatAsState(progress,animationSpec = tween(600),label = "专注进度")
    Canvas(modifier) {
        val path = Path()
        val radius = size.minDimension * 0.47f
        for (i in 0..240) {
            val angle = i / 240f * 2 * PI.toFloat()
            val r = radius * (1 + 0.035f * cos(angle * 10))
            val x = center.x + r * cos(angle); val y = center.y + r * sin(angle)
            if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        path.close(); drawPath(path,container)
        drawArc(primary,-90f,animated.coerceIn(0f,1f)*360,false,Offset(7.dp.toPx(),7.dp.toPx()),
            androidx.compose.ui.geometry.Size(size.width-14.dp.toPx(),size.height-14.dp.toPx()),style = Stroke(3.dp.toPx()))
    }
}

@Composable fun StatCard(label: String,value: String,unit: String,icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,green: Boolean = true) {
    Surface(modifier,shape = RoundedCornerShape(26.dp),color = if (green) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(18.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Text(label,style = MaterialTheme.typography.labelMedium,modifier = Modifier.weight(1f))
                Icon(icon,null,Modifier.size(18.dp))
            }
            Row(verticalAlignment = Alignment.Bottom,horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(value,fontSize = 34.sp,fontWeight = FontWeight.Bold,letterSpacing = (-1).sp)
                Text(unit,Modifier.padding(bottom = 6.dp),style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
