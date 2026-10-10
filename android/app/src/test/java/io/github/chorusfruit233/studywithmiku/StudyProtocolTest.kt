package io.github.chorusfruit233.studywithmiku

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.util.Base64

class StudyProtocolTest {
    private fun token(exp: Long = 2000,alg: String = "HS256"): String {
        fun part(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        return "${part("""{"alg":"$alg"}""")}.${part("""{"sub":"abc","username":"Miku","exp":$exp}""")}.signature"
    }
    @Test fun acceptsOnlyOfficialStudyCallbackOriginAndRoot() {
        assertEquals("a.b.c",StudyProtocol.callbackToken("https://study.mikumod.com/#study_token=a.b.c"))
        assertEquals("a.b.c",StudyProtocol.callbackToken("https://study.mikumod.com:443#study_token=a.b.c"))
        for (url in listOf("http://study.mikumod.com/#study_token=x","https://study.mikumod.com.evil.test/#study_token=x",
            "https://study.mikumod.com@evil.test/#study_token=x","https://evil@study.mikumod.com/#study_token=x",
            "https://study.mikumod.com:8443/#study_token=x","https://study.mikumod.com/other#study_token=x",
            "https://study.mikumod.com/?study_token=x","file:///study_token=x")) assertNull(url,StudyProtocol.callbackToken(url))
    }
    @Test fun readsDisplayClaimsButRejectsExpiredOrUnsecuredCredentials() {
        assertEquals(TokenClaims("abc","Miku",2000),StudyProtocol.tokenClaims(token(),1000))
        assertThrows(IllegalArgumentException::class.java) { StudyProtocol.tokenClaims(token(),2000) }
        assertThrows(IllegalArgumentException::class.java) { StudyProtocol.tokenClaims(token(alg = "none"),1000) }
        assertThrows(IllegalArgumentException::class.java) { StudyProtocol.tokenClaims("bad",1000) }
    }
    @Test fun chatHistoryDeduplicatesAndOrdersByTimestampThenId() {
        val a = ChatMessage("a","u","name","old","2026-10-07T12:00:00.000Z")
        val b = a.copy(id = "b",createdAt = "2026-10-07T11:59:59.000Z")
        val c = a.copy(id = "c")
        val merged = StudyProtocol.mergeMessages(listOf(c,a),listOf(b,a.copy(content = "updated")))
        assertEquals(listOf("b","a","c"),merged.map { it.id })
        assertEquals("updated",merged[1].content)
        assertEquals(1791374399000L,b.timestamp)
        assertNull(StudyProtocol.chat(JSONObject("""{"content":"missing id"}""")))
    }
    @Test fun stickersPreserveLegacyRenderingAndExcludeRetiredStickerFromSending() {
        for (id in 1..16) assertEquals(id,StudyProtocol.stickerId("[sticker:$id]"))
        assertEquals("",StudyProtocol.stickerMessage(10))
        assertEquals("[sticker:12]",StudyProtocol.stickerMessage(12))
        assertTrue(StudyProtocol.stickerUrl(12).endsWith("12.gif"))
        assertNull(StudyProtocol.stickerId("[sticker:17]"))
        assertNull(StudyProtocol.stickerId("hello [sticker:1]"))
    }
    @Test fun musicSharesRoundTripOriginalWireContract() {
        val song = Song("id","名称","作者","https://music.test/1.mp3",platform = "netease",playlistId = "123",playlistIndex = 83,
            playlistName = "歌单",publicCover = "https://api.test/?type=pic&id=456")
        val card = StudyProtocol.sharedSong(StudyProtocol.songShare(song)!!)!!
        assertEquals(SharedSong("netease","123",83,"名称","456","歌单"),card)
        assertNull(StudyProtocol.songShare(song.copy(playlistId = "")))
        assertNull(StudyProtocol.songShare(song.copy(platform = "other")))
        assertNull(StudyProtocol.sharedSong("[music:{\"p\":\"netease\",\"pid\":\"123\",\"i\":-1}]"))
        assertNull(StudyProtocol.sharedSong("[music:{\"p\":\"netease\",\"pid\":\"123\",\"i\":1.5}]"))
        assertNull(StudyProtocol.sharedSong("[music:bad]"))
    }
    @Test fun songCoversAreLimitedToOriginalImageProviders() {
        val card = SharedSong("netease","123",0,"name","","list")
        assertEquals("https://study.mikumod.com/music/cover.png",StudyProtocol.sharedCover(card.copy(cover = "/music/cover.png"),"https://api.test/"))
        assertEquals("https://api.test/?server=netease&type=pic&id=123",StudyProtocol.sharedCover(card.copy(cover = "123"),"https://api.test/"))
        assertEquals("",StudyProtocol.sharedCover(card.copy(cover = "https://evil.test/cover.png"),"https://api.test/"))
        assertEquals("",StudyProtocol.sharedCover(card.copy(cover = "https://user:pass@p1.music.126.net/cover.png"),"https://api.test/"))
        assertEquals("https://p1.music.126.net/cover.jpg",StudyProtocol.sharedCover(card.copy(cover = "https://p1.music.126.net/cover.jpg"),"https://api.test/"))
    }
    @Test fun countdownSharesValidateActualCalendarDates() {
        val day = CountdownDay("123","期末考试","2027-01-15")
        assertEquals(day.copy(id = "shared"),StudyProtocol.countdown(StudyProtocol.countdownShare(day)))
        assertNull(StudyProtocol.countdown("[countdown:{\"t\":\"标题\",\"d\":\"2026-02-30\"}]"))
        assertFalse(StudyProtocol.validDate("2026-1-01"))
        assertEquals("Wed Oct 07 2026",StudyProtocol.jsDate(LocalDate.of(2026,10,7)))
    }
    private fun study(time: Int,sessions: Int,todo: String,planDone: Boolean) = CloudStudy(
        JSONObject().put("stats",JSONObject().put("totalStudyTime",time).put("totalPomodoros",sessions)
            .put("todayStudyTime",time).put("todayPomodoros",sessions).put("lastDate",StudyProtocol.jsDate(LocalDate.now())))
            .put("todos",JSONArray().put(JSONObject().put("id",123L).put("text",todo).put("completed",planDone)))
            .put("settings",JSONObject().put("focusDuration",0.1).put("countdowns",JSONArray().put(JSONObject().put("id",time).put("title",todo).put("date","2027-01-01")))),
        JSONObject().put("dailyLog",JSONObject().put("2026-10-07",JSONObject().put("studyTime",time).put("pomodoros",sessions)))
            .put("plans",JSONObject().put("2026-10-07",JSONArray().put(JSONObject().put("id",42).put("text",todo).put("done",planDone)))))
    @Test fun mergesByIdAndDailyMaximumWithoutDoubleCounting() {
        val local = study(60,1,"local",true)
        val remote = study(90,2,"remote",false)
        val merged = StudyProtocol.merged(local,remote)
        assertEquals(90.0,merged.data.getJSONObject("stats").getDouble("totalStudyTime"),0.0)
        assertEquals(90.0,merged.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-07").getDouble("studyTime"),0.0)
        assertEquals(2,merged.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-07").getInt("pomodoros"))
        assertEquals(1,merged.data.getJSONArray("todos").length())
        assertEquals("local",merged.data.getJSONArray("todos").getJSONObject(0).getString("text"))
        assertTrue(merged.calendar.getJSONObject("plans").getJSONArray("2026-10-07").getJSONObject(0).getBoolean("done"))
        assertEquals(2,merged.data.getJSONObject("settings").getJSONArray("countdowns").length())
        assertEquals("remote",remote.data.getJSONArray("todos").getJSONObject(0).getString("text"))
        assertEquals(123L,StudyProtocol.wireId("123"))
        assertEquals("uuid",StudyProtocol.wireId("uuid"))
    }
    @Test fun ignoresYesterdayTodayStatsAndPreservesUnknownCloudFields() {
        val local = study(30,1,"local",false)
        val remote = study(100,4,"remote",true)
        remote.data.getJSONObject("stats").put("lastDate","Tue Oct 06 2020")
        remote.data.put("futureField","kept")
        remote.data.getJSONObject("settings").put("futureSetting",true)
        val merged = StudyProtocol.merged(local,remote)
        assertEquals(30.0,merged.data.getJSONObject("stats").getDouble("todayStudyTime"),0.0)
        assertEquals("kept",merged.data.getString("futureField"))
        assertTrue(merged.data.getJSONObject("settings").getBoolean("futureSetting"))
        assertEquals(0.1,merged.data.getJSONObject("settings").getDouble("focusDuration"),0.0)
    }
    @Test fun supportsDecimalDurationsAndCustomLongBreakCycle() {
        val prefs = Preferences(focusSeconds = 6,breakSeconds = 12,longBreakSeconds = 24,countdownSeconds = 6,pomodoroCount = 2)
        val first = FocusClock(remainingMs = 6000).start(1000).tick(7000,prefs)
        assertEquals(6000L,first.studiedMs); assertEquals(Phase.BREAK,first.clock.phase); assertEquals(12000L,first.clock.remainingMs)
        val second = FocusClock(round = 1,remainingMs = 6000).start(1000).tick(7000,prefs)
        assertEquals(Phase.LONG_BREAK,second.clock.phase); assertEquals(24000L,second.clock.remainingMs)
        assertEquals(6000L,FocusClock.duration(TimerMode.COUNTDOWN,Phase.FOCUS,prefs))
    }
    @Test fun snapshotComparisonIgnoresObjectOrderAndNumericRepresentation() {
        val first = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":60},"todos":[]}"""))
        val second = CloudStudy(JSONObject("""{"todos":[],"stats":{"totalStudyTime":60.0}}"""))
        assertTrue(StudyProtocol.sameStudy(first,second))
        second.data.getJSONObject("stats").put("totalStudyTime",61)
        assertFalse(StudyProtocol.sameStudy(first,second))
    }
    @Test fun localBaselineIgnoresMidnightButDetectsHistoryAndSettingsChanges() {
        val first = study(60,1,"task",false)
        val second = CloudStudy(StudyProtocol.deepCopy(first.data),StudyProtocol.deepCopy(first.calendar))
        second.data.getJSONObject("stats").put("lastDate","Thu Oct 08 2026").put("todayStudyTime",0).put("todayPomodoros",0)
        second.calendar.getJSONObject("dailyLog").put("2026-10-08",JSONObject().put("studyTime",0).put("pomodoros",0))
        assertTrue(StudyProtocol.sameLocalStudy(first,second))
        second.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-08").put("studyTime",1)
        assertFalse(StudyProtocol.sameLocalStudy(first,second))
        second.calendar.getJSONObject("dailyLog").remove("2026-10-08")
        second.data.getJSONObject("settings").put("focusDuration",40)
        assertFalse(StudyProtocol.sameLocalStudy(first,second))
    }
}
