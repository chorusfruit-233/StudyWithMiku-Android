package io.github.chorusfruit233.studywithmiku

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplyProtocolTest {
    @Test fun replyUsesOriginalTextPrefixAndPreservesBody() {
        val payload = StudyProtocol.replyMessage(" message-42 ","  你好\n第二行  ")!!
        assertEquals("[reply:message-42]你好\n第二行",payload)
        assertEquals(ChatReply("message-42","你好\n第二行"),StudyProtocol.reply(payload))
        val message = StudyProtocol.chat(JSONObject().put("id","answer").put("content",payload))!!
        assertEquals(payload,message.content)
        assertEquals("message-42",StudyProtocol.reply(message.content)!!.replyToId)
    }
    @Test fun ordinaryAndMalformedPrefixesRemainOrdinaryMessages() {
        for (body in listOf("普通消息","hello [reply:id]world","[reply:]hello","[reply:id"," [reply:id]hello"))
            assertNull(body,StudyProtocol.reply(body))
        assertEquals("[reply:]hello",StudyProtocol.replySummary("[reply:]hello"))
        assertNull(StudyProtocol.replyMessage("","hello"))
        assertNull(StudyProtocol.replyMessage("id", "  "))
        assertNull(StudyProtocol.replyMessage("broken]id","hello"))
    }
    @Test fun repliedCardsKeepTheirOriginalRenderingPayloads() {
        val sticker = StudyProtocol.reply("[reply:id][sticker:12]")!!
        assertEquals(12,StudyProtocol.stickerId(sticker.text))
        assertEquals("[表情包]",StudyProtocol.replySummary("[reply:id][sticker:12]"))
        val song = Song("song","曲目","作者","https://example.test/a.mp3",platform = "netease",playlistId = "42",playlistIndex = 0)
        val music = StudyProtocol.songShare(song)!!
        assertEquals("曲目",StudyProtocol.sharedSong(StudyProtocol.reply("[reply:id]$music")!!.text)!!.name)
        assertEquals("[音乐] 曲目",StudyProtocol.replySummary(music))
        val countdown = StudyProtocol.countdownShare(CountdownDay("id","考试","2027-01-01"))
        assertEquals("[倒计时] 考试",StudyProtocol.replySummary("[reply:id]$countdown"))
    }
    @Test fun quoteSummaryRemovesOneReplyPrefixAndLimitsLongText() {
        assertEquals("原回复",StudyProtocol.replySummary("[reply:older] 原回复 "))
        assertEquals("[空消息]",StudyProtocol.replySummary("[reply:older]"))
        val long = "未".repeat(45)
        assertEquals("未".repeat(40)+"…",StudyProtocol.replySummary(long))
        assertEquals(ChatReply("outer","[reply:inner]正文"),StudyProtocol.reply("[reply:outer][reply:inner]正文"))
    }
}
