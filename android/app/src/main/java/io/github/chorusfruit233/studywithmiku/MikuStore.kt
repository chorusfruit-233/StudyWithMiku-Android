package io.github.chorusfruit233.studywithmiku

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

class MikuStore(private val context: Context) : StudySyncStore {
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
    private val _countdowns = MutableStateFlow(arr("countdowns").objects().mapNotNull {
        val date = it.optString("date")
        if (StudyProtocol.validDate(date)) CountdownDay(it.optString("id"),it.optString("title"),date) else null
    })
    val countdowns = _countdowns.asStateFlow()
    private val _revision = MutableStateFlow(0L)
    override val revision = _revision.asStateFlow()
    private var importingStudy = false
    fun markLearningChanged() { if (!importingStudy) _revision.value++ }
    override val studyOwner get() = disk.getString("study_owner","") ?: ""
    override var autoSync: Boolean
        get() = disk.getBoolean("auto_sync",true)
        set(value) { disk.edit().putBoolean("auto_sync",value).apply(); markLearningChanged() }

    private fun readPreferences(): Preferences {
        val p = obj("preferences")
        return Preferences(p.optInt("focus",25).coerceIn(1,180), p.optInt("break",5).coerceIn(1,100),
            p.optInt("long",15).coerceIn(1,60), p.optInt("countdown",30).coerceIn(1,180),
            p.optInt("goal",120).coerceIn(15,600),p.optInt("scene",0).coerceIn(0,2),
            p.optInt("theme",0).coerceIn(0,2),p.optBoolean("dynamic",false),
            p.optBoolean("animate",true),p.optBoolean("screen",false),p.optBoolean("sound",true),
            showHitokoto = p.optBoolean("hitokoto",true),pauseMusicDuringBreak = p.optBoolean("pauseMusic",true),
            pomodoroCount = p.optInt("cycles",4).coerceIn(1,20),notificationVolume = p.optDouble("notificationVolume",0.6).toFloat().coerceIn(0f,1f),
            autoAdvance = p.optBoolean("autoAdvance",true),hidePomodoroOnIdle = p.optBoolean("hideIdle",false),crossfade = p.optBoolean("crossfade",true),
            focusSeconds = p.optInt("focusSeconds",p.optInt("focus",25)*60).coerceIn(6,6000),
            breakSeconds = p.optInt("breakSeconds",p.optInt("break",5)*60).coerceIn(6,6000),
            countdownSeconds = p.optInt("countdownSeconds",p.optInt("countdown",30)*60).coerceIn(6,36000),
            longBreakSeconds = p.optInt("longSeconds",p.optInt("long",15)*60).coerceIn(6,12000),
            timerMode = p.optString("timerMode","pomodoro"),overlayOpacity = p.optDouble("overlay",0.3).toFloat().coerceIn(0f,1f))
    }
    fun updatePreferences(p: Preferences) {
        _prefs.value = p
        disk.edit().putString("preferences",JSONObject().put("focus",p.focusMinutes).put("break",p.breakMinutes)
            .put("long",p.longBreakMinutes).put("countdown",p.countdownMinutes).put("goal",p.dailyGoalMinutes)
            .put("scene",p.scene).put("theme",p.theme).put("dynamic",p.dynamicColor)
            .put("animate",p.animateScene).put("screen",p.keepScreenOn).put("sound",p.completionSound)
            .put("hitokoto",p.showHitokoto).put("pauseMusic",p.pauseMusicDuringBreak).put("cycles",p.pomodoroCount)
            .put("notificationVolume",p.notificationVolume).put("autoAdvance",p.autoAdvance).put("hideIdle",p.hidePomodoroOnIdle)
            .put("crossfade",p.crossfade).put("focusSeconds",p.focusSeconds).put("breakSeconds",p.breakSeconds)
            .put("countdownSeconds",p.countdownSeconds).put("longSeconds",p.longBreakSeconds).put("timerMode",p.timerMode).put("overlay",p.overlayOpacity).toString()).apply()
        markLearningChanged()
    }
    private fun readTodos() = arr("todos").objects().map {
        Todo(it.getString("id"),it.getString("text"),it.optString("date"),it.optBoolean("done"),it.optBoolean("pinned"),it.optString("color","#39c5bb"))
    }
    private fun saveTodos(list: List<Todo>) {
        _todos.value = list
        disk.edit().putString("todos",JSONArray().apply { list.forEach { put(JSONObject()
            .put("id",it.id).put("text",it.text).put("date",it.date).put("done",it.done).put("pinned",it.pinned).put("color",it.color)) } }.toString()).apply()
        markLearningChanged()
    }
    fun addTodo(text: String, date: LocalDate? = null) {
        if (text.isNotBlank() && _todos.value.count { it.date == (date?.toString() ?: "") } < 200) saveTodos(_todos.value + Todo(System.currentTimeMillis().toString(),text.trim().take(200),date?.toString() ?: ""))
    }
    fun pinTodo(id: String) = saveTodos(_todos.value.map { it.copy(pinned = if (it.id == id) !it.pinned else false) })
    fun toggleTodo(id: String) = saveTodos(_todos.value.map { if (it.id == id) it.copy(done = !it.done) else it })
    fun editTodo(id: String,text: String,color: String? = null) {
        if (text.isNotBlank()) saveTodos(_todos.value.map { if (it.id == id) it.copy(text = text.trim().take(200),color = color ?: it.color) else it })
    }
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
        val before = _logs.value.values.sumOf { it.millis } / 60_000
        _logs.value = updated
        if (completed || updated.values.sumOf { it.millis } / 60_000 != before) markLearningChanged()
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
        if (!c.has("mode")) {
            val mode = runCatching { TimerMode.valueOf(_prefs.value.timerMode.uppercase()) }.getOrDefault(TimerMode.POMODORO)
            return@runCatching FocusClock(mode = mode,remainingMs = FocusClock.duration(mode,Phase.FOCUS,_prefs.value))
        }
        FocusClock(TimerMode.valueOf(c.getString("mode")),Phase.valueOf(c.getString("phase")),
            c.optBoolean("running") && c.optInt("boot",-1) == boot,c.optLong("remaining"),
            c.optLong("elapsed"),c.optLong("last"),c.optInt("round"))
    }.getOrDefault(FocusClock())
    private fun song(o: JSONObject) = Song(o.getString("id"),o.getString("title"),o.getString("artist"),
        o.getString("uri"),o.optString("cover"),o.optBoolean("builtin"),o.optString("platform"),o.optString("lyrics"),
        o.optString("playlistId"),o.optInt("playlistIndex",-1),o.optString("playlistName"),o.optString("publicCover"),o.optString("webTitle"))
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
                .put("platform",it.platform).put("lyrics",it.lyrics).put("playlistId",it.playlistId).put("playlistIndex",it.playlistIndex)
                .put("playlistName",it.playlistName).put("publicCover",it.publicCover).put("webTitle",it.webTitle)) } }.toString())
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
    fun addCountdown(title: String,date: String): Boolean {
        if (title.isBlank() || !StudyProtocol.validDate(date) || _countdowns.value.size >= 20) return false
        saveCountdowns(_countdowns.value + CountdownDay(System.currentTimeMillis().toString(),title.trim().take(40),date))
        return true
    }
    fun deleteCountdown(id: String) = saveCountdowns(_countdowns.value.filterNot { it.id == id })
    private fun saveCountdowns(list: List<CountdownDay>) {
        _countdowns.value = list.take(20)
        disk.edit().putString("countdowns",JSONArray().apply { _countdowns.value.forEach {
            put(JSONObject().put("id",StudyProtocol.wireId(it.id)).put("title",it.title).put("date",it.date))
        } }.toString()).apply()
        markLearningChanged()
    }
    fun studyTotals(): DayLog {
        val base = obj("study_baseline")
        return DayLog(_logs.value.values.sumOf { it.millis } + base.optLong("millis"),
            _logs.value.values.sumOf { it.sessions } + base.optInt("sessions"))
    }
    @Synchronized override fun studySnapshot(): CloudStudy {
        val shadow = obj("study_shadow")
        val data = shadow.optJSONObject("data")?.let(StudyProtocol::deepCopy) ?: JSONObject()
        val calendar = shadow.optJSONObject("calendar")?.let(StudyProtocol::deepCopy) ?: JSONObject()
        val total = studyTotals()
        val today = LocalDate.now()
        val day = _logs.value[today.toString()] ?: DayLog()
        data.put("stats",(data.optJSONObject("stats") ?: JSONObject()).put("totalStudyTime",total.millis / 1000.0)
            .put("totalPomodoros",total.sessions).put("todayStudyTime",day.millis / 1000.0).put("todayPomodoros",day.sessions).put("lastDate",StudyProtocol.jsDate(today)))
        fun original(array: JSONArray?,id: String): JSONObject = array?.objects()?.firstOrNull { it.optString("id") == id }
            ?.let(StudyProtocol::deepCopy) ?: JSONObject()
        fun todoJson(t: Todo,plan: Boolean) = original(if (plan) calendar.optJSONObject("plans")?.optJSONArray(t.date) else data.optJSONArray("todos"),t.id)
            .put("id",StudyProtocol.wireId(t.id)).put("text",t.text).apply {
            if (plan) { put("done",t.done); put("color",t.color) } else { put("completed",t.done); put("pinned",t.pinned) }
        }
        data.put("todos",JSONArray().apply { _todos.value.filter { it.date.isEmpty() }.forEach { put(todoJson(it,false)) } })
        val p = _prefs.value
        data.put("settings",(data.optJSONObject("settings") ?: JSONObject()).put("focusDuration",p.focusSeconds / 60.0)
            .put("breakDuration",p.breakSeconds / 60.0).put("countdownDuration",p.countdownSeconds / 60.0).put("timerMode",p.timerMode)
            .put("pomodoroCount",p.pomodoroCount).put("notificationVolume",p.notificationVolume)
            .put("pauseMusicDuringBreak",p.pauseMusicDuringBreak).put("showHitokoto",p.showHitokoto).put("hidePomodoroOnIdle",p.hidePomodoroOnIdle)
            .put("countdowns",JSONArray().apply { _countdowns.value.forEach { put(original(data.optJSONObject("settings")?.optJSONArray("countdowns"),it.id)
                .put("id",StudyProtocol.wireId(it.id)).put("title",it.title).put("date",it.date)) } }))
        calendar.put("dailyLog",JSONObject().apply { _logs.value.forEach { (date,log) ->
            put(date,(calendar.optJSONObject("dailyLog")?.optJSONObject(date)?.let(StudyProtocol::deepCopy) ?: JSONObject())
                .put("studyTime",log.millis/1000.0).put("pomodoros",log.sessions)) } })
        calendar.put("plans",JSONObject().apply { _todos.value.filter { StudyProtocol.validDate(it.date) }.groupBy { it.date }.forEach { (date,list) ->
            put(date,JSONArray().apply { list.forEach { put(todoJson(it,true)) } }) } })
        return CloudStudy(data,calendar)
    }
    private fun savedStudy(key: String): CloudStudy? {
        if (!disk.contains(key)) return null
        val saved = obj(key)
        return CloudStudy(saved.optJSONObject("data") ?: JSONObject(),saved.optJSONObject("calendar") ?: JSONObject())
    }
    override fun lastCloudStudy() = savedStudy("study_shadow")
    override fun lastAppliedStudy() = savedStudy("study_applied")
    override val pendingStudyUpload get() = disk.getString("study_pending_upload","false") == "true"
    override fun rememberAppliedStudy(study: CloudStudy,pendingUpload: Boolean) {
        disk.edit().putString("study_applied",JSONObject().put("data",study.data).put("calendar",study.calendar).toString())
            .putString("study_pending_upload",pendingUpload.toString()).apply()
    }
    override fun rememberCloud(study: CloudStudy) {
        disk.edit().putString("study_shadow",JSONObject().put("data",study.data).put("calendar",study.calendar).toString()).apply()
    }
    @Synchronized override fun applyStudy(study: CloudStudy) {
        importingStudy = true
        try {
            val logs = mutableMapOf<String,DayLog>()
            study.calendar.optJSONObject("dailyLog")?.let { o -> o.keys().forEach { date ->
                if (StudyProtocol.validDate(date)) o.optJSONObject(date)?.let { logs[date] = DayLog(StudyProtocol.studyMillis(it.optDouble("studyTime").coerceAtLeast(0.0)),it.optInt("pomodoros").coerceAtLeast(0)) }
            } }
            val stats = study.data.optJSONObject("stats") ?: JSONObject()
            val today = LocalDate.now()
            if (stats.optString("lastDate") == StudyProtocol.jsDate(today)) {
                val old = logs[today.toString()] ?: DayLog()
                logs[today.toString()] = DayLog(maxOf(old.millis,StudyProtocol.studyMillis(stats.optDouble("todayStudyTime").coerceAtLeast(0.0))),maxOf(old.sessions,stats.optInt("todayPomodoros")))
            }
            _logs.value = logs
            disk.edit().putString("logs",logsJson().toString()).putString("study_baseline",JSONObject()
                .put("millis",(StudyProtocol.studyMillis(stats.optDouble("totalStudyTime"))-logs.values.sumOf { it.millis }).coerceAtLeast(0))
                .put("sessions",(stats.optInt("totalPomodoros")-logs.values.sumOf { it.sessions }).coerceAtLeast(0)).toString()).apply()
            val todos = mutableListOf<Todo>()
            study.data.optJSONArray("todos")?.objects()?.forEach { o ->
                if (o.optString("id").isNotBlank()) todos.add(Todo(o.optString("id"),o.optString("text"),"",o.optBoolean("completed"),o.optBoolean("pinned")))
            }
            study.calendar.optJSONObject("plans")?.let { o -> o.keys().forEach { date -> if (StudyProtocol.validDate(date)) o.optJSONArray(date)?.objects()?.forEach { plan ->
                todos.add(Todo(plan.optString("id"),plan.optString("text"),date,plan.optBoolean("done"),color = plan.optString("color","#39c5bb")))
            } } }
            saveTodos(todos)
            study.data.optJSONObject("settings")?.let { s ->
                val p = _prefs.value
                val focus = StudyProtocol.durationSeconds(s.optDouble("focusDuration",p.focusSeconds/60.0)).coerceIn(6,6000)
                val rest = StudyProtocol.durationSeconds(s.optDouble("breakDuration",p.breakSeconds/60.0)).coerceIn(6,6000)
                val countdown = StudyProtocol.durationSeconds(s.optDouble("countdownDuration",p.countdownSeconds/60.0)).coerceIn(6,36000)
                updatePreferences(p.copy(focusMinutes = (focus+59)/60,breakMinutes = (rest+59)/60,countdownMinutes = (countdown+59)/60,
                    focusSeconds = focus,breakSeconds = rest,countdownSeconds = countdown,longBreakSeconds = rest*2,longBreakMinutes = (rest*2+59)/60,
                    timerMode = s.optString("timerMode",p.timerMode).takeIf { it in listOf("pomodoro","countdown","stopwatch") } ?: "pomodoro",
                    pomodoroCount = s.optInt("pomodoroCount",4).coerceIn(1,20),notificationVolume = s.optDouble("notificationVolume",0.6).toFloat().coerceIn(0f,1f),
                    pauseMusicDuringBreak = s.optBoolean("pauseMusicDuringBreak",true),showHitokoto = s.optBoolean("showHitokoto",true),hidePomodoroOnIdle = s.optBoolean("hidePomodoroOnIdle",false)))
                saveCountdowns(s.optJSONArray("countdowns")?.objects()?.mapNotNull { o ->
                    val date = o.optString("date"); if (StudyProtocol.validDate(date)) CountdownDay(o.optString("id"),o.optString("title"),date) else null
                } ?: emptyList())
            }
            rememberCloud(study)
            disk.edit().remove("clock").apply()
        } finally { importingStudy = false }
        _revision.value++
    }
    @Synchronized override fun switchStudyOwner(id: String) {
        if (id == studyOwner) return
        val keys = listOf("preferences","todos","logs","clock","countdowns","study_baseline","study_shadow","study_applied","study_pending_upload")
        val backup = JSONObject()
        keys.forEach { key -> disk.getString(key,null)?.let { backup.put(key,it) } }
        disk.edit().putString("study_profile_${studyOwner.ifBlank { "guest" }}",backup.toString()).commit()
        val target = obj("study_profile_${id.ifBlank { "guest" }}")
        disk.edit().apply {
            keys.forEach { key -> if (target.has(key)) putString(key,target.getString(key)) else remove(key) }
            putString("study_owner",id)
        }.commit()
        _prefs.value = readPreferences(); _todos.value = readTodos(); _logs.value = readLogs()
        _countdowns.value = arr("countdowns").objects().mapNotNull { o ->
            val date = o.optString("date"); if (StudyProtocol.validDate(date)) CountdownDay(o.optString("id"),o.optString("title"),date) else null
        }
        _revision.value++
    }
}
fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray.strings() = (0 until length()).map { getString(it) }
