package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PartialPreferencesTest {
    private fun fixture(test: (Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "partial_settings_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try { test(context) } finally { target.deleteSharedPreferences(name) }
    }
    @Test fun partialSavedPreferencesFillDefaultsAndKeepMusicQueueIndependent() = fixture { context ->
        context.getSharedPreferences("fixture",0).edit().putString("preferences","""{"focus":50,"focusSeconds":3000,"scene":2}""").commit()
        val store = MikuStore(context)
        assertEquals(5,store.prefs.value.breakMinutes); assertEquals(4,store.prefs.value.pomodoroCount)
        assertTrue(store.prefs.value.pauseMusicDuringBreak); assertEquals(.6f,store.prefs.value.notificationVolume,.001f)
        store.saveQueue(listOf("one","two"),1,3000,2,true,.27f)
        store.updatePreferences(store.prefs.value.copy(pauseMusicDuringBreak = false))
        val reopened = MikuStore(context)
        assertEquals(3000,reopened.prefs.value.focusSeconds); assertEquals(2,reopened.prefs.value.scene)
        assertFalse(reopened.prefs.value.pauseMusicDuringBreak)
        assertEquals(1,reopened.readQueue().getInt("index")); assertEquals(.27,reopened.readQueue().getDouble("volume"),.001)
        assertEquals(listOf("one","two"),reopened.readQueue().getJSONArray("ids").strings())
    }
    @Test fun malformedRootPreferencesFallBackToCompleteDefaults() = fixture { context ->
        context.getSharedPreferences("fixture",0).edit().putString("preferences","[]").commit()
        val p = MikuStore(context).prefs.value
        assertEquals(25,p.focusMinutes); assertEquals(5,p.breakMinutes); assertEquals(0,p.scene)
        assertTrue(p.pauseMusicDuringBreak); assertTrue(p.crossfade)
    }
}
