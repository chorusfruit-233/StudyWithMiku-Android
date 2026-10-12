package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercise the production choice dialog, real native storage and the original HTTP upload contract. */
class SparseSyncUiTest {
    @get:Rule val compose = createComposeRule()
    private fun study(local: Boolean) = CloudStudy(JSONObject("""{
        "stats":{"totalStudyTime":300.125,"totalPomodoros":2},
        "todos":[{"id":10,"text":"${if (local) "Local task" else "Cloud task"}","${if (local) "localField" else "remoteField"}":"keep"}],
        "settings":{"focusDuration":6.1},"serverOnly":"read metadata"
    }"""),JSONObject(if (local) """{
        "dailyLog":{"2026-10-07":{"studyTime":60.001,"pomodoros":1,"localField":"keep"}},"plans":{},"updated_at":"metadata"
    }""" else """{
        "dailyLog":{"2026-10-08":{"studyTime":90.008,"pomodoros":1,"remoteField":"keep"}},"plans":{},"updated_at":"metadata"
    }"""))

    private fun scenario(failFirstCalendarUpload: Boolean) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sparse_sync_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        val rejectCalendar = AtomicBoolean(failFirstCalendarUpload)
        val dataWrites = AtomicInteger(); val calendarWrites = AtomicInteger()
        val malformedRequests = AtomicInteger()
        val uploadedData = AtomicReference<JSONObject>()
        val uploadedCalendar = AtomicReference<JSONObject>()
        val remote = study(false)
        var sync: CloudSync? = null
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.getHeader("Authorization") != "Bearer offline-fixture" || request.getHeader("Cookie") != null) {
                        malformedRequests.incrementAndGet(); return MockResponse().setResponseCode(500)
                    }
                    val calendar = request.path == "/api/study/calendar"
                    if (request.method == "GET") return MockResponse().setBody(
                        JSONObject().put("data",if (calendar) remote.calendar else remote.data).toString())
                    val payload = JSONObject(request.body.readUtf8())
                    val keys = if (calendar) setOf("dailyLog","plans") else setOf("stats","todos","settings")
                    if (request.method != "PUT" || request.getHeader("Content-Type") != "application/json" || payload.keys().asSequence().toSet() != keys) {
                        malformedRequests.incrementAndGet(); return MockResponse().setResponseCode(500)
                    }
                    if (calendar) {
                        calendarWrites.incrementAndGet()
                        if (rejectCalendar.get()) return MockResponse().setResponseCode(500)
                        uploadedCalendar.set(payload)
                    } else { dataWrites.incrementAndGet(); uploadedData.set(payload) }
                    return MockResponse().setResponseCode(204)
                }
            }
            server.start()
            val store = MikuStore(context).apply {
                autoSync = false; switchStudyOwner("fixture"); applyStudy(study(true))
            }
            val account = object : StudySyncAccount {
                override val state = MutableStateFlow(AccountState("fixture","Miku",Long.MAX_VALUE))
                override fun token() = "offline-fixture"
                override fun expire() { state.value = state.value.copy(expired = true) }
                override fun logout() { state.value = AccountState() }
            }
            val timer = object : StudySyncTimer {
                override val state = MutableStateFlow(FocusClock())
                override fun pause() {}
                override fun flush() {}
                override fun restoreFromStore() {}
            }
            try {
                val controller = CloudSync(store,timer,account,StudyApi(server.url("/").toString().trimEnd('/'))).also { sync = it }
                compose.setContent {
                    DisposableEffect(controller) { controller.start(); onDispose { controller.close() } }
                    val state by controller.state.collectAsState()
                    MikuTheme(Preferences(animateScene = false)) {
                        state.error?.let { Text(it) }
                        SyncChoiceDialog(controller)
                    }
                }
                compose.waitUntil(15_000) { controller.state.value.conflict != null }
                compose.onNodeWithText("合并本地与云端").performScrollTo().performClick()
                if (!failFirstCalendarUpload) saveDesignScreenshot("sync-sparse-merge")
                compose.onNodeWithText("确认选择").performClick()
                compose.waitUntil(15_000) {
                    !controller.state.value.busy && controller.state.value.ready && dataWrites.get() >= 1 && calendarWrites.get() >= 1
                }
                compose.runOnIdle {
                    assertEquals(0,malformedRequests.get())
                    assertEquals(1,dataWrites.get()); assertEquals(1,calendarWrites.get())
                    assertEquals(60_001L,store.logs.value["2026-10-07"]!!.millis)
                    assertEquals(90_008L,store.logs.value["2026-10-08"]!!.millis)
                    assertEquals(300_125L,store.studyTotals().millis)
                    assertEquals("Local task",store.todos.value.single().text)
                    assertEquals("keep",uploadedData.get()!!.getJSONArray("todos").getJSONObject(0).getString("remoteField"))
                    assertEquals("read metadata",store.studySnapshot().data.getString("serverOnly"))
                }
                if (failFirstCalendarUpload) {
                    compose.onNodeWithText("上传日历失败",substring = true).assertIsDisplayed()
                    compose.runOnIdle {
                        assertTrue(MikuStore(context).pendingStudyUpload)
                        assertTrue(account.state.value.loggedIn)
                        rejectCalendar.set(false); controller.syncNow()
                    }
                    compose.waitUntil(15_000) {
                        !controller.state.value.busy && controller.state.value.error == null && dataWrites.get() >= 2 && calendarWrites.get() >= 2
                    }
                    compose.runOnIdle { assertEquals(2,dataWrites.get()); assertEquals(2,calendarWrites.get()) }
                }
                compose.runOnIdle {
                    val reopened = MikuStore(context)
                    assertFalse(reopened.pendingStudyUpload)
                    assertEquals(300_125L,reopened.studyTotals().millis)
                    assertEquals("keep",uploadedCalendar.get()!!.getJSONObject("dailyLog").getJSONObject("2026-10-07").getString("localField"))
                    assertEquals("keep",uploadedCalendar.get()!!.getJSONObject("dailyLog").getJSONObject("2026-10-08").getString("remoteField"))
                }
            } finally {
                compose.runOnIdle { sync?.close() }
                target.deleteSharedPreferences(name)
            }
        }
    }

    @Test fun mergeOfOneSidedDatesUploadsOriginalWireFormatAndSurvivesRecreation() = scenario(false)
    @Test fun failedCalendarUploadNamesTheStepPreservesLocalDataAndAllowsRetry() = scenario(true)
}
