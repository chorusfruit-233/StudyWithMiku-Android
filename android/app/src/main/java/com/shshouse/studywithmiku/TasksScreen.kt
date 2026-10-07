@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*

@Composable fun TasksScreen(todos: List<Todo>,store: MikuStore) {
    var selectedKey by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedKey)
    var calendar by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val tasks = todos.filter { it.date == selectedKey }.sortedBy { it.done }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading("ONE LITTLE STEP AT A TIME","一件一件，\n慢慢来就好。","把想做的事写下来，给今天一点方向。")
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日",style = MaterialTheme.typography.titleLarge,modifier = Modifier.weight(1f))
            TextButton(onClick = { selectedKey = LocalDate.now().toString() }) { Text("今天") }
            IconButton(onClick = { calendar = true }) { Icon(Icons.Rounded.CalendarMonth,"选择日期") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (-3L..3L).forEach { offset ->
                val day = selected.plusDays(offset)
                val chosen = offset == 0L
                Surface(onClick = { selectedKey = day.toString() },modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(vertical = 15.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(listOf("一","二","三","四","五","六","日")[day.dayOfWeek.value-1],fontSize = 11.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${day.dayOfMonth}",fontWeight = FontWeight.Bold,fontSize = 18.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text("当天计划",style = MaterialTheme.typography.titleMedium,modifier = Modifier.weight(1f))
            Text("${tasks.count { it.done }} / ${tasks.size} 已完成",style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tasks.isNotEmpty()) LinearProgressIndicator(progress = { tasks.count { it.done }.toFloat()/tasks.size },modifier = Modifier.fillMaxWidth().height(8.dp))
        if (tasks.isEmpty()) EmptyState("今天的空白，留给新的可能。","添加一个小目标，Miku 陪你一起完成。",Icons.Rounded.EditNote)
        else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            tasks.forEachIndexed { i,todo ->
                Surface(shape = listItemShape(i,tasks.size),color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp,end = 8.dp,top = 9.dp,bottom = 9.dp),verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(todo.done,{ store.toggleTodo(todo.id) })
                        Text(todo.text,Modifier.weight(1f),style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { store.deleteTodo(todo.id) }) { Icon(Icons.Rounded.Close,"删除计划",Modifier.size(19.dp)) }
                    }
                }
            }
        }
        FilledTonalButton(onClick = { add = true },modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.Add,null); Spacer(Modifier.width(8.dp)); Text("添加一个小目标")
        }
        Text("完成多少都没关系，开始就已经很棒了。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
    if (calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendar = false },confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { selectedKey = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; calendar = false }) { Text("确定") }
        },dismissButton = { TextButton(onClick = { calendar = false }) { Text("取消") } }) { DatePicker(picker) }
    }
    if (add) AlertDialog(onDismissRequest = { add = false },icon = { Icon(Icons.Rounded.EditNote,null) },title = { Text("一个小目标") },
        text = { OutlinedTextField(value = text,onValueChange = { if (it.length <= 200) text = it },label = { Text("想在这一天完成什么？") },
            supportingText = { Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日 · ${text.length}/200") },minLines = 2,maxLines = 4) },
        confirmButton = { TextButton(onClick = { store.addTodo(text,selected); text = ""; add = false },enabled = text.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } })
}

fun listItemShape(index: Int,size: Int) = RoundedCornerShape(
    topStart = if (index == 0) 24.dp else 6.dp,topEnd = if (index == 0) 24.dp else 6.dp,
    bottomStart = if (index == size-1) 24.dp else 6.dp,bottomEnd = if (index == size-1) 24.dp else 6.dp)

@Composable fun EmptyState(title: String,subtitle: String,icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(32.dp),color = MaterialTheme.colorScheme.surfaceContainerLow,modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape,color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon,null,Modifier.padding(20.dp).size(32.dp),tint = MaterialTheme.colorScheme.primary) }
            Text(title,style = MaterialTheme.typography.titleMedium)
            Text(subtitle,style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
