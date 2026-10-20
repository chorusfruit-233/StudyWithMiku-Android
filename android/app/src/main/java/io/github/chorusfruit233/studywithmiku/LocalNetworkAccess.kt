package io.github.chorusfruit233.studywithmiku

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Android 17 requires a separate runtime grant for user-configured LAN music sources. */
@Composable internal fun LocalNetworkAccessSetting() {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
    fun allowed() = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = allowed() }
    Text(if (granted) "已允许访问局域网音乐源" else "使用局域网音乐源时，需要允许访问局域网。",
        style = MaterialTheme.typography.bodySmall)
    if (!granted) TextButton(onClick = { launcher.launch(permission) }) { Text("允许局域网访问") }
}
