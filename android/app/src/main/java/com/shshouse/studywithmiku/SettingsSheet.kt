@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.shshouse.studywithmiku

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable fun SettingsSheet(prefs: Preferences,store: MikuStore,engine: FocusEngine,onDismiss: () -> Unit) {
    val clock by engine.state.collectAsStateWithLifecycle()
    var license by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    fun update(p: Preferences,resetClock: Boolean = false) {
        store.updatePreferences(p)
        if (resetClock && !clock.running) engine.reset()
    }
    ModalBottomSheet(onDismissRequest = onDismiss,sheetState = sheet,containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("让这里，更像你的角落。",style = MaterialTheme.typography.headlineMedium)
            Text("按自己的节奏，调整一点点。",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingSection("专注节奏") {
                if (clock.running) Text("暂停计时后，可以调整本轮时长。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.primary)
                MinuteSlider("专注时长",prefs.focusMinutes,1..120,!clock.running) { update(prefs.copy(focusMinutes = it),true) }
                MinuteSlider("短休息",prefs.breakMinutes,1..30,!clock.running) { update(prefs.copy(breakMinutes = it),true) }
                MinuteSlider("长休息 · 每四轮一次",prefs.longBreakMinutes,1..60,!clock.running) { update(prefs.copy(longBreakMinutes = it),true) }
                MinuteSlider("倒计时时长",prefs.countdownMinutes,1..180,!clock.running) { update(prefs.copy(countdownMinutes = it),true) }
                MinuteSlider("每日专注目标",prefs.dailyGoalMinutes,15..360,true) { update(prefs.copy(dailyGoalMinutes = it)) }
                SettingSwitch("Miku 完成语音","计时结束时，听到一声鼓励",prefs.completionSound) { update(prefs.copy(completionSound = it)) }
            }
            SettingSection("外观与陪伴") {
                Text("主题",style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("跟随系统","浅色","深色").forEachIndexed { i,title ->
                        FilterChip(selected = prefs.theme == i,onClick = { update(prefs.copy(theme = i)) },label = { Text(title) },shape = CircleShape)
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) SettingSwitch("壁纸取色","使用系统壁纸的颜色",prefs.dynamicColor) { update(prefs.copy(dynamicColor = it)) }
                SettingSwitch("动态陪伴场景","关闭后显示静态画面，减少耗电",prefs.animateScene) { update(prefs.copy(animateScene = it)) }
                SettingSwitch("保持屏幕常亮","应用打开时，让 Miku 留在屏幕上",prefs.keepScreenOn) { update(prefs.copy(keepScreenOn = it)) }
            }
            Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(18.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.OfflinePin,null,Modifier.size(30.dp),tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("陪伴，也可以完全离线。",style = MaterialTheme.typography.titleMedium)
                        Text("无需账户，所有学习记录和导入音乐\n只保存在这台设备。",style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Study With Miku · 1.0.0",style = MaterialTheme.typography.titleMedium)
                Text("原作：shshouse / StudyWithMiku\nAndroid 离线版 · Material 3 Expressive\n场景、音乐、语音和贴图沿用原项目，创作者署名见内置资源说明。",
                    style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { license = true }) { Text("开源许可与资源说明") }
            }
        }
    }
    if (license) {
        val text = remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { license = false },title = { Text("开源许可与资源") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("基于 github.com/shshouse/StudyWithMiku，遵循 GNU GPL v3。\n\n音乐：和未来一起喝茶、Alice In Cradle、Snail’s House。原始音视频和插画资源来源于原仓库；资源权利归各自创作者。\n\nAndroidX / Jetpack Compose / Media3：Apache License 2.0。",style = MaterialTheme.typography.bodyMedium)
                Text(text,fontSize = 11.sp,lineHeight = 16.sp)
            } },confirmButton = { TextButton(onClick = { license = false }) { Text("关闭") } })
    }
}

@Composable private fun SettingSection(title: String,content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title,style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary,modifier = Modifier.padding(start = 4.dp,top = 6.dp))
        Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp),verticalArrangement = Arrangement.spacedBy(12.dp),content = content)
        }
    }
}
@Composable private fun MinuteSlider(label: String,value: Int,range: IntRange,enabled: Boolean,onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style = MaterialTheme.typography.bodyMedium)
            Text("${draft.roundToInt()} 分钟",style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = draft,onValueChange = { draft = it },onValueChangeFinished = { onChange(draft.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),steps = range.last-range.first-1,enabled = enabled)
    }
}
@Composable private fun SettingSwitch(title: String,subtitle: String,checked: Boolean,onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title,style = MaterialTheme.typography.titleMedium)
            Text(subtitle,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked,onChange)
    }
}
