package com.shshouse.studywithmiku

import org.json.JSONObject
import org.json.JSONArray
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

const val STUDY_SITE = "https://study.mikumod.com"
const val ACCOUNT_SITE = "https://mikumod.com"
const val STUDY_WS = "wss://online.study.mikugame.cc/ws"
val LOGIN_URL = "$ACCOUNT_SITE/login?redirect=study&callback=${URLEncoder.encode(STUDY_SITE,"UTF-8")}"
data class TokenClaims(val userId: String, val username: String, val expiresAt: Long)
data class ChatMessage(val id: String, val userId: String, val username: String, val content: String,
    val createdAt: String, val location: String = "", val roomId: String = "global") {
    val timestamp get() = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0)
}
data class SharedSong(val platform: String, val playlistId: String, val index: Int, val name: String, val cover: String, val playlistName: String)
data class CloudStudy(val data: JSONObject = JSONObject(), val calendar: JSONObject = JSONObject())

object StudyProtocol {
    fun callbackToken(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "study.mikumod.com" || uri.port !in listOf(-1,443) || uri.userInfo != null || uri.path !in listOf("","/")) return null
        return uri.rawFragment?.split('&')?.map { it.split('=',limit = 2) }?.firstOrNull { it.first() == "study_token" && it.size == 2 }
            ?.get(1)?.let { runCatching { URLDecoder.decode(it,"UTF-8") }.getOrNull() }?.takeIf { it.length in 1..16384 }
    }
    /** Claims are display hints; the account API must validate the signature before login is accepted. */
    fun tokenClaims(token: String, nowSeconds: Long = Instant.now().epochSecond): TokenClaims {
        val parts = token.split('.')
        require(parts.size == 3 && parts.all { it.isNotBlank() }) { "登录凭据格式无效" }
        val header = JSONObject(String(Base64.getUrlDecoder().decode(parts[0]),Charsets.UTF_8))
        require(header.optString("alg").isNotBlank() && header.optString("alg").lowercase() != "none") { "登录凭据格式无效" }
        val p = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]),Charsets.UTF_8))
        val uid = p.optString("sub")
        require(uid.isNotBlank() && uid.length <= 200) { "登录凭据缺少用户信息" }
        val expiry = p.optLong("exp")
        require(expiry > nowSeconds) { "登录已过期，请重新登录" }
        return TokenClaims(uid,p.optString("username").ifBlank { uid.take(8) },expiry)
    }
    fun chat(o: JSONObject): ChatMessage? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return ChatMessage(id,o.optString("userId"),o.optString("username"),o.optString("content"),
            o.optString("createdAt"),o.optString("location"),o.optString("roomId","global"))
    }
    fun mergeMessages(vararg lists: List<ChatMessage>) = lists.flatMap { it }.associateBy { it.id }.values
        .sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
    fun stickerId(content: String): Int? = Regex("^\\[sticker:([0-9]+)]$").matchEntire(content)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..16 }
    fun stickerMessage(id: Int) = if (id in 1..16 && id != 10) "[sticker:$id]" else ""
    fun stickerUrl(id: Int): String {
        val ext = when (id) { 4,11,13,14 -> "jpg"; 12,15 -> "gif"; 16 -> "png"; else -> "webp" }
        return "$STUDY_SITE/sticker/$id.$ext"
    }
    private fun payload(content: String,prefix: String): JSONObject? = if (content.startsWith(prefix) && content.endsWith(']'))
        runCatching { JSONObject(content.substring(prefix.length,content.length-1)) }.getOrNull() else null
    fun sharedSong(content: String): SharedSong? {
        val p = payload(content,"[music:") ?: return null
        val platform = p.optString("p")
        val id = p.optString("pid")
        val index = p.optDouble("i",Double.NaN)
        if (platform !in listOf("netease","tencent") || !id.matches(Regex("[0-9]{1,30}")) || !index.isFinite() || index < 0 || index % 1 != 0.0 || index > 100000) return null
        return SharedSong(platform,id,index.toInt(),p.optString("n"),p.optString("c"),p.optString("pn"))
    }
    fun songShare(song: Song): String? {
        if (song.playlistId.isBlank() || song.playlistIndex < 0) return null
        val platform = song.platform.ifBlank { if (song.builtin) "netease" else "" }
        if (platform !in listOf("netease","tencent")) return null
        val cover = song.publicCover.ifBlank { song.cover }.let { Regex("[?&]id=([^&]+)").find(it)?.groupValues?.get(1) ?: it }
        return "[music:${JSONObject().put("p",platform).put("pid",song.playlistId).put("i",song.playlistIndex)
            .put("n",song.webTitle.ifBlank { song.title }.take(180)).put("c",cover).put("pn",song.playlistName.take(80))}]"
    }
    fun sharedCover(share: SharedSong,api: String): String {
        val cover = share.cover
        if (cover.startsWith('/')) return "$STUDY_SITE$cover"
        if (cover.startsWith("http")) {
            val uri = runCatching { URI(cover) }.getOrNull() ?: return ""
            return cover.takeIf { uri.scheme in listOf("http","https") && uri.userInfo == null && uri.host in setOf(
                "p1.music.126.net","p2.music.126.net","p3.music.126.net","p4.music.126.net","y.gtimg.cn","imgcache.qq.com") } ?: ""
        }
        return if (cover.isNotBlank()) "$api?server=${share.platform}&type=pic&id=${URLEncoder.encode(cover,"UTF-8")}" else ""
    }
    fun countdown(content: String): CountdownDay? {
        val p = payload(content,"[countdown:") ?: return null
        val title = p.optString("t").take(40)
        val date = p.optString("d")
        if (title.isBlank() || !validDate(date)) return null
        return CountdownDay("shared",title,date)
    }
    fun countdownShare(day: CountdownDay) = "[countdown:${JSONObject().put("t",day.title.take(40)).put("d",day.date)}]"
    fun validDate(value: String) = value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && runCatching { LocalDate.parse(value) }.isSuccess
    fun wireId(id: String): Any = id.toLongOrNull() ?: id
    fun jsDate(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEE MMM dd yyyy",Locale.US))
    fun deepCopy(o: JSONObject) = JSONObject(o.toString())
    /** Compare JSON values, not key order or 60 versus 60.0 returned by different clients. */
    private fun canonical(value: Any?): String = when (value) {
        null,JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") {
            JSONObject.quote(it)+":"+canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonical(value.get(it)) }
        is Number -> runCatching { java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString() }.getOrDefault(value.toString())
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    fun sameStudy(a: CloudStudy,b: CloudStudy) = canonical(a.data) == canonical(b.data) && canonical(a.calendar) == canonical(b.calendar)
    /** Daily history is authoritative locally; midnight rolls the derived today counters over. */
    fun sameLocalStudy(a: CloudStudy,b: CloudStudy): Boolean {
        fun stable(study: CloudStudy): CloudStudy {
            val data = deepCopy(study.data)
            data.optJSONObject("stats")?.let { stats -> listOf("lastDate","todayStudyTime","todayPomodoros").forEach { stats.remove(it) } }
            val calendar = deepCopy(study.calendar)
            calendar.optJSONObject("dailyLog")?.let { log -> log.keys().asSequence().toList().forEach { date ->
                val day = log.optJSONObject(date)
                if (day != null && day.optDouble("studyTime",0.0) == 0.0 && day.optInt("pomodoros",0) == 0) log.remove(date)
            } }
            return CloudStudy(data,calendar)
        }
        return sameStudy(stable(a),stable(b))
    }
    fun merged(local: CloudStudy,remote: CloudStudy): CloudStudy {
        val data = deepCopy(remote.data)
        val stats = deepCopy(remote.data.optJSONObject("stats") ?: JSONObject())
        val localStats = local.data.optJSONObject("stats") ?: JSONObject()
        listOf("totalStudyTime","totalPomodoros").forEach { stats.put(it,maxOf(stats.optDouble(it),localStats.optDouble(it))) }
        val today = jsDate(LocalDate.now())
        listOf("todayStudyTime","todayPomodoros").forEach { stats.put(it,maxOf(
            if (stats.optString("lastDate") == today) stats.optDouble(it) else 0.0,
            if (localStats.optString("lastDate") == today) localStats.optDouble(it) else 0.0)) }
        stats.put("lastDate",today)
        data.put("stats",stats)
        fun combined(a: JSONArray?,b: JSONArray?): JSONArray {
            val byId = linkedMapOf<String,JSONObject>()
            listOf(b,a).forEach { arr -> if (arr != null) for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { item -> if (item.optString("id").isNotBlank()) byId[item.optString("id")] = item }
            } }
            return JSONArray(byId.values.toList())
        }
        data.put("todos",combined(local.data.optJSONArray("todos"),remote.data.optJSONArray("todos")))
        val settings = deepCopy(remote.data.optJSONObject("settings") ?: JSONObject())
        local.data.optJSONObject("settings")?.let { s -> s.keys().forEach { key -> if (key != "countdowns") settings.put(key,s.get(key)) } }
        settings.put("countdowns",combined(local.data.optJSONObject("settings")?.optJSONArray("countdowns"),settings.optJSONArray("countdowns")))
        data.put("settings",settings)
        val log = JSONObject()
        val plans = JSONObject()
        val dates = linkedSetOf<String>()
        listOf(local.calendar,remote.calendar).forEach { c -> c.optJSONObject("dailyLog")?.keys()?.forEach { dates.add(it) }; c.optJSONObject("plans")?.keys()?.forEach { dates.add(it) } }
        dates.forEach { date ->
            val l = local.calendar.optJSONObject("dailyLog")?.optJSONObject(date) ?: JSONObject()
            val r = remote.calendar.optJSONObject("dailyLog")?.optJSONObject(date) ?: JSONObject()
            if (l.length() > 0 || r.length() > 0) log.put(date,deepCopy(r).put("studyTime",maxOf(l.optDouble("studyTime"),r.optDouble("studyTime")))
                .put("pomodoros",maxOf(l.optInt("pomodoros"),r.optInt("pomodoros"))))
            val array = combined(local.calendar.optJSONObject("plans")?.optJSONArray(date),remote.calendar.optJSONObject("plans")?.optJSONArray(date))
            if (array.length() > 0) plans.put(date,array)
        }
        return CloudStudy(data,deepCopy(remote.calendar).put("dailyLog",log).put("plans",plans))
    }
}
