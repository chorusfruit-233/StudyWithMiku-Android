package io.github.chorusfruit233.studywithmiku

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyDataMergeTest {
    private fun merge(base: String,local: String,remote: String) = StudyDataMerge.merge(JSONObject(base),JSONObject(local),JSONObject(remote))
    @Test fun independentNestedEditsAndTodoFieldsBothSurvive() {
        val result = merge(
            """{"todos":[{"id":1,"text":"a","completed":false}],"settings":{"timer":{"focus":25,"break":5},"volume":10}}""",
            """{"todos":[{"id":1,"text":"a","completed":true}],"settings":{"timer":{"focus":30,"break":5},"volume":10}}""",
            """{"todos":[{"id":1,"text":"b","completed":false}],"settings":{"timer":{"focus":25,"break":10},"volume":20}}""")
        assertTrue(StudyProtocol.sameJson(JSONObject("""{"todos":[{"id":1,"text":"b","completed":true}],"settings":{"timer":{"focus":30,"break":10},"volume":20}}"""),result))
    }
    @Test fun cumulativeAndSameDayCountersAddOnlyLocalDelta() {
        fun doc(time: Int,count: Int) = """{"stats":{"totalStudyTime":$time,"totalPomodoros":$count,"todayStudyTime":$time,"todayPomodoros":$count,"lastDate":"2026-11-08"}}"""
        val stats = merge(doc(100,2),doc(160,4),doc(130,5)).getJSONObject("stats")
        assertEquals(190,stats.getInt("totalStudyTime")); assertEquals(7,stats.getInt("totalPomodoros"))
        assertEquals(190,stats.getInt("todayStudyTime")); assertEquals(7,stats.getInt("todayPomodoros"))
        val withoutBase = merge("{}",doc(100,2),doc(80,3)).getJSONObject("stats")
        assertEquals(100,withoutBase.getInt("totalStudyTime")); assertEquals(3,withoutBase.getInt("totalPomodoros"))
    }
    @Test fun resetCountersNeverSubtractRemoteProgressAndNewDayKeepsItsCounters() {
        val result = merge("""{"stats":{"totalStudyTime":100,"lastDate":"2026-11-08","todayStudyTime":30}}""",
            """{"stats":{"totalStudyTime":20,"lastDate":"2026-11-09","todayStudyTime":20}}""",
            """{"stats":{"totalStudyTime":140,"lastDate":"Sun Nov 08 2026","todayStudyTime":70}}""").getJSONObject("stats")
        assertEquals(140,result.getInt("totalStudyTime")); assertEquals(20,result.getInt("todayStudyTime"))
        assertEquals("2026-11-09",result.getString("lastDate"))
    }
    @Test fun invalidLocalDateCannotReplaceValidTodayCounters() {
        val result = merge("""{"stats":{"totalStudyTime":100,"lastDate":"2026-11-08","todayStudyTime":60}}""",
            """{"stats":{"totalStudyTime":160,"lastDate":"bad date","todayStudyTime":120}}""",
            """{"stats":{"totalStudyTime":130,"lastDate":"2026-11-08","todayStudyTime":90}}""").getJSONObject("stats")
        assertEquals(190,result.getInt("totalStudyTime"))
        assertEquals(90,result.getInt("todayStudyTime"))
        assertEquals("2026-11-08",result.getString("lastDate"))
    }
    @Test fun deletionWinsAgainstUnchangedButConcurrentEditSurvivesOnEitherSide() {
        val base = """{"todos":[{"id":1,"text":"a"},{"id":2,"text":"b"}]}"""
        val deleted = """{"todos":[]}"""
        val edited = """{"todos":[{"id":1,"text":"edited"},{"id":2,"text":"b"}]}"""
        for ((local,remote) in listOf(deleted to edited,edited to deleted)) {
            val todos = merge(base,local,remote).getJSONArray("todos")
            assertEquals(1,todos.length()); assertEquals("edited",todos.getJSONObject(0).getString("text"))
        }
    }
    @Test fun localOrderThenRemoteAdditionsAndNumericStringIdsRemainDistinct() {
        val result = merge("""{"todos":[]}""","""{"todos":[{"id":"z"},{"id":3},{"id":"a"}]}""",
            """{"todos":[{"id":"3"},{"id":"b"},{"id":"z"}]}""").getJSONArray("todos")
        assertEquals(listOf("z",3,"a","3","b"),(0 until result.length()).map { result.getJSONObject(it).get("id") })
    }
    @Test fun nullAndMissingAreDistinctAndInputsAreNotMutated() {
        val explicitNull = merge("{}","""{"settings":null}""","""{"settings":{"theme":"dark"}}""")
        assertTrue(explicitNull.has("settings")); assertTrue(explicitNull.isNull("settings"))
        val base = JSONObject("""{"settings":{"a":null,"b":1,"list":[1]}}""")
        val local = JSONObject("""{"settings":{"b":1,"list":[2]}}""")
        val remote = JSONObject("""{"settings":{"a":null,"b":2,"list":[3]}}""")
        val result = StudyDataMerge.merge(base,local,remote)
        assertFalse(result.getJSONObject("settings").has("a"))
        assertEquals(2,result.getJSONObject("settings").getInt("b"))
        result.getJSONObject("settings").getJSONArray("list").put(4)
        assertEquals(1,local.getJSONObject("settings").getJSONArray("list").length())
    }
}
