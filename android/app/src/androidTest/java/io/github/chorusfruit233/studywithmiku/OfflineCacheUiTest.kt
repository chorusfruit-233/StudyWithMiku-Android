package io.github.chorusfruit233.studywithmiku

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OfflineCacheUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun partialPlaylistPlaysOnlyCompleteSongsAndClearRequiresConfirmation() {
        val playlist = OfflinePlaylist(MusicConfig(name = "测试离线歌单"),listOf(
            Song("one","Complete","Fixture","https://example.test/one",platform = "netease"),
            Song("two","Partial","Fixture","https://example.test/two",platform = "netease")))
        var state by mutableStateOf(AudioCacheState(mapOf("one" to CachedAudio(AudioCacheStatus.COMPLETE,100,100f)),
            listOf(playlist),100,initialized = true))
        var downloads = 0; var resumed = 0; var cleared = 0; var played = emptyList<Song>()
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    OfflineCachePanel(state,playlist,false,{ downloads++ },{}, { resumed++ },{ cleared++ }, { played = state.readySongs(it) })
                }
            }
        }
        compose.onNodeWithText("补全当前歌单缓存").performClick()
        compose.runOnIdle { state = state.copy(songs = state.songs + ("two" to CachedAudio(AudioCacheStatus.PAUSED,50,50f)),bytes = 150,paused = true) }
        compose.onNodeWithText("补全当前歌单缓存").assertDoesNotExist()
        compose.onNodeWithText("继续全部缓存").performClick()
        compose.onNodeWithText("离线播放").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,downloads); assertEquals(1,resumed); assertEquals(listOf("one"),played.map { it.id }) }
        saveDesignScreenshot("offline-playlist-cache")
        compose.onNodeWithText("清除全部缓存").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0,cleared) }
        compose.onNodeWithText("清除全部缓存").performClick()
        compose.onNodeWithText("清除缓存",substring = false).performClick()
        compose.runOnIdle { assertEquals(1,cleared); state = state.copy(songs = emptyMap(),bytes = 0,playlists = emptyList()) }
        compose.onNodeWithText("离线播放").assertDoesNotExist()
    }
}
