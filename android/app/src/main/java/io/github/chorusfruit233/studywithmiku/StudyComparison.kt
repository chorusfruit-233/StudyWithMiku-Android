package io.github.chorusfruit233.studywithmiku

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Compare effective study data across web/native encodings, without mutating either document. */
internal object StudyComparison {
    fun same(a: CloudStudy,b: CloudStudy) = StudyProtocol.sameStudy(normalized(a),normalized(b))

    private fun normalized(study: CloudStudy): CloudStudy {
        val data = StudyProtocol.deepCopy(study.data)
        val calendar = StudyProtocol.deepCopy(study.calendar)
        fun JSONObject.objectAt(key: String) = optJSONObject(key) ?: JSONObject().also { put(key,it) }
        fun JSONObject.defaults(vararg values: Pair<String,Any>) {
            values.forEach { (key,value) -> if (!has(key) || isNull(key)) put(key,value) }
        }
        fun JSONObject.millis(key: String) {
            val seconds = optDouble(key,0.0)
            if (seconds.isFinite()) put(key,StudyProtocol.studyMillis(seconds))
        }
        fun items(array: JSONArray?,vararg defaults: Pair<String,Any>): JSONArray = JSONArray().apply {
            if (array != null) for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item == null) put(array.get(i)) else {
                    if (item.has("id")) item.put("id",item.optString("id"))
                    item.defaults(*defaults)
                    put(item)
                }
            }
        }
        val stats = data.objectAt("stats")
        val daily = calendar.objectAt("dailyLog")
        // A today's counter can contain credit not yet present in the separate calendar document.
        // Preserve that credit before ignoring the date/counters that otherwise only roll at midnight.
        val date = runCatching { LocalDate.parse(stats.optString("lastDate"),
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy",Locale.US)).toString() }.getOrNull()
        if (date != null && (stats.optDouble("todayStudyTime",0.0) > 0 || stats.optInt("todayPomodoros",0) > 0)) {
            val day = daily.objectAt(date)
            day.put("studyTime",maxOf(day.optDouble("studyTime",0.0),stats.optDouble("todayStudyTime",0.0)))
            day.put("pomodoros",maxOf(day.optInt("pomodoros",0),stats.optInt("todayPomodoros",0)))
        }
        listOf("lastDate","todayStudyTime","todayPomodoros").forEach { stats.remove(it) }
        stats.defaults("totalStudyTime" to 0,"totalPomodoros" to 0)
        stats.millis("totalStudyTime")
        daily.keys().asSequence().toList().forEach { key -> daily.optJSONObject(key)?.let { day ->
            day.defaults("studyTime" to 0,"pomodoros" to 0)
            day.millis("studyTime")
            if (day.optLong("studyTime") == 0L && day.optInt("pomodoros") == 0 && day.length() == 2) daily.remove(key)
        } }
        data.put("todos",items(data.optJSONArray("todos"),"completed" to false,"pinned" to false))
        val settings = data.objectAt("settings")
        // Defaults are the original web project's defaults, not arbitrary values from this device.
        settings.defaults("focusDuration" to 25,"breakDuration" to 5,"countdownDuration" to 10,
            "timerMode" to "pomodoro","pomodoroCount" to 4,"notificationVolume" to 0.6,
            "pauseMusicDuringBreak" to true,"showHitokoto" to true,"hidePomodoroOnIdle" to false)
        listOf("focusDuration","breakDuration","countdownDuration").forEach { key ->
            val minutes = settings.optDouble(key)
            if (minutes.isFinite()) settings.put(key,(minutes*60).roundToLong())
        }
        val volume = settings.optDouble("notificationVolume")
        if (volume.isFinite()) settings.put("notificationVolume",volume.toFloat())
        settings.put("countdowns",items(settings.optJSONArray("countdowns")))
        val plans = calendar.objectAt("plans")
        plans.keys().asSequence().toList().forEach { key ->
            val array = plans.optJSONArray(key)
            if (array != null) {
                val normalized = items(array,"done" to false,"color" to "#39c5bb")
                if (normalized.length() == 0) plans.remove(key) else plans.put(key,normalized)
            }
        }
        return CloudStudy(data,calendar)
    }

    fun differences(a: CloudStudy,b: CloudStudy): List<String> {
        val first = normalized(a); val second = normalized(b)
        val result = mutableListOf<String>()
        fun compare(label: String,left: Any?,right: Any?) {
            if (!StudyProtocol.sameJson(left,right)) result.add(label)
        }
        compare("累计学习记录",first.data.opt("stats"),second.data.opt("stats"))
        compare("每日学习记录",first.calendar.opt("dailyLog"),second.calendar.opt("dailyLog"))
        compare("待办",first.data.opt("todos"),second.data.opt("todos"))
        compare("日历计划",first.calendar.opt("plans"),second.calendar.opt("plans"))
        val firstSettings = first.data.getJSONObject("settings")
        val secondSettings = second.data.getJSONObject("settings")
        compare("倒数日",firstSettings.remove("countdowns"),secondSettings.remove("countdowns"))
        compare("计时设置",firstSettings,secondSettings)
        listOf("stats","todos","settings").forEach { first.data.remove(it); second.data.remove(it) }
        listOf("dailyLog","plans").forEach { first.calendar.remove(it); second.calendar.remove(it) }
        if (!StudyProtocol.sameStudy(first,second)) result.add("其他资料")
        return result
    }

    fun describe(study: CloudStudy): String {
        val stats = study.data.optJSONObject("stats")
        val millis = StudyProtocol.studyMillis(stats?.optDouble("totalStudyTime",0.0) ?: 0.0).coerceAtLeast(0)
        val seconds = java.math.BigDecimal.valueOf(millis%60_000,3).stripTrailingZeros().toPlainString()
        return "${millis/60_000} 分钟 $seconds 秒 · ${stats?.optInt("totalPomodoros",0) ?: 0} 个番茄"
    }
}
