package io.github.chorusfruit233.studywithmiku

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SparseStudyMergeTest {
    private fun data() = JSONObject("""{"stats":{"totalStudyTime":600.125,"totalPomodoros":4}}""")

    @Test fun daysPresentOnOnlyOneSideKeepBothHistoriesWithoutNaN() {
        val local = CloudStudy(data(),JSONObject("""{"dailyLog":{"2026-10-10":{"studyTime":60.001,"pomodoros":1,"localNote":"keep"}}}"""))
        val remote = CloudStudy(data(),JSONObject("""{"dailyLog":{"2026-10-11":{"studyTime":90.008,"pomodoros":2,"remoteNote":"keep"}}}"""))
        val before = local.calendar.toString()+remote.calendar.toString()
        val result = StudyProtocol.merged(local,remote)
        val days = result.calendar.getJSONObject("dailyLog")
        assertEquals(60.001,days.getJSONObject("2026-10-10").getDouble("studyTime"),0.0)
        assertEquals(90.008,days.getJSONObject("2026-10-11").getDouble("studyTime"),0.0)
        assertEquals(1,days.getJSONObject("2026-10-10").getInt("pomodoros"))
        assertEquals(2,days.getJSONObject("2026-10-11").getInt("pomodoros"))
        assertEquals("keep",days.getJSONObject("2026-10-10").getString("localNote"))
        assertEquals("keep",days.getJSONObject("2026-10-11").getString("remoteNote"))
        assertEquals(600.125,result.data.getJSONObject("stats").getDouble("totalStudyTime"),0.0)
        assertEquals(before,local.calendar.toString()+remote.calendar.toString())
    }

    @Test fun missingStatisticsUseZeroAndRetainTheOtherSidesCredit() {
        val local = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":1.001,"totalPomodoros":2}}"""))
        val result = StudyProtocol.merged(local,CloudStudy())
        val stats = result.data.getJSONObject("stats")
        assertEquals(1.001,stats.getDouble("totalStudyTime"),0.0)
        assertEquals(2,stats.getInt("totalPomodoros"))
        assertEquals(0.0,stats.getDouble("todayStudyTime"),0.0)
        assertEquals(0,stats.getInt("todayPomodoros"))
        assertNotNull(result.data.toString())
    }

    @Test fun incompleteDailyRecordsRetainFiniteTimeAndBothSidesExtraFields() {
        val local = CloudStudy(data(),JSONObject("""{"dailyLog":{"2026-10-10":{"studyTime":7.25,"localNote":"keep"}}}"""))
        val remote = CloudStudy(data(),JSONObject("""{"dailyLog":{"2026-10-10":{"pomodoros":3,"remoteNote":"keep"}}}"""))
        val day = StudyProtocol.merged(local,remote).calendar.getJSONObject("dailyLog").getJSONObject("2026-10-10")
        assertEquals(7.25,day.getDouble("studyTime"),0.0)
        assertEquals(3,day.getInt("pomodoros"))
        assertEquals("keep",day.getString("localNote")); assertEquals("keep",day.getString("remoteNote"))
    }

    @Test fun nonFiniteLegacyNumericStringsNeverDestroyFiniteCreditOrMutateInputs() {
        val today = StudyProtocol.jsDate(LocalDate.now())
        val local = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":1.001,"totalPomodoros":2,"lastDate":"$today","todayStudyTime":"NaN"}}"""),
            JSONObject("""{"dailyLog":{"2026-10-10":{"studyTime":1.001,"pomodoros":1}}}"""))
        val remote = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":"Infinity","totalPomodoros":null,"lastDate":"$today","todayPomodoros":"NaN"}}"""),
            JSONObject("""{"dailyLog":{"2026-10-10":{"studyTime":null,"pomodoros":1}}}"""))
        val before = local.data.toString()+remote.data.toString()
        val merged = StudyProtocol.merged(local,remote)
        assertEquals(1.001,merged.data.getJSONObject("stats").getDouble("totalStudyTime"),0.0)
        assertEquals(2,merged.data.getJSONObject("stats").getInt("totalPomodoros"))
        assertEquals(1.001,merged.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-10").getDouble("studyTime"),0.0)
        assertEquals(0.0,merged.data.getJSONObject("stats").getDouble("todayStudyTime"),0.0)
        assertEquals(before,local.data.toString()+remote.data.toString())
    }

    @Test fun sharedRecordIdsKeepLocalEditsAndExtraFieldsFromBothSides() {
        val local = CloudStudy(data().put("todos",JSONArray("""[{"id":1,"text":"local edit","localField":true}]""")))
        val remote = CloudStudy(data().put("todos",JSONArray("""[{"id":1,"text":"remote text","remoteField":"keep"}]""")))
        val merged = StudyProtocol.merged(local,remote).data.getJSONArray("todos")
        assertEquals(1,merged.length())
        assertEquals("local edit",merged.getJSONObject(0).getString("text"))
        assertTrue(merged.getJSONObject(0).getBoolean("localField"))
        assertEquals("keep",merged.getJSONObject(0).getString("remoteField"))
        assertEquals("remote text",remote.data.getJSONArray("todos").getJSONObject(0).getString("text"))
    }
}
