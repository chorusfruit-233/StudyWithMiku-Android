package io.github.chorusfruit233.studywithmiku

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Bounded requests with platform TLS checks, redirects and actionable HTTP errors. */
object MusicHttp {
    fun bytes(url: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent","StudyWithMiku-Android/1.1")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("音乐服务返回 HTTP $code")
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw IOException("音乐服务响应过大")
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    fun text(url: String) = bytes(url).toString(Charsets.UTF_8)
}
data class OnlineMusicState(val config: MusicConfig = MusicConfig(), val ids: List<String> = emptyList(),
    val loading: Boolean = false, val loadingName: String = "", val error: String? = null, val cached: Boolean = false)

class OnlineMusic(context: Context, private val store: MikuStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val recommendations = JSONArray(context.assets.open("recommendations.json").bufferedReader().use { it.readText() }).objects().map {
        Recommendation(it.getString("name"),it.optString("desc"),it.getString("platform"),it.getString("playlistId"),it.getJSONArray("tags").strings())
    }
    private val names = JSONObject(context.assets.open("song-names.json").bufferedReader().use { it.readText() }).let { o ->
        o.keys().asSequence().associateWith { o.getString(it) }
    }
    private val _state = MutableStateFlow(OnlineMusicState(store.readMusicConfig(),store.readOnlineIds(),cached = store.readOnlineIds().isNotEmpty()))
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var attempted = _state.value.config
    private var initialized = false
    private val specialOrder = JSONArray(context.assets.open("special-song-order.json").bufferedReader().use { it.readText() }).strings()
    fun ensureLoaded() { if (!initialized) { initialized = true; load(_state.value.config) } }
    fun retry() = load(attempted)
    fun load(config: MusicConfig = _state.value.config, onLoaded: (List<Song>) -> Unit = {}) {
        job?.cancel()
        attempted = config
        _state.value = _state.value.copy(loading = true,loadingName = config.name,error = null)
        job = scope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    val fetched = MusicProtocol.songs(MusicHttp.text(MusicProtocol.playlistUrl(config)),config,names)
                    check(fetched.isNotEmpty()) { "歌单为空或暂不可用，请检查歌单 ID、平台或稍后重试" }
                    if (config.platform == "netease" && config.id == "8894040639") fetched + specialOrder.mapIndexedNotNull { i,id ->
                        store.library.value.songs.find { it.id == id }?.let { it.copy(title = it.webTitle.ifBlank { it.title },
                            playlistId = config.id,playlistIndex = fetched.size+i,playlistName = config.name) }
                    }
                    else fetched
                }
                ensureActive()
                withContext(Dispatchers.IO) { store.cacheOnline(config,songs) }
                _state.value = OnlineMusicState(config,songs.map { it.id })
                onLoaded(songs)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(loading = false,error = "${config.name}加载失败：${e.message ?: "请检查网络连接"}")
            }
        }
    }
}
