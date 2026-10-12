package io.github.chorusfruit233.studywithmiku

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BackgroundAccessTest {
    private fun withPreferences(test: (Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "background_access_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try { test(context) } finally { target.deleteSharedPreferences(name) }
    }

    @Test fun declinedRequestSurvivesRecreationAndManualRetryRemainsAvailable() = withPreferences { context ->
        var exempt = false
        var access = BackgroundAccess(context) { exempt }
        assertEquals(BackgroundPrompt.REQUEST_PERMISSION,access.nextAutomaticPrompt())
        var request: Intent? = null
        assertEquals(BackgroundRequestResult.LAUNCHED,access.requestPermission { request = it })
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,request?.action)
        assertEquals("package:${context.packageName}",request?.dataString)
        access = BackgroundAccess(context) { exempt }
        assertEquals(BackgroundPrompt.SHOW_GUIDANCE,access.nextAutomaticPrompt())
        access.markGuidanceShown()
        assertNull(BackgroundAccess(context) { exempt }.nextAutomaticPrompt())
        assertFalse(access.ignoresBatteryOptimizations.value) // Launching does not mean permission was granted.
        assertEquals(BackgroundRequestResult.LAUNCHED,access.requestPermission { request = it })
        exempt = true
        access.refresh()
        assertTrue(access.ignoresBatteryOptimizations.value)
        assertEquals(BackgroundRequestResult.ALREADY_ALLOWED,access.requestPermission { fail("Already exempt: must not launch another request") })
        exempt = false
        access.refresh()
        assertFalse(access.ignoresBatteryOptimizations.value)
        assertNull(access.nextAutomaticPrompt())
    }

    @Test fun alreadyExemptInstallShowsGuidanceOnceAndCanRequestAfterExemptionIsRevoked() = withPreferences { context ->
        var exempt = true
        val access = BackgroundAccess(context) { exempt }
        assertEquals(BackgroundPrompt.SHOW_GUIDANCE,access.nextAutomaticPrompt())
        access.markGuidanceShown()
        assertNull(BackgroundAccess(context) { exempt }.nextAutomaticPrompt())
        exempt = false
        assertEquals(BackgroundPrompt.REQUEST_PERMISSION,access.nextAutomaticPrompt())
    }

    @Test fun unsupportedVendorScreensFallBackAndNeverRepeatedlyInterruptTimer() = withPreferences { context ->
        val access = BackgroundAccess(context) { false }
        val attempted = mutableListOf<Intent>()
        assertEquals(BackgroundRequestResult.LAUNCHED,access.requestPermission {
            attempted += it
            if (attempted.size == 1) throw SecurityException("Fixture: direct request blocked")
            if (attempted.size == 2) throw ActivityNotFoundException("Fixture: no optimization list")
        })
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,attempted.last().action)
        assertEquals("package:${context.packageName}",attempted.last().dataString)
        assertEquals(BackgroundRequestResult.UNAVAILABLE,access.requestPermission { throw ActivityNotFoundException("Fixture: no settings activity") })
        access.markGuidanceShown()
        assertNull(BackgroundAccess(context) { false }.nextAutomaticPrompt())
    }
}
