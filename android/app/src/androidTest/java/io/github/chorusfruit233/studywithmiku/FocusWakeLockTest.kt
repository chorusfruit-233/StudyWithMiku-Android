package io.github.chorusfruit233.studywithmiku

import android.Manifest
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Verify the actual service acquires CPU wake protection with no music and releases it on pause. */
class FocusWakeLockTest {
    @get:Rule val compose = createComposeRule()
    @Test fun timerWithoutMusicAutomaticallyAcquiresAndReleasesWakeLock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MikuApplication
        val before = app.store.prefs.value
        fun held() = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys power")).use { stream ->
                stream.bufferedReader().readLines().any { it.contains("PARTIAL_WAKE_LOCK") && it.contains("StudyWithMiku:Focus") }
            }
        compose.setContent { MikuTheme(Preferences(animateScene = false)) { Text("Offline focus wake test") } }
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(app,Manifest.permission.WAKE_LOCK))
        try {
            compose.runOnIdle { app.timer.reset(TimerMode.STOPWATCH); app.timer.toggle() }
            compose.waitUntil(10_000) { held() }
            compose.runOnIdle { assertTrue(app.timer.state.value.running); app.timer.pause() }
            compose.waitUntil(10_000) { !held() }
            compose.runOnIdle { assertFalse(app.timer.state.value.running) }
        } finally {
            compose.runOnIdle { app.timer.pause(); app.store.updatePreferences(before); app.timer.reset() }
        }
    }
}
