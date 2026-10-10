package io.github.chorusfruit233.studywithmiku

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SavedNetworkPlaylist(val platform: String,val id: String,val name: String)

/** Local platform/ID bookmarks, separate from the native song-based playlists. */
object NetworkPlaylistCatalog {
    private fun valid(platform: String,id: String) = platform in listOf("netease","tencent") && id.matches(Regex("[0-9]{1,30}"))
    fun read(array: JSONArray): List<SavedNetworkPlaylist> = array.objects().mapNotNull { entry ->
        val platform = entry.optString("platform")
        val id = entry.optString("playlistId").trim()
        if (!valid(platform,id)) null else SavedNetworkPlaylist(platform,id,entry.optString("name").trim().take(50).ifBlank { "ID:$id" })
    }.distinctBy { it.platform to it.id }
    fun write(list: List<SavedNetworkPlaylist>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("platform",it.platform).put("playlistId",it.id).put("name",it.name)) }
    }
    fun add(list: List<SavedNetworkPlaylist>,platform: String,id: String,name: String): List<SavedNetworkPlaylist> {
        val normalized = id.trim()
        require(valid(platform,normalized)) { "无效的网络歌单" }
        val previous = list.find { it.platform == platform && it.id == normalized }
        val title = name.trim().take(50).ifBlank { previous?.name ?: "ID:$normalized" }
        return listOf(SavedNetworkPlaylist(platform,normalized,title)) + list.filterNot { it.platform == platform && it.id == normalized }
    }
    fun remove(list: List<SavedNetworkPlaylist>,platform: String,id: String) = list.filterNot { it.platform == platform && it.id == id }
}

/** Optional naming service added upstream; failure never prevents a valid playlist from loading. */
class PlaylistNameService(private val baseUrl: String = "https://online.study.mikugame.cc",
    client: OkHttpClient = StudyNetwork.client) {
    private val client = client.newBuilder().callTimeout(4,TimeUnit.SECONDS).connectTimeout(4,TimeUnit.SECONDS)
        .readTimeout(4,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    fun fetch(platform: String,id: String): String {
        if (platform !in listOf("netease","tencent") || !id.matches(Regex("[0-9]{1,20}"))) return ""
        return runCatching {
            val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("playlist-name")
                .addQueryParameter("platform",platform).addQueryParameter("id",id).build()
            val request = Request.Builder().url(url).header("Origin",STUDY_SITE).header("Referer","$STUDY_SITE/").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                val source = response.body?.source() ?: return@use ""
                source.request(128 * 1024L + 1)
                if (source.buffer.size > 128 * 1024L) return@use ""
                val name = JSONObject(source.readUtf8()).opt("name")
                if (name is String) name.trim().take(50) else ""
            }
        }.getOrDefault("")
    }
}
