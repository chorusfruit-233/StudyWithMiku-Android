package com.shshouse.studywithmiku

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyComparisonTest {
    private fun fixture() = CloudStudy(JSONObject("""{
        "stats":{"totalStudyTime":56100,"totalPomodoros":32,"lastDate":"Thu Oct 08 2026","todayStudyTime":600,"todayPomodoros":1},
        "todos":[{"id":123,"text":"task","completed":false}],
        "settings":{"focusDuration":6.1,"notificationVolume":0.6}
    }"""),JSONObject("""{
        "dailyLog":{"2026-10-08":{"studyTime":600,"pomodoros":1}},
        "plans":{"2026-10-08":[{"id":456,"text":"plan"}]}
    }"""))
    private fun copy(study: CloudStudy) = CloudStudy(StudyProtocol.deepCopy(study.data),StudyProtocol.deepCopy(study.calendar))

    @Test fun representationalChangesAreEqualAndInputsAreUntouched() {
        val web = fixture(); val native = copy(web)
        native.data.getJSONObject("stats").put("lastDate","Fri Oct 09 2026").put("todayStudyTime",0).put("todayPomodoros",0)
        native.data.getJSONObject("settings").put("notificationVolume",0.6f.toDouble())
        native.data.getJSONArray("todos").getJSONObject(0).put("id","123").put("pinned",false)
        native.calendar.getJSONObject("plans").getJSONArray("2026-10-08").getJSONObject(0)
            .put("id","456").put("done",false).put("color","#39c5bb")
        native.calendar.getJSONObject("plans").put("2026-10-09",JSONArray())
        native.calendar.getJSONObject("dailyLog").put("2026-10-09",JSONObject("""{"studyTime":0,"pomodoros":0}"""))
        val originalWeb = web.data.toString()+web.calendar.toString()
        val originalNative = native.data.toString()+native.calendar.toString()
        assertTrue(StudyComparison.same(web,native)); assertTrue(StudyComparison.differences(web,native).isEmpty())
        assertEquals(originalWeb,web.data.toString()+web.calendar.toString())
        assertEquals(originalNative,native.data.toString()+native.calendar.toString())
    }
    @Test fun equalDisplayedWholeMinutesNeverHideGenuineSecondsOrHistoryDifferences() {
        val web = fixture(); val native = copy(web)
        native.data.getJSONObject("stats").put("totalStudyTime",56100.001)
        assertFalse(StudyComparison.same(web,native))
        assertEquals(listOf("累计学习记录"),StudyComparison.differences(web,native))
        native.data.getJSONObject("stats").put("totalStudyTime",56100)
        native.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-08").put("studyTime",601)
        assertFalse(StudyComparison.same(web,native))
        assertEquals(listOf("每日学习记录"),StudyComparison.differences(web,native))
        assertEquals("935 分钟 0.001 秒 · 32 个番茄",StudyComparison.describe(CloudStudy(
            JSONObject("""{"stats":{"totalStudyTime":56100.001,"totalPomodoros":32}}"""))))
    }
    @Test fun genuineTodoPlanCountdownAndSettingsChangesAreStillConflicts() {
        val web = fixture(); val native = copy(web)
        native.data.getJSONArray("todos").getJSONObject(0).put("completed",true)
        native.calendar.getJSONObject("plans").getJSONArray("2026-10-08").getJSONObject(0).put("text","edited plan")
        native.data.getJSONObject("settings").put("breakDuration",8)
            .put("countdowns",JSONArray("""[{"id":1,"title":"exam","date":"2027-01-01"}]"""))
        assertFalse(StudyComparison.same(web,native))
        assertEquals(listOf("待办","日历计划","倒数日","计时设置"),StudyComparison.differences(web,native))
    }
    @Test fun todayCountersWithUnrecordedCreditAreNotSilentlyIgnored() {
        val web = fixture(); val native = copy(web)
        native.data.getJSONObject("stats").put("todayStudyTime",601)
        assertFalse(StudyComparison.same(web,native))
        assertEquals(listOf("每日学习记录"),StudyComparison.differences(web,native))
    }
    @Test fun absentEmptySectionsAreEquivalentButUnknownFieldsAreNotDiscarded() {
        val first = CloudStudy(); val second = CloudStudy(JSONObject("""{"todos":[],"settings":{"countdowns":[]}}"""),
            JSONObject("""{"dailyLog":{},"plans":{}}"""))
        assertTrue(StudyComparison.same(first,second))
        second.data.put("futureField","preserve")
        assertFalse(StudyComparison.same(first,second))
        assertEquals(listOf("其他资料"),StudyComparison.differences(first,second))
        second.data.remove("futureField")
        second.calendar.getJSONObject("dailyLog").put("2026-10-09",JSONObject("""{"studyTime":0,"pomodoros":0,"note":"preserve"}"""))
        assertFalse(StudyComparison.same(first,second))
    }
    @Test fun nativeConversionRoundsInsteadOfTruncatingFloatingPointNoise() {
        assertEquals(366,StudyProtocol.durationSeconds(6.1))
        assertEquals(1001L,StudyProtocol.studyMillis(1.001))
        assertEquals(0L,StudyProtocol.studyMillis(Double.NaN))
        assertTrue(StudyComparison.same(CloudStudy(JSONObject("""{"stats":{"totalStudyTime":1.001}}""")),
            CloudStudy(JSONObject("""{"stats":{"totalStudyTime":1.0009999999999999}}"""))))
    }
    @Test fun todoOrderAndRealVolumeChangesRemainSignificant() {
        val first = fixture(); val second = copy(first)
        second.data.getJSONObject("settings").put("notificationVolume",0.65)
        assertFalse(StudyComparison.same(first,second))
        second.data.getJSONObject("settings").put("notificationVolume",0.6)
        val todo = first.data.getJSONArray("todos").getJSONObject(0)
        val another = JSONObject("""{"id":789,"text":"second task"}""")
        first.data.put("todos",JSONArray().put(todo).put(another))
        second.data.put("todos",JSONArray().put(another).put(todo))
        assertFalse(StudyComparison.same(first,second))
    }
}
