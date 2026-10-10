package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Click the actual dialog button with synthetic cloud data and real Android persistence. */
class CloudChoiceUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun useCloudButtonImportsLatestDataAndRecreatedSessionDoesNotPrompt() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cloud_choice_ui_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        val reads = AtomicInteger()
        val writes = AtomicInteger()
        var cloud = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":600,"totalPomodoros":1},"todos":[{"id":10,"text":"Old cloud task"}],"settings":{"focusDuration":40}}"""))
        val api = object : StudySyncApi {
            override fun fetch(token: String): CloudStudy { reads.incrementAndGet(); return cloud }
            override fun push(token: String,study: CloudStudy) { writes.incrementAndGet() }
        }
        val account = object : StudySyncAccount {
            override val state = MutableStateFlow(AccountState("fixture","Miku",Long.MAX_VALUE))
            override fun token() = "offline-fixture"
            override fun expire() {}
            override fun logout() {}
        }
        val timer = object : StudySyncTimer {
            override val state = MutableStateFlow(FocusClock())
            override fun pause() {}
            override fun flush() {}
            override fun restoreFromStore() {}
        }
        var controller: CloudSync? = null
        try {
            var store = MikuStore(context).apply { autoSync = false; addTodo("Local task") }
            var generation by mutableIntStateOf(0)
            compose.setContent {
                key(generation) {
                    val sync = remember { CloudSync(store,timer,account,api).also { controller = it } }
                    DisposableEffect(sync) { sync.start(); onDispose { sync.close() } }
                    val state by sync.state.collectAsState()
                    MikuTheme(Preferences(animateScene = false)) {
                        state.message?.let { Text(it) }
                        SyncChoiceDialog(sync)
                    }
                }
            }
            compose.waitUntil(15_000) { controller?.state?.value?.conflict != null }
            compose.onNodeWithText("确认选择").assertIsDisplayed()
            saveDesignScreenshot("sync-choice")
            // The server changed after the original choice dialog was opened.
            cloud = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":1200,"totalPomodoros":2},"todos":[{"id":10,"text":"Latest cloud task","completed":true}],"settings":{"focusDuration":45}}"""))
            compose.onNodeWithText("使用云端资料").performScrollTo().performClick()
            compose.runOnIdle {
                assertFalse(controller!!.state.value.ready)
                assertEquals("Local task",store.todos.value.single().text)
                assertEquals(1,reads.get()); assertEquals(0,writes.get())
            }
            compose.onNodeWithText("确认选择").performClick()
            compose.waitUntil(15_000) { controller?.state?.value?.ready == true }
            compose.onNodeWithText("学习资料已保存到本机").assertIsDisplayed()
            compose.onNodeWithText("选择学习资料的同步方式").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1_200_000L,store.studyTotals().millis)
                assertEquals("Latest cloud task",store.todos.value.single().text)
                assertTrue(store.todos.value.single().done)
                assertEquals(2700,store.prefs.value.focusSeconds)
                assertEquals(2,reads.get()); assertEquals(0,writes.get())
                store = MikuStore(context)
                generation++
            }
            compose.waitUntil(15_000) { reads.get() == 3 && controller?.state?.value?.ready == true }
            compose.onNodeWithText("选择学习资料的同步方式").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1_200_000L,store.studyTotals().millis)
                assertNull(controller!!.state.value.conflict)
                assertEquals(0,writes.get())
            }
        } finally {
            compose.runOnIdle { controller?.close() }
            target.deleteSharedPreferences(name)
        }
    }
}
