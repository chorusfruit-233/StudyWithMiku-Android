package io.github.chorusfruit233.studywithmiku

import android.content.pm.ActivityInfo
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicModuleUiTest {
    @get:Rule val compose = createAndroidComposeRule<LayoutTestActivity>()

    @Test fun settingsDisableMusicFromItsPageAndBothNavigationLayoutsThenAllowReenabling() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MikuApplication
        val previous = app.store.prefs.value
        val playlist = app.onlineMusic.currentPlaylist()
        try {
            compose.runOnIdle {
                app.store.updatePreferences(previous.copy(musicEnabled = true,animateScene = false))
                app.onlineMusic.openOffline(OfflinePlaylist(playlist.config,app.store.library.value.songs.filter { it.builtin }.take(1)))
            }
            compose.setContent { MikuTheme(app.store.prefs.collectAsState().value) { MikuApp(app,BackgroundAccess(compose.activity)) } }
            compose.onNodeWithTag("destination-1").performClick()
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithContentDescription("音乐模块").assertIsOn().performClick().assertIsOff()
            saveDesignScreenshot("music-module-disabled")
            pressBack()
            compose.onNodeWithTag("destination-1").assertDoesNotExist()
            compose.onNodeWithTag("destination-0").assertIsSelected()
            for (id in listOf(2,3,4)) compose.onNodeWithTag("destination-$id").assertIsDisplayed()
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("navigation-rail").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("destination-1").assertDoesNotExist()
            compose.onNodeWithTag("destination-3").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithContentDescription("音乐模块").performScrollTo().assertIsOff().performClick()
            pressBack()
            compose.onNodeWithTag("destination-1").assertIsDisplayed()
            compose.onNodeWithTag("destination-3").assertIsSelected()
        } finally {
            compose.runOnIdle {
                app.store.updatePreferences(previous.copy(musicEnabled = true))
                app.onlineMusic.openOffline(playlist)
                app.store.updatePreferences(previous)
            }
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test fun sharedSongsRemainReadableWithoutPlaybackOrCoverRequestsWhenDisabled() {
        val song = Song("shared","Shared fixture","Miku","https://example.test/audio",platform = "netease",playlistId = "42",playlistIndex = 0)
        val message = ChatMessage("share","fixture","Miku",StudyProtocol.songShare(song)!!,"2026-11-08T00:00:00Z")
        var played = 0
        var enabled by mutableStateOf(false)
        compose.setContent { MikuTheme(Preferences()) {
            ChatBubble(message,null,false,false,DEFAULT_MUSIC_API,musicEnabled = enabled,onSong = { played++ })
        } }
        compose.onNodeWithText("音乐模块已关闭").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放分享的歌曲").assertDoesNotExist()
        compose.onNodeWithText("Shared fixture").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0,played); enabled = true }
        compose.onNodeWithContentDescription("播放分享的歌曲").assertIsDisplayed()
        compose.onNodeWithText("Shared fixture").performClick()
        compose.runOnIdle { assertEquals(1,played) }
    }
}
