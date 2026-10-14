package io.github.chorusfruit233.studywithmiku

import org.json.JSONArray
import org.json.JSONObject

/** Immutable playlist membership; a song may also appear in other playlists or qualities. */
data class OfflinePlaylist(val config: MusicConfig,val songs: List<Song>) {
    val key get() = key(config)
    fun toJson() = JSONObject().put("config",JSONObject().put("api",config.api).put("platform",config.platform)
        .put("id",config.id).put("bitrate",config.bitrate).put("name",config.name))
        .put("songs",JSONArray(songs.map { song -> JSONObject().put("id",song.id).put("title",song.title)
            .put("artist",song.artist).put("uri",song.uri).put("cover",song.cover).put("builtin",song.builtin)
            .put("platform",song.platform).put("lyrics",song.lyrics).put("playlistId",song.playlistId)
            .put("playlistIndex",song.playlistIndex).put("playlistName",song.playlistName)
            .put("publicCover",song.publicCover).put("webTitle",song.webTitle).put("audioCacheKey",song.audioCacheKey) }))
    companion object {
        fun key(config: MusicConfig) = "${config.api.trim()}|${config.platform}|${config.id}|${config.bitrate}"
        fun fromJson(json: JSONObject): OfflinePlaylist {
            val c = json.getJSONObject("config")
            val config = MusicConfig(c.getString("api"),c.getString("platform"),c.getString("id"),c.getString("bitrate"),c.getString("name"))
            val array = json.getJSONArray("songs")
            val songs = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Song(o.getString("id"),o.getString("title"),o.getString("artist"),o.getString("uri"),o.optString("cover"),
                    o.optBoolean("builtin"),o.optString("platform"),o.optString("lyrics"),o.optString("playlistId"),
                    o.optInt("playlistIndex",-1),o.optString("playlistName"),o.optString("publicCover"),o.optString("webTitle"),o.optString("audioCacheKey"))
            }
            return OfflinePlaylist(config,songs)
        }
    }
}

enum class AudioCacheStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETE, FAILED, REMOVING }
data class CachedAudio(val status: AudioCacheStatus,val bytes: Long,val percent: Float)
data class AudioCacheState(val songs: Map<String,CachedAudio> = emptyMap(),val playlists: List<OfflinePlaylist> = emptyList(),
    val bytes: Long = 0,val paused: Boolean = false,val waitingForNetwork: Boolean = false,val initialized: Boolean = false) {
    fun ready(song: Song) = song.builtin || !song.online || songs[song.cacheKey]?.status == AudioCacheStatus.COMPLETE
    fun readySongs(playlist: OfflinePlaylist) = playlist.songs.filter(::ready)
    val pending get() = songs.values.any { it.status in listOf(AudioCacheStatus.QUEUED,AudioCacheStatus.DOWNLOADING,AudioCacheStatus.PAUSED) }
}
