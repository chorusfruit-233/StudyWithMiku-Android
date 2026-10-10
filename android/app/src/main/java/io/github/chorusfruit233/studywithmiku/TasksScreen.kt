@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.chorusfruit233.studywithmiku

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun TasksScreen(todos: List<Todo>,store: MikuStore,onShare: (String)->Unit) {
    var selectedKey by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var global by rememberSaveable { mutableStateOf(true) }
    val countdowns by store.countdowns.collectAsStateWithLifecycle()
    var addDay by remember { mutableStateOf(false) }
    val selected = LocalDate.parse(selectedKey)
    var calendar by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Todo?>(null) }
    var deletingTodo by remember { mutableStateOf<Todo?>(null) }
    var deletingCountdown by remember { mutableStateOf<CountdownDay?>(null) }
    var taskError by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf("#39c5bb") }
    var text by rememberSaveable { mutableStateOf("") }
    val tasks = todos.filter { it.date == if (global) "" else selectedKey }.sortedWith(compareBy<Todo> { it.done }.thenByDescending { it.pinned })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading("ONE LITTLE STEP AT A TIME","一件一件，\n慢慢来就好。","把想做的事写下来，给今天一点方向。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(global,{ global = true },label = { Text("待办事项") })
            FilterChip(!global,{ global = false },label = { Text("日历计划") })
        }
        if (!global) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.year} 年 ${selected.monthValue} 月 ${selected.dayOfMonth} 日",style = MaterialTheme.typography.titleLarge,modifier = Modifier.weight(1f))
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
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(if (global) "待办事项" else "当天计划",style = MaterialTheme.typography.titleMedium,modifier = Modifier.weight(1f))
            Text("${tasks.count { it.done }} / ${tasks.size} 已完成",style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tasks.isNotEmpty()) LinearProgressIndicator(progress = { tasks.count { it.done }.toFloat()/tasks.size },modifier = Modifier.fillMaxWidth().height(8.dp))
        if (tasks.isEmpty()) EmptyState(if (global) "还没有待办事项。" else "这一天还没有计划。","添加一个小目标，Miku 陪你一起完成。",Icons.Rounded.EditNote)
        else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            tasks.forEachIndexed { i,todo ->
                Surface(shape = listItemShape(i,tasks.size),color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp,end = 8.dp,top = 9.dp,bottom = 9.dp),verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(todo.done,{ store.toggleTodo(todo.id) })
                        Text(todo.text,Modifier.weight(1f).clickable { editing = todo; text = todo.text; color = todo.color; taskError = null; add = true },style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (global) IconButton(onClick = { store.pinTodo(todo.id) }) { Icon(Icons.Rounded.PushPin,if (todo.pinned) "取消置顶" else "置顶到专注页",tint = if (todo.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { deletingTodo = todo }) { Icon(Icons.Rounded.DeleteOutline,if (global) "删除待办" else "删除计划",Modifier.size(19.dp)) }
                    }
                }
            }
        }
        FilledTonalButton(onClick = { editing = null; text = ""; color = "#39c5bb"; taskError = null; add = true },enabled = tasks.size < 200,modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Rounded.Add,null); Spacer(Modifier.width(8.dp)); Text("添加一个小目标")
        }
        if (tasks.size >= 200) Text(if (global) "待办事项已达到 200 项上限，请先删除一些项目。" else "当天计划已达到 200 项上限，请先删除一些项目。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("完成多少都没关系，开始就已经很棒了。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text("倒数日",Modifier.weight(1f),style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { addDay = true },enabled = countdowns.size < 20) { Text("添加 · ${countdowns.size}/20") }
        }
        countdowns.forEach { day ->
            Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(day.title,style = MaterialTheme.typography.titleMedium); Text("${day.date} · ${countdownLabel(day.date)}",style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { onShare(StudyProtocol.countdownShare(day)) }) { Icon(Icons.Rounded.Share,"分享倒数日") }
                    IconButton(onClick = { deletingCountdown = day }) { Icon(Icons.Rounded.DeleteOutline,"删除倒数日") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendar = false },confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { selectedKey = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; calendar = false }) { Text("确定") }
        },dismissButton = { TextButton(onClick = { calendar = false }) { Text("取消") } }) { DatePicker(picker) }
    }
    if (add) AlertDialog(onDismissRequest = { add = false },icon = { Icon(Icons.Rounded.EditNote,null) },title = { Text(if (editing == null) "添加小目标" else "编辑小目标") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(value = text,onValueChange = { if (it.length <= 200) text = it },label = { Text(if (global) "想完成什么？" else "想在这一天完成什么？") },
            supportingText = { Text(if (global) "${text.length}/200" else "${selected.year} 年 ${selected.monthValue} 月 ${selected.dayOfMonth} 日 · ${text.length}/200") },minLines = 2,maxLines = 4)
            if (editing == null && tasks.size >= 200) Text("已达到 200 项上限，请先删除一些项目。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error)
            taskError?.let { Text(it,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error) }
            if (!global) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#39c5bb","#f06292","#ffb74d","#7986cb").forEach { value ->
                    FilterChip(color == value,{ color = value },label = { Text("●",color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value))) })
                }
            }
        } },
        confirmButton = { TextButton(onClick = {
            val todo = editing
            var saved = true
            if (todo == null) {
                val id = store.addTodo(text,if (global) null else selected)
                if (id == null) {
                    taskError = "未能添加目标，请检查内容和 200 项数量上限。"
                    saved = false
                } else if (!global) store.editTodo(id,text,color)
            } else store.editTodo(todo.id,text,color)
            if (saved) { text = ""; editing = null; taskError = null; add = false }
        },enabled = text.isNotBlank() && (editing != null || tasks.size < 200)) { Text(if (editing == null) "添加" else "保存") } },
        dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } })
    if (addDay) CountdownDialog(onDismiss = { addDay = false }) { title,date -> store.addCountdown(title,date); addDay = false }
    deletingTodo?.let { todo ->
        AlertDialog(onDismissRequest = { deletingTodo = null },title = { Text(if (todo.date.isEmpty()) "删除这项待办？" else "删除这项计划？") },
            text = { Text("“${todo.text}”将被删除，已记录的专注时间会保留。",Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { store.deleteTodo(todo.id); deletingTodo = null }) { Text("删除",color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingTodo = null }) { Text("取消") } })
    }
    deletingCountdown?.let { day ->
        AlertDialog(onDismissRequest = { deletingCountdown = null },title = { Text("删除这个倒数日？") },
            text = { Text("“${day.title}”（${day.date}）将被删除。") },
            confirmButton = { TextButton(onClick = { store.deleteCountdown(day.id); deletingCountdown = null }) { Text("删除",color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingCountdown = null }) { Text("取消") } })
    }
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

@Composable private fun CountdownDialog(onDismiss: ()->Unit,onSave: (String,String)->Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var pickerOpen by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,title = { Text("添加倒数日") },text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{ title = it.take(40) },label = { Text("标题") },singleLine = true)
            OutlinedButton(onClick = { pickerOpen = true }) { Icon(Icons.Rounded.CalendarMonth,null); Text(date) }
        }
    },confirmButton = { TextButton(onClick = { onSave(title,date) },enabled = title.isNotBlank()) { Text("保存") } },dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    if (pickerOpen) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickerOpen = false },confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; pickerOpen = false
        }) { Text("确定") } },dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("取消") } }) { DatePicker(picker) }
    }
}
