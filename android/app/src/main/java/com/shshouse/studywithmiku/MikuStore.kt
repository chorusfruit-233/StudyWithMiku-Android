package com.shshouse.studywithmiku

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class MikuStore(private val context: Context) {
    private val disk = context.getSharedPreferences("miku_offline_v1", Context.MODE_PRIVATE)
    private fun obj(key: String) = runCatching { JSONObject(disk.getString(key, "{}")!!) }.getOrDefault(JSONObject())
    private fun arr(key: String) = runCatching { JSONArray(disk.getString(key, "[]")!!) }.getOrDefault(JSONArray())
    private val _prefs = MutableStateFlow(readPreferences())
    val prefs = _prefs.asStateFlow()
    private val _todos = MutableStateFlow(readTodos())
    val todos = _todos.asStateFlow()
    private val _logs = MutableStateFlow(readLogs())
    val logs = _logs.asStateFlow()
    private val _library = MutableStateFlow(readLibrary())
    val library = _library.asStateFlow()

    private fun readPreferences(): Preferences {
        val p = obj("preferences")
        return Preferences(p.optInt("focus",25).coerceIn(1,180), p.optInt("break",5).coerceIn(1,60),
            p.optInt("long",15).coerceIn(1,60), p.optInt("countdown",30).coerceIn(1,180),
            p.optInt("goal",120).coerceIn(15,600),p.optInt("scene",0).coerceIn(0,2),
            p.optInt("theme",0).coerceIn(0,2),p.optBoolean("dynamic",false),
            p.optBoolean("animate",true),p.optBoolean("screen",false),p.optBoolean("sound",true))
    }
    fun updatePreferences(p: Preferences) {
        _prefs.value = p
        disk.edit().putString("preferences",JSONObject().put("focus",p.focusMinutes).put("break",p.breakMinutes)
            .put("long",p.longBreakMinutes).put("countdown",p.countdownMinutes).put("goal",p.dailyGoalMinutes)
            .put("scene",p.scene).put("theme",p.theme).put("dynamic",p.dynamicColor)
            .put("animate",p.animateScene).put("screen",p.keepScreenOn).put("sound",p.completionSound).toString()).apply()
    }
    private fun readTodos() = arr("todos").objects().map {
        Todo(it.getString("id"),it.getString("text"),it.getString("date"),it.optBoolean("done"))
    }
    private fun saveTodos(list: List<Todo>) {
        _todos.value = list
        disk.edit().putString("todos",JSONArray().apply { list.forEach { put(JSONObject()
            .put("id",it.id).put("text",it.text).put("date",it.date).put("done",it.done)) } }.toString()).apply()
    }
    fun addTodo(text: String, date: LocalDate) {
        if (text.isNotBlank()) saveTodos(_todos.value + Todo(UUID.randomUUID().toString(),text.trim().take(200),date.toString()))
    }
    fun toggleTodo(id: String) = saveTodos(_todos.value.map { if (it.id == id) it.copy(done = !it.done) else it })
    fun deleteTodo(id: String) = saveTodos(_todos.value.filterNot { it.id == id })
    private fun readLogs(): Map<String,DayLog> {
        val o = obj("logs")
        return o.keys().asSequence().associateWith { key -> o.getJSONObject(key).let { DayLog(it.optLong("millis"),it.optInt("sessions")) } }
    }
    /** Split credit at local midnight so overnight sessions belong to the right days. */
    fun record(millis: Long, completed: Boolean, endingWallMs: Long = System.currentTimeMillis()) {
        val updated = _logs.value.toMutableMap()
        var cursor = endingWallMs - millis
        while (cursor < endingWallMs) {
            val day = Instant.ofEpochMilli(cursor).atZone(ZoneId.systemDefault()).toLocalDate()
            val boundary = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = minOf(boundary,endingWallMs)
            val old = updated[day.toString()] ?: DayLog()
            updated[day.toString()] = old.copy(millis = old.millis + (end - cursor))
            cursor = end
        }
        if (completed) {
            val key = Instant.ofEpochMilli(endingWallMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            val old = updated[key] ?: DayLog()
            updated[key] = old.copy(sessions = old.sessions + 1)
        }
        _logs.value = updated
    }
    private fun logsJson() = JSONObject().apply { _logs.value.forEach { (key,value) ->
        put(key,JSONObject().put("millis",value.millis).put("sessions",value.sessions)) } }
    fun saveClock(clock: FocusClock, boot: Int) {
        // Timer checkpoint and credited history are committed atomically to avoid double credit.
        disk.edit().putString("logs",logsJson().toString()).putString("clock",JSONObject()
            .put("mode",clock.mode.name).put("phase",clock.phase.name).put("running",clock.running)
            .put("remaining",clock.remainingMs).put("elapsed",clock.elapsedMs).put("last",clock.lastTickMs)
            .put("round",clock.round).put("boot",boot).toString()).apply()
    }
    fun readClock(boot: Int): FocusClock = runCatching {
        val c = obj("clock")
        if (!c.has("mode")) return@runCatching FocusClock(remainingMs = _prefs.value.focusMinutes * 60_000L)
        FocusClock(TimerMode.valueOf(c.getString("mode")),Phase.valueOf(c.getString("phase")),
            c.optBoolean("running") && c.optInt("boot",-1) == boot,c.optLong("remaining"),
            c.optLong("elapsed"),c.optLong("last"),c.optInt("round"))
    }.getOrDefault(FocusClock())
    private fun song(o: JSONObject) = Song(o.getString("id"),o.getString("title"),o.getString("artist"),
        o.getString("uri"),o.optString("cover"),o.optBoolean("builtin"),o.optString("platform"),o.optString("lyrics"))
    private fun readLibrary(): Library {
        val builtins = JSONArray(context.assets.open("library.json").bufferedReader().use { it.readText() }).objects().map(::song)
        val imported = arr("songs").objects().map(::song).filter { it.online || File(Uri.parse(it.uri).path ?: "").exists() }
        val favorites = arr("favorites").strings().toSet()
        val playlists = arr("playlists").objects().map { Playlist(it.getString("id"),it.getString("name"),it.getJSONArray("songs").strings()) }
        return Library(builtins + imported,favorites,playlists)
    }
    @Synchronized private fun saveLibrary(l: Library) {
        _library.value = l
        disk.edit().putString("songs",JSONArray().apply { l.songs.filterNot { it.builtin }.forEach {
            put(JSONObject().put("id",it.id).put("title",it.title).put("artist",it.artist).put("uri",it.uri).put("cover",it.cover)
                .put("platform",it.platform).put("lyrics",it.lyrics)) } }.toString())
            .putString("favorites",JSONArray(l.favorites.toList()).toString())
            .putString("playlists",JSONArray().apply { l.playlists.forEach {
                put(JSONObject().put("id",it.id).put("name",it.name).put("songs",JSONArray(it.songIds))) } }.toString()).apply()
    }
    @Synchronized fun toggleFavorite(id: String) {
        val l = _library.value
        saveLibrary(l.copy(favorites = if (id in l.favorites) l.favorites - id else l.favorites + id))
    }
    @Synchronized fun savePlaylist(name: String, ids: List<String>, existingId: String? = null) {
        val l = _library.value
        val playlist = Playlist(existingId ?: UUID.randomUUID().toString(),name.trim().take(40),ids.distinct())
        saveLibrary(l.copy(playlists = l.playlists.filterNot { it.id == existingId } + playlist))
    }
    @Synchronized fun deletePlaylist(id: String) = saveLibrary(_library.value.let { it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) })
    @Synchronized fun removeSong(id: String) {
        val l = _library.value
        val s = l.songs.find { it.id == id && !it.builtin && !it.online } ?: return
        File(Uri.parse(s.uri).path!!).delete()
        saveLibrary(l.copy(songs = l.songs.filterNot { it.id == id },favorites = l.favorites - id,
            playlists = l.playlists.map { it.copy(songIds = it.songIds - id) }))
    }
    /** Copy picked content into private storage: no lasting provider/network dependency. */
    fun importSong(uri: Uri): Song {
        var filename = "导入的音乐"
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
            if (it.moveToFirst()) filename = it.getString(0) ?: filename
        }
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir,"music").apply { mkdirs() }
        val file = File(directory,"$id.audio")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: error("无法读取文件")
            val retriever = MediaMetadataRetriever()
            var title = filename.substringBeforeLast('.')
            var artist = "本地音乐"
            try {
                retriever.setDataSource(file.absolutePath)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: artist
                check((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0) { "不是可播放的音频文件" }
            } finally { retriever.release() }
            val result = Song(id,title,artist,Uri.fromFile(file).toString())
            synchronized(this) { saveLibrary(_library.value.copy(songs = _library.value.songs + result)) }
            return result
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun saveQueue(ids: List<String>, index: Int, position: Long, repeat: Int, shuffle: Boolean, volume: Float = 1f) {
        disk.edit().putString("queue",JSONObject().put("ids",JSONArray(ids)).put("index",index)
            .put("position",position).put("repeat",repeat).put("shuffle",shuffle).put("volume",volume).toString()).apply()
    }
    fun readQueue() = obj("queue")
    fun readMusicConfig(): MusicConfig {
        val o = obj("network_music")
        return MusicConfig(o.optString("api",DEFAULT_MUSIC_API),o.optString("platform","netease"),
            o.optString("id",DEFAULT_MUSIC_PLAYLIST),o.optString("bitrate","2000"),o.optString("name","Study With Miku 默认歌单"))
    }
    fun readOnlineIds() = arr("network_ids").strings()
    @Synchronized fun cacheOnline(config: MusicConfig, songs: List<Song>) {
        val ids = songs.map { it.id }.toSet()
        val l = _library.value
        saveLibrary(l.copy(songs = l.songs.filterNot { it.id in ids } + songs))
        disk.edit().putString("network_music",JSONObject().put("api",config.api).put("platform",config.platform)
            .put("id",config.id).put("bitrate",config.bitrate).put("name",config.name).toString())
            .putString("network_ids",JSONArray(songs.map { it.id }).toString()).apply()
    }
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings() = (0 until length()).map { getString(it) }
