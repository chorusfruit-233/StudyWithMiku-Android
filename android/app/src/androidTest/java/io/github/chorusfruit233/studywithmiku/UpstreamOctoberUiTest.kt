package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class UpstreamOctoberUiTest {
    @get:Rule val compose = createComposeRule()
    private fun message(id: String,content: String) = ChatMessage(id,"fixture-user","Miku fixture",content,"2026-10-09T12:00:00Z")

    @Test fun quotedReplyDisplaysReferenceAndBodyAndAllowsCancelingAnotherReply() {
        val original = message("original","原消息内容")
        val reply = message("reply","[reply:original]回复正文")
        var selected by mutableStateOf<ChatMessage?>(null)
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ChatBubble(reply,null,false,false,DEFAULT_MUSIC_API,referenced = original,
                        onReply = { selected = reply },onSong = {})
                    selected?.let { ChatReplyPreview(it,null,onCancel = { selected = null }) }
                }
            }
        }
        compose.onNodeWithText("原消息内容").assertIsDisplayed()
        compose.onNodeWithText("回复正文").assertIsDisplayed()
        compose.onNodeWithText("[reply:original]回复正文").assertDoesNotExist()
        compose.onNodeWithContentDescription("回复 Miku fixture").performClick()
        compose.onNodeWithContentDescription("取消回复").assertIsDisplayed()
        saveDesignScreenshot("chat-reply")
        compose.onNodeWithContentDescription("取消回复").performClick()
        compose.onNodeWithContentDescription("取消回复").assertDoesNotExist()
        compose.runOnIdle { assertNull(selected) }
    }
    @Test fun unloadedReferenceDoesNotHideReplyBodyOrSticker() {
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                Column {
                    ChatBubble(message("text","[reply:missing]仍可阅读"),null,false,false,DEFAULT_MUSIC_API,onSong = {})
                    ChatBubble(message("sticker","[reply:missing][sticker:12]"),null,false,true,DEFAULT_MUSIC_API,onSong = {})
                }
            }
        }
        compose.onNodeWithText("仍可阅读").assertIsDisplayed()
        compose.onAllNodesWithText("原消息未加载").assertCountEquals(2)
        compose.onNodeWithContentDescription("表情包 12").assertIsDisplayed()
        compose.onNodeWithText("[reply:missing][sticker:12]").assertDoesNotExist()
    }
    @Test fun deletingSavedNetworkPlaylistRequiresConfirmationWithoutLoadingIt() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "network_playlist_ui_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try {
            val store = MikuStore(context)
            store.saveNetworkPlaylist("netease","42","Saved fixture playlist")
            val online = OnlineMusic(context,store)
            compose.setContent {
                val state by online.state.collectAsState()
                MikuTheme(Preferences(animateScene = false)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        NetworkMusicPanel(state,online,onSelect = {},onMessage = {})
                    }
                }
            }
            compose.onNodeWithContentDescription("删除网络歌单 Saved fixture playlist").performScrollTo().performClick()
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { assertEquals(1,store.networkPlaylists.value.size); assertFalse(online.state.value.loading) }
            compose.onNodeWithContentDescription("删除网络歌单 Saved fixture playlist").performScrollTo().performClick()
            compose.onNodeWithText("删除").performClick()
            compose.runOnIdle {
                assertTrue(MikuStore(context).networkPlaylists.value.isEmpty())
                assertFalse(online.state.value.loading)
            }
        } finally { target.deleteSharedPreferences(name) }
    }
}
