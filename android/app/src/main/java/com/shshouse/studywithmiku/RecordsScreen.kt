package com.shshouse.studywithmiku

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable fun RecordsScreen(logs: Map<String,DayLog>,todos: List<Todo>) {
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var dayKey by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthKey)
    val selected = LocalDate.parse(dayKey)
    val monthLogs = logs.filterKeys { it.startsWith(monthKey) }.values
    val totalMs = monthLogs.sumOf { it.millis }
    val totalSessions = monthLogs.sumOf { it.sessions }
    val active = monthLogs.count { it.millis > 0 }
    val dayLog = logs[dayKey] ?: DayLog()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading("YOUR EFFORT COUNTS","努力过的日子，\n都有迹可循。","每一分钟，都是朝未来走的一小步。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("本月专注","${totalMs / 60_000}","分钟",Icons.Rounded.AutoAwesome,Modifier.weight(1f))
            StatCard("本月番茄","$totalSessions","个",Icons.Rounded.LocalFlorist,Modifier.weight(1f),false)
        }
        Surface(shape = RoundedCornerShape(32.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft,"上个月") }
                    Text("${month.year} 年 ${month.monthValue} 月",Modifier.weight(1f),style = MaterialTheme.typography.titleMedium,textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { monthKey = month.plusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronRight,"下个月") }
                }
                Row(Modifier.fillMaxWidth()) { listOf("一","二","三","四","五","六","日").forEach {
                    Text(it,Modifier.weight(1f),textAlign = androidx.compose.ui.text.style.TextAlign.Center,style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                val offset = month.atDay(1).dayOfWeek.value-1
                val cells = ((offset+month.lengthOfMonth()+6)/7)*7
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until cells/7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (0..6).forEach { col ->
                                val number = row*7+col-offset+1
                                if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).aspectRatio(1f))
                                else {
                                    val date = month.atDay(number)
                                    val millis = logs[date.toString()]?.millis ?: 0L
                                    val intensity = (millis / 7_200_000f).coerceIn(0f,1f)
                                    val chosen = date.toString() == dayKey
                                    val color = when {
                                        chosen -> MaterialTheme.colorScheme.primary
                                        millis > 0 -> androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.primary,intensity)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    }
                                    Surface(onClick = { dayKey = date.toString() },modifier = Modifier.weight(1f).aspectRatio(1f),
                                        shape = RoundedCornerShape(12.dp),color = color,
                                        border = if (date == LocalDate.now() && !chosen) BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary) else null) {
                                        Box(contentAlignment = Alignment.Center) { Text("$number",fontSize = 13.sp,fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                                            color = if (chosen || intensity > 0.6f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本月学习 $active 天",Modifier.weight(1f),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("少",style = MaterialTheme.typography.labelSmall)
                    (0..3).forEach { level -> Box(Modifier.size(10.dp).background(androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.primary,level/3f),RoundedCornerShape(3.dp))) }
                    Text("多",style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日",style = MaterialTheme.typography.titleLarge)
        Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("专注时间",style = MaterialTheme.typography.bodySmall); Text("${dayLog.millis/60_000} 分钟",style = MaterialTheme.typography.titleLarge) }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("完成番茄",style = MaterialTheme.typography.bodySmall); Text("${dayLog.sessions} 个",style = MaterialTheme.typography.titleLarge) }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("完成计划",style = MaterialTheme.typography.bodySmall); Text("${todos.count { it.date == dayKey && it.done }} 件",style = MaterialTheme.typography.titleLarge) }
            }
        }
        val plans = todos.filter { it.date == dayKey }
        if (plans.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plans.forEach { Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (it.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,null,Modifier.size(20.dp),tint = MaterialTheme.colorScheme.primary)
                Text(it.text,style = MaterialTheme.typography.bodyMedium)
            } }
        }
        if (totalMs == 0L) Text("从第一次专注开始，这里会慢慢长出你的足迹。",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
