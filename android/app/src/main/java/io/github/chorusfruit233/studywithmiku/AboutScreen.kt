@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.chorusfruit233.studywithmiku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The single destination for app information, original credits, updates and licenses. */
@Composable fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var updatesExpanded by rememberSaveable { mutableStateOf(false) }
    var licenseExpanded by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val updates = remember { JSONArray(context.assets.open("updates.json").bufferedReader().use { it.readText() }).objects() }
    val since = remember { ChronoUnit.DAYS.between(LocalDate.of(2025,12,2),LocalDate.now()).coerceAtLeast(0) }
    fun open(url: String) {
        linkError = null
        runCatching { uri.openUri(url) }.onFailure { linkError = "无法打开链接，请检查浏览器设置。" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss,sheetState = sheet,containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding = PaddingValues(start = 24.dp,end = 24.dp,bottom = 32.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Text("关于 Study With Miku",Modifier.weight(1f),style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close,"关闭关于") }
                }
                Text("Android ${BuildConfig.VERSION_NAME} · Material 3 Expressive",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
                linkError?.let { Text(it,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error) }
            }
            item {
                AboutSection("Android 应用") {
                    Text("原生 Android 版本",style = MaterialTheme.typography.bodyMedium)
                    AboutLink("Android 仓库与版本发布") { open("https://github.com/chorusfruit-233/StudyWithMiku-Android") }
                }
            }
            item {
                AboutSection("原作与创作者") {
                    Text("Love by SHSHOUSE",style = MaterialTheme.typography.titleMedium)
                    Text("原站已陪伴大家 $since 天",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        "原项目与源码" to "https://github.com/shshouse/StudyWithMiku",
                        "作者主页" to "https://space.bilibili.com/3493127123897196",
                        "MikuMod 账号设置" to "$ACCOUNT_SITE/settings",
                        "B站项目介绍" to "https://www.bilibili.com/video/BV18TSWBzE4o",
                        "支持原作者" to "https://afdian.com/a/shshouse"
                    ).forEach { (name,url) -> AboutLink(name) { open(url) } }
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("StudyWithMiku QQ群","941108668"))
                        copied = true
                    }) {
                        Icon(Icons.Rounded.ContentCopy,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (copied) "已复制 QQ 群号 941108668" else "复制 QQ 群号 941108668")
                    }
                }
            }
            item {
                AboutDisclosure("原项目更新日志",if (updatesExpanded) "收起更新日志" else "查看内置的原作更新记录",updatesExpanded) { updatesExpanded = !updatesExpanded }
            }
            if (updatesExpanded) {
                itemsIndexed(updates,key = { index,update -> "update-$index-${update.optString("date")}" }) { _,update ->
                    AboutSection(update.optString("date")) {
                        update.optJSONArray("content")?.strings()?.forEach { Text(it,style = MaterialTheme.typography.bodyMedium) }
                        update.optJSONArray("images")?.strings()?.forEach { image ->
                            AsyncImage("$STUDY_SITE$image","${update.optString("date")} 更新配图",Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(16.dp)),contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
            item {
                AboutSection("开源许可与资源") {
                    Text("基于 shshouse / StudyWithMiku，遵循 GNU GPL v3。",style = MaterialTheme.typography.bodyMedium)
                    Text("场景、音乐、语音和贴图沿用原项目。音乐：和未来一起喝茶、Alice In Cradle、Snail’s House。原始音视频和插画来自原仓库，资源权利归各自创作者。",style = MaterialTheme.typography.bodyMedium)
                    Text("AndroidX / Jetpack Compose / Media3：Apache License 2.0。",style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { licenseExpanded = !licenseExpanded }) {
                        Text(if (licenseExpanded) "收起 GPL v3 全文" else "阅读 GPL v3 全文")
                        Spacer(Modifier.width(8.dp))
                        Icon(if (licenseExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)
                    }
                    if (licenseExpanded) {
                        val license = remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }
                        SelectionContainer { Text(license,fontSize = 12.sp,lineHeight = 18.sp) }
                    }
                }
            }
        }
    }
}

@Composable private fun AboutSection(title: String,content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title,style = MaterialTheme.typography.titleMedium,color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable private fun AboutLink(label: String,onClick: () -> Unit) {
    TextButton(onClick = onClick,modifier = Modifier.fillMaxWidth(),contentPadding = PaddingValues(0.dp)) {
        Text(label,Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.OpenInNew,null,Modifier.size(18.dp))
    }
}

@Composable private fun AboutDisclosure(title: String,subtitle: String,expanded: Boolean,onClick: () -> Unit) {
    Surface(onClick = onClick,shape = RoundedCornerShape(24.dp),color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title,style = MaterialTheme.typography.titleMedium)
                Text(subtitle,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)
        }
    }
}
