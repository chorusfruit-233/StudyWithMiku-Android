@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.chorusfruit233.studywithmiku

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable fun SettingsSheet(prefs: Preferences,store: MikuStore,engine: FocusEngine,onDismiss: () -> Unit,onAbout: () -> Unit,
    ignoresBatteryOptimizations: Boolean,onRequestBackgroundPermission: () -> Unit,onOpenSystemSettings: () -> Unit) {
    val clock by engine.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun update(p: Preferences,resetClock: Boolean = false) {
        if (p == prefs) return
        store.updatePreferences(p)
        if (resetClock && !clock.running) engine.reset()
    }
    ModalBottomSheet(onDismissRequest = onDismiss,sheetState = sheet,containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("让这里，更像你的角落。",style = MaterialTheme.typography.headlineMedium)
            Text("按自己的节奏，调整一点点。",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingSection("专注节奏") {
                if (clock.running) Text("暂停计时后，可以调整本轮时长。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.primary)
                DurationField("专注时长",prefs.focusSeconds,100,!clock.running) { update(prefs.copy(focusSeconds = it,focusMinutes = (it+59)/60),true) }
                DurationField("短休息",prefs.breakSeconds,100,!clock.running) { update(prefs.copy(breakSeconds = it,breakMinutes = (it+59)/60),true) }
                DurationField("长休息",prefs.longBreakSeconds,200,!clock.running) { update(prefs.copy(longBreakSeconds = it,longBreakMinutes = (it+59)/60),true) }
                DurationField("倒计时时长",prefs.countdownSeconds,600,!clock.running) { update(prefs.copy(countdownSeconds = it,countdownMinutes = (it+59)/60),true) }
                SettingSlider("长休息间隔",prefs.pomodoroCount,1..20,"轮",!clock.running) { update(prefs.copy(pomodoroCount = it),true) }
                SettingSwitch("自动进入下一阶段","番茄钟连续专注和休息",prefs.autoAdvance) { update(prefs.copy(autoAdvance = it)) }
                SettingSlider("每日专注目标",prefs.dailyGoalMinutes,15..600,"分钟",true) { update(prefs.copy(dailyGoalMinutes = it)) }
            }
            SettingSection("声音与播放") {
                SettingSwitch("Miku 完成语音","计时结束时，听到一声鼓励",prefs.completionSound) { update(prefs.copy(completionSound = it)) }
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Text("完成语音音量",Modifier.weight(1f),style = MaterialTheme.typography.bodyMedium)
                    Text("${(prefs.notificationVolume*100).roundToInt()}%",style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary)
                }
                Slider(prefs.notificationVolume,{ update(prefs.copy(notificationVolume = it)) },enabled = prefs.completionSound)
                SettingSwitch("休息时暂停音乐","淡出后暂停，专注开始时恢复",prefs.pauseMusicDuringBreak) { update(prefs.copy(pauseMusicDuringBreak = it)) }
                SettingSwitch("音乐交叉淡入淡出","提前缓冲下一首，平滑衔接",prefs.crossfade) { update(prefs.copy(crossfade = it)) }
            }
            SettingSection("外观与陪伴") {
                Text("主题",style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(7.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("跟随系统","浅色","深色").forEachIndexed { i,title ->
                        FilterChip(selected = prefs.theme == i,onClick = { update(prefs.copy(theme = i)) },label = { Text(title) },shape = CircleShape)
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) SettingSwitch("壁纸取色","使用系统壁纸的颜色",prefs.dynamicColor) { update(prefs.copy(dynamicColor = it)) }
                SettingSwitch("动态陪伴场景","关闭后显示静态画面，减少耗电",prefs.animateScene) { update(prefs.copy(animateScene = it)) }
                SettingSwitch("显示一言","点击语录换一句，每五分钟自动更新",prefs.showHitokoto) { update(prefs.copy(showHitokoto = it)) }
                SettingSwitch("空闲时隐藏沉浸计时器","点击场景显示控制按钮",prefs.hidePomodoroOnIdle) { update(prefs.copy(hidePomodoroOnIdle = it)) }
                Text("场景遮罩",style = MaterialTheme.typography.bodyMedium)
                Slider(prefs.overlayOpacity,{ update(prefs.copy(overlayOpacity = it)) },valueRange = 0f..0.8f)
                SettingSwitch("保持屏幕常亮","应用打开时，让 Miku 留在屏幕上",prefs.keepScreenOn) { update(prefs.copy(keepScreenOn = it)) }
            }
            BackgroundSettingsSection(ignoresBatteryOptimizations,onRequestBackgroundPermission,onOpenSystemSettings)
            Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(18.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.CloudSync,null,Modifier.size(30.dp),tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("音乐在线，专注在当下。",style = MaterialTheme.typography.titleMedium)
                        Text("使用原站账号可同步学习记录与设置。\n在社区页登录、管理云端数据。",style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Surface(onClick = onAbout,shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Info,null,tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("关于 Study With Miku",style = MaterialTheme.typography.titleMedium)
                        Text("版本、原作、更新与开源许可",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
    }
}

@Composable internal fun BackgroundSettingsSection(ignoresBatteryOptimizations: Boolean,onRequestPermission: () -> Unit,onOpenSettings: () -> Unit) {
    SettingSection("后台运行") {
        Text("后台计时",style = MaterialTheme.typography.titleMedium)
        Text("计时开始后自动保持 CPU 运行，暂停或结束后释放。首次开始计时会自动申请后台运行权限，也可在这里重新获取。",
            style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (ignoresBatteryOptimizations) "已允许忽略系统电池优化" else "尚未允许忽略系统电池优化",
            style = MaterialTheme.typography.bodyMedium,color = if (ignoresBatteryOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onRequestPermission,modifier = Modifier.fillMaxWidth()) { Text("获取后台运行权限") }
        HorizontalDivider()
        Text("请在系统设置的“电池 / 后台耗电管理”中，将 Study With Miku 设为“无限制”。部分手机称“不限制”或“允许后台运行”。",
            style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenSettings,modifier = Modifier.fillMaxWidth()) { Text("打开应用系统设置") }
    }
}

@Composable internal fun BatteryGuidanceDialog(onDismiss: () -> Unit,onOpenSettings: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,icon = { Icon(Icons.Rounded.BatteryChargingFull,null) },title = { Text("允许后台稳定计时") },
        text = { Text("请在系统设置中打开 Study With Miku 的应用信息，进入“电池 / 后台耗电管理”，选择“无限制”（部分手机称“不限制”或“允许后台运行”）。\n\n这样锁屏或切到后台后，计时和完成提醒更稳定。稍后也可从应用设置进入。") },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("打开系统设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } })
}

@Composable private fun SettingSection(title: String,content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title,style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary,modifier = Modifier.padding(start = 4.dp,top = 6.dp))
        Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp),verticalArrangement = Arrangement.spacedBy(12.dp),content = content)
        }
    }
}
@Composable private fun SettingSlider(label: String,value: Int,range: IntRange,unit: String,enabled: Boolean,onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style = MaterialTheme.typography.bodyMedium)
            Text("${draft.roundToInt()} $unit",style = MaterialTheme.typography.labelLarge,color = MaterialTheme.colorScheme.primary)
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

@Composable private fun DurationField(label: String,seconds: Int,maxMinutes: Int,enabled: Boolean,onSave: (Int)->Unit) {
    var text by remember(seconds) {
        mutableStateOf("%.2f".format(java.util.Locale.US,seconds/60.0).removeSuffix("0"))
    }
    val minutes = text.toDoubleOrNull()
    val valid = minutes != null && minutes.isFinite() && minutes in 0.1..maxMinutes.toDouble()
    val draftSeconds = if (valid) (minutes!!*60).roundToInt() else null
    val canSave = enabled && draftSeconds != null && draftSeconds != seconds
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || fontScale > 1.3f) {
            Column(Modifier.fillMaxWidth()) {
                DurationInput(text,{ text = it.take(8) },label,maxMinutes,enabled,valid,Modifier.fillMaxWidth())
                TextButton(onClick = { draftSeconds?.let(onSave) },enabled = canSave,modifier = Modifier.align(Alignment.End)) { Text("保存") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationInput(text,{ text = it.take(8) },label,maxMinutes,enabled,valid,Modifier.weight(1f))
                TextButton(onClick = { draftSeconds?.let(onSave) },enabled = canSave) { Text("保存") }
            }
        }
    }
}

@Composable private fun DurationInput(text: String,onTextChange: (String) -> Unit,label: String,maxMinutes: Int,enabled: Boolean,valid: Boolean,modifier: Modifier) {
    OutlinedTextField(text,onTextChange,modifier,label = { Text("$label（分钟）") },
        singleLine = true,enabled = enabled,isError = !valid,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        supportingText = { Text("0.1–$maxMinutes 分钟，支持小数") })
}
