package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Actual window rotation, offline fixtures, and the production layouts on both device APIs. */
class LandscapeLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<LayoutTestActivity>()

    private fun rotate(landscape: Boolean) {
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        compose.waitForIdle()
    }

    @After fun restoreOrientation() { closeSoftKeyboard(); rotate(false) }

    private fun isolated(test: (Context,MikuStore) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "landscape_fixture_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try { test(context,MikuStore(context)) } finally { target.deleteSharedPreferences(name) }
    }

    private fun assertSideBySide() {
        val first = compose.onNodeWithTag("primary-pane").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("secondary-pane").fetchSemanticsNode().boundsInRoot
        assertTrue("Panels must not overlap: $first / $second",second.left >= first.right)
        assertEquals(first.top,second.top,1f)
    }

    @Test fun landscapeFocusHasVisibleTimerAndNavigationAndRotationKeepsSelection() = isolated { context,store ->
        rotate(true)
        var toggles = 0
        compose.setContent {
            val engine = remember { FocusEngine(context,store) }
            val extras = remember { OnlineExtras(context) }
            val music = remember { MusicConnection(context) }
            DisposableEffect(music) { onDispose { music.close() } }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            MikuTheme(Preferences(animateScene = false)) {
                AdaptiveAppScaffold(tab,{ tab = it },remember { SnackbarHostState() },
                    title = { Text("STUDY WITH MIKU",Modifier.weight(1f)) },actions = {
                        IconButton({}) { Icon(Icons.Rounded.ChatBubbleOutline,"打开聊天室") }
                        IconButton({}) { Icon(Icons.Rounded.Tune,"设置") }
                    },miniPlayer = { compact ->
                        MiniPlayer(Song("fixture","午后学习音乐","Miku","asset:///fixture.mp3"),
                            PlaybackState(connected = true),music,compact = compact) {}
                    }) {
                    if (tab == 0) FocusScreen(Preferences(animateScene = false),FocusClock(),emptyMap(),emptyList(),store,engine,extras) { toggles++ }
                    else Text("Selected $tab")
                }
            }
        }
        compose.onNodeWithTag("navigation-rail").assertIsDisplayed()
        compose.onNodeWithTag("navigation-bar").assertDoesNotExist()
        assertSideBySide()
        compose.onNodeWithText("开始专注").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("沉浸场景").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,toggles) }
        saveDesignScreenshot("landscape-focus")
        compose.onNodeWithTag("destination-4").assertIsDisplayed().performClick()
        rotate(false)
        compose.onNodeWithTag("navigation-bar").assertIsDisplayed()
        compose.onNodeWithText("Selected 4").assertIsDisplayed()
        rotate(true)
        compose.onNodeWithTag("destination-4").assertIsSelected()
        compose.onNodeWithText("Selected 4").assertIsDisplayed()
    }

    @Test fun playerControlsAreVisibleAndQueueSurvivesRotation() = isolated { context,store ->
        rotate(true)
        val song = Song("fixture","横屏播放测试","Miku","asset:///fixture.mp3")
        val library = Library(songs = listOf(song))
        compose.setContent {
            val music = remember { MusicConnection(context) }
            DisposableEffect(music) { onDispose { music.close() } }
            MikuTheme(Preferences(animateScene = false)) {
                PlayerSheet(library,PlaybackState(connected = true,songId = song.id,duration = 60_000,queueIds = listOf(song.id)),store,music,{}, {})
            }
        }
        assertSideBySide()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        compose.onNodeWithText("横屏播放测试").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭播放器").assertIsDisplayed()
        saveDesignScreenshot("landscape-player")
        compose.onNodeWithText("查看播放队列 · 1 首").performScrollTo().performClick()
        rotate(false)
        compose.onNodeWithText("收起播放队列 · 1 首").performScrollTo().assertIsDisplayed()
        rotate(true)
        compose.onNodeWithText("收起播放队列 · 1 首").performScrollTo().assertIsDisplayed()
    }

    @Test fun recordsShowCalendarBesideDayDetails() {
        rotate(true)
        val today = LocalDate.now().toString()
        compose.setContent { MikuTheme(Preferences()) { RecordsScreen(mapOf(today to DayLog(120_000,2)),emptyList()) } }
        assertSideBySide()
        compose.onNodeWithText("2 分钟").assertIsDisplayed()
        saveDesignScreenshot("landscape-records")
    }

    @Test fun taskEditorKeepsTextAcrossRotationAndCanSaveInLandscape() = isolated { _,store ->
        rotate(true)
        compose.setContent { MikuTheme(Preferences()) { TasksScreen(emptyList(),store,{}) } }
        compose.onNodeWithText("添加一个小目标").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("旋转后继续编辑")
        compose.onNodeWithText("添加",substring = false).assertIsDisplayed()
        closeSoftKeyboard()
        rotate(false)
        compose.onNode(hasSetTextAction()).assertTextContains("旋转后继续编辑")
        rotate(true)
        compose.onNode(hasSetTextAction()).assertTextContains("旋转后继续编辑")
        saveDesignScreenshot("landscape-task-editor")
        compose.onNodeWithText("添加",substring = false).performClick()
        compose.runOnIdle { assertEquals("旋转后继续编辑",store.todos.value.single().text) }
    }

    @Test fun chatComposerStaysAccessibleWithLandscapeKeyboard() {
        rotate(true)
        compose.setContent {
            MikuTheme(Preferences()) {
                FocusChatSheet({}) {
                    var input by rememberSaveable { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().imePadding()) {
                        LazyColumn(Modifier.weight(1f).testTag("chat-messages")) {
                            items(30) { Text("Message $it",Modifier.height(48.dp)) }
                        }
                        OutlinedTextField(input,{ input = it },Modifier.fillMaxWidth().testTag("chat-input"),singleLine = true)
                    }
                }
            }
        }
        compose.onNodeWithTag("chat-input").assertIsDisplayed().performClick().performTextInput("横屏消息")
        compose.onNodeWithTag("chat-input").assertIsDisplayed().assertTextContains("横屏消息")
        saveDesignScreenshot("landscape-chat-keyboard")
        closeSoftKeyboard()
        compose.onNodeWithTag("chat-messages").performScrollToIndex(29)
        repeat(3) { compose.onNodeWithTag("chat-messages").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("chat-input").assertIsDisplayed().assertTextContains("横屏消息")
    }

    @Test fun settingsAndCalendarRemainScrollableInLandscape() = isolated { context,store ->
        rotate(true)
        var settings by mutableStateOf(true)
        compose.setContent {
            val engine = remember { FocusEngine(context,store) }
            MikuTheme(Preferences()) {
                if (settings) SettingsSheet(Preferences(),store,engine,{}, { settings = false },false,{}, {})
                else TasksScreen(emptyList(),store,{})
            }
        }
        compose.onNodeWithText("关于 Study With Miku").performScrollTo().assertIsDisplayed()
        saveDesignScreenshot("landscape-settings")
        compose.runOnIdle { settings = false }
        compose.onNodeWithText("日历计划").performScrollTo().performClick()
        compose.onNodeWithContentDescription("选择日期").performScrollTo().performClick()
        compose.onNodeWithText("确定").assertIsDisplayed()
        saveDesignScreenshot("landscape-calendar")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithContentDescription("选择日期").assertIsDisplayed()
    }

    @Test fun syncChoiceOptionsScrollWhileConfirmationRemainsVisible() = isolated { _,store ->
        rotate(true)
        store.autoSync = false
        store.addTodo("Local fixture")
        val cloud = CloudStudy(JSONObject("""{"todos":[{"id":1,"text":"Cloud fixture"}]}"""))
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
        val api = object : StudySyncApi {
            override fun fetch(token: String) = cloud
            override fun push(token: String,study: CloudStudy) {}
        }
        lateinit var sync: CloudSync
        compose.setContent {
            sync = remember { CloudSync(store,timer,account,api) }
            DisposableEffect(sync) { sync.start(); onDispose { sync.close() } }
            MikuTheme(Preferences()) { SyncChoiceDialog(sync) }
        }
        compose.waitUntil(15_000) { sync.state.value.conflict != null }
        compose.onNodeWithText("使用云端资料").performScrollTo().performClick()
        compose.onNodeWithText("确认选择").assertIsDisplayed()
        saveDesignScreenshot("landscape-sync-choice")
        compose.onNodeWithText("确认选择").performClick()
        compose.waitUntil(15_000) { sync.state.value.ready }
        compose.runOnIdle { assertEquals("Cloud fixture",store.todos.value.single().text) }
    }

    @Test fun narrowWindowWithLargeFontStacksPanelsAndKeepsActionsReachable() {
        rotate(true)
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                MikuTheme(Preferences()) {
                    Box(Modifier.width(500.dp).fillMaxHeight()) {
                        AdaptivePaneLayout(primary = { Text("Primary",Modifier.height(240.dp)) },secondary = {
                            Button({ clicks++ }) { Text("Reachable action") }
                        })
                    }
                }
            }
        }
        val first = compose.onNodeWithTag("primary-pane").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("secondary-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left,second.left,1f)
        compose.onNodeWithText("Reachable action").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,clicks) }
    }
}
