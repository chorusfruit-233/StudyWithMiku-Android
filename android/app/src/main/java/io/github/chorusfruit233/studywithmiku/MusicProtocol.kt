package io.github.chorusfruit233.studywithmiku

import org.json.JSONArray
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

const val DEFAULT_MUSIC_API = "https://api.qijieya.cn/meting/"
const val DEFAULT_MUSIC_PLAYLIST = "17543418420"
data class MusicConfig(val api: String = DEFAULT_MUSIC_API, val platform: String = "netease",
    val id: String = DEFAULT_MUSIC_PLAYLIST, val bitrate: String = "2000", val name: String = "Study With Miku 默认歌单")
data class Recommendation(val name: String, val description: String, val platform: String, val id: String, val tags: List<String>)
data class LyricLine(val timeMs: Long, val text: String)

object MusicProtocol {
    fun validate(config: MusicConfig) {
        val uri = runCatching { URI(config.api.trim()) }.getOrNull()
        require(uri != null && uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.query == null) { "请输入有效的 HTTP(S) 音乐接口地址，不含查询参数" }
        require(config.platform in listOf("netease","tencent")) { "请选择网易云或 QQ 音乐" }
        require(config.id.matches(Regex("[0-9]{1,30}"))) { "请输入数字歌单 ID，或粘贴歌单链接" }
        require(config.bitrate in listOf("2000","320")) { "不支持此音质" }
    }
    fun playlistUrl(config: MusicConfig): String {
        validate(config)
        fun enc(value: String) = URLEncoder.encode(value,"UTF-8")
        return "${config.api.trim()}?server=${enc(config.platform)}&type=playlist&id=${enc(config.id)}" +
            if (config.bitrate == "320") "" else "&br=${enc(config.bitrate)}"
    }
    fun playlistId(input: String): String? {
        val value = input.trim()
        if (value.matches(Regex("[0-9]{1,30}"))) return value
        return Regex("(?:[?&#](?:id|disstid)=|/playlist/)([0-9]{1,30})(?:[^0-9]|$)").find(value)?.groupValues?.get(1)
    }
    fun webUrl(platform: String, id: String) = if (platform == "tencent")
        "https://y.qq.com/n/ryqq/playlist/$id" else "https://music.163.com/#/playlist?id=$id"
    private fun httpUrl(value: String, base: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val uri = URI(base).resolve(value)
            if (uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() && uri.userInfo == null) uri.toASCIIString() else ""
        }.getOrDefault("")
    }
    fun songs(json: String, config: MusicConfig, names: Map<String,String> = emptyMap()): List<Song> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").ifBlank { o.optString("name") }.trim()
            val url = httpUrl(o.optString("url"),config.api)
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            val artist = o.optString("author").ifBlank { o.optString("artist") }
            val remoteId = o.optString("id").ifBlank {
                Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1) ?: "$title|$artist"
            }
            val key = MessageDigest.getInstance("SHA-256").digest(remoteId.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
            val clean = title.replace(Regex("\\s*-\\s*STUDY WITH MIKU ver\\.\\s*-\\s*$"),"")
            val display = if (config.id == DEFAULT_MUSIC_PLAYLIST) names[clean] ?: title else title
            Song("online_${config.platform}_$key",display,artist,url,
                httpUrl(o.optString("pic").ifBlank { o.optString("cover") },config.api),
                platform = config.platform,lyrics = o.optString("lrc").let { if (it.startsWith("[")) it else httpUrl(it,config.api) },
                playlistId = config.id,playlistIndex = i,playlistName = config.name)
        }.distinctBy { it.id }
    }
    fun lyrics(text: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?[0-9]+)]").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val stamp = Regex("\\[([0-9]+):([0-9]{2})(?:[.:]([0-9]{1,3}))?]")
        return text.lineSequence().flatMap { raw ->
            val stamps = stamp.findAll(raw).toList()
            val content = raw.substring(stamps.lastOrNull()?.range?.last?.plus(1) ?: raw.length).trim()
            stamps.asSequence().map {
                val fraction = it.groupValues[3].padEnd(3,'0').take(3).toLongOrNull() ?: 0
                LyricLine((it.groupValues[1].toLong()*60_000 + it.groupValues[2].toLong()*1000 + fraction + offset).coerceAtLeast(0),content)
            }
        }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()
    }
}
