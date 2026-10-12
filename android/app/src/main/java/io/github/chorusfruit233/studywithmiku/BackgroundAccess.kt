package io.github.chorusfruit233.studywithmiku

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BackgroundPrompt { REQUEST_PERMISSION, SHOW_GUIDANCE }
internal enum class BackgroundRequestResult { LAUNCHED, ALREADY_ALLOWED, UNAVAILABLE }

/** Battery exemption is special system access, separate from the normal WAKE_LOCK permission. */
internal class BackgroundAccess(private val context: Context,
    private val isExempt: () -> Boolean = {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }) {
    private val preferences = context.getSharedPreferences("background_permissions",Context.MODE_PRIVATE)
    private val _ignoresBatteryOptimizations = MutableStateFlow(isExempt())
    val ignoresBatteryOptimizations = _ignoresBatteryOptimizations.asStateFlow()

    fun refresh() { _ignoresBatteryOptimizations.value = isExempt() }

    fun nextAutomaticPrompt(): BackgroundPrompt? {
        refresh()
        if (!_ignoresBatteryOptimizations.value && !preferences.getBoolean("automatically_requested",false))
            return BackgroundPrompt.REQUEST_PERMISSION
        if (!preferences.getBoolean("guidance_shown",false)) return BackgroundPrompt.SHOW_GUIDANCE
        return null
    }

    fun markGuidanceShown() { preferences.edit().putBoolean("guidance_shown",true).apply() }

    fun requestPermission(launch: (Intent) -> Unit): BackgroundRequestResult {
        refresh()
        if (_ignoresBatteryOptimizations.value) return BackgroundRequestResult.ALREADY_ALLOWED
        // Declining, unsupported vendor screens and recreation must not repeatedly interrupt a timer.
        preferences.edit().putBoolean("automatically_requested",true).apply()
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,packageUri()),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()),
            Intent(Settings.ACTION_SETTINGS))
        return if (launchFirstSupported(intents,launch)) BackgroundRequestResult.LAUNCHED else BackgroundRequestResult.UNAVAILABLE
    }

    fun openAppSettings(): Boolean = launchFirstSupported(listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_SETTINGS))) { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun packageUri() = Uri.fromParts("package",context.packageName,null)
    private fun launchFirstSupported(intents: List<Intent>,launch: (Intent) -> Unit): Boolean {
        for (intent in intents) {
            try { launch(intent); return true }
            catch (_: ActivityNotFoundException) { /* Try the next system settings screen. */ }
            catch (_: SecurityException) { /* Some vendor ROMs disallow the direct request. */ }
        }
        return false
    }
}
