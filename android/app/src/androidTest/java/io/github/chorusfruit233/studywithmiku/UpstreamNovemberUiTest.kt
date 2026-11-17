package io.github.chorusfruit233.studywithmiku

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class UpstreamNovemberUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pendingBubbleCannotBeRepliedToAndFailedDraftDoesNotOverwriteInput() {
        val draft = ChatDraft("draft","fixture","Miku fixture","[reply:original]等待发送的正文","",1_000,failure = "未收到服务器确认")
        var input by mutableStateOf("正在编辑的内容")
        var restored = false
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                Column {
                    ChatBubble(draft.message(),null,true,false,DEFAULT_MUSIC_API,pendingStatus = "等待连接后发送…",onSong = {})
                    ChatFailedDraft(draft,input.isBlank(),onRestore = { input = draft.content; restored = true },onDiscard = {})
                    OutlinedTextField(input,{ input = it },label = { androidx.compose.material3.Text("测试输入框") })
                }
            }
        }
        compose.onNodeWithText("等待连接后发送…").assertIsDisplayed()
        compose.onNodeWithContentDescription("回复 Miku fixture").assertDoesNotExist()
        compose.onNodeWithText("恢复草稿").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("恢复草稿").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(restored); assertEquals(draft.content,input) }
        saveDesignScreenshot("chat-outbox")
    }
    @Test fun actualPreferencesPreserveUnacknowledgedMessagesAcrossRecreationAndAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "chat_outbox_fixture_${UUID.randomUUID()}"
        val disk = context.getSharedPreferences(name,Context.MODE_PRIVATE)
        fun box() = ChatOutbox({ disk.getString("entries","[]")!! },{ disk.edit().putString("entries",it).commit() })
        try {
            val first = box()
            assertTrue(first.enqueue("one","Miku","[sticker:21]",""))
            var id = ""
            first.pump("one",true,true) { id = it.clientId; true }
            val next = box()
            assertTrue(next.pending("two").isEmpty())
            assertEquals(id,next.pending("one").single().clientId)
            assertEquals(1,next.pending("one").single().attempts)
            assertNotNull(next.confirm("one",id)); assertTrue(box().pending("one").isEmpty())
        } finally { context.deleteSharedPreferences(name) }
    }
}
