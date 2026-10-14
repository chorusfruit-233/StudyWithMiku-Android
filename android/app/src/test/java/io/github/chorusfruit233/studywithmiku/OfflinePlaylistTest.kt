package io.github.chorusfruit233.studywithmiku

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfflinePlaylistTest {
    private val config = MusicConfig(id = "42",name = "Fixture")
    private fun song(uri: String = "https://example.test/audio?token=first",config: MusicConfig = this.config) =
        MusicProtocol.songs("""[{"id":"track-1","name":"Track","author":"Artist","url":"$uri"}]""",config).single()
    @Test fun expiringUrlsAndPlaylistMembershipReuseAudioButQualityAndProviderDoNot() {
        val first = song()
        assertEquals(first.cacheKey,song("https://cdn.example.test/audio?token=renewed",config.copy(id = "99")).cacheKey)
        assertNotEquals(first.cacheKey,song(config = config.copy(bitrate = "320")).cacheKey)
        assertNotEquals(first.cacheKey,song(config = config.copy(api = "https://another.test/meting/")).cacheKey)
        assertNotEquals(first.cacheKey,song(config = config.copy(platform = "tencent")).cacheKey)
    }
    @Test fun playlistRoundTripPreservesMembershipQualityAndCompleteSongMetadata() {
        val list = OfflinePlaylist(config,listOf(song().copy(lyrics = "[00:01]line",publicCover = "cover",webTitle = "web"),
            Song("builtin","Local","Artist","asset:///local.mp3",builtin = true)))
        assertEquals(list,OfflinePlaylist.fromJson(JSONObject(list.toJson().toString())))
        assertNotEquals(list.key,OfflinePlaylist.key(config.copy(bitrate = "320")))
    }
    @Test fun offlineQueueExcludesPartialFailedAndMissingAudio() {
        val songs = (1..5).map { song().copy(id = "track-$it",audioCacheKey = "cache-$it") } +
            Song("builtin","Local","Artist","asset:///local.mp3",builtin = true)
        val state = AudioCacheState(songs = mapOf(
            "cache-1" to CachedAudio(AudioCacheStatus.COMPLETE,100,100f),
            "cache-2" to CachedAudio(AudioCacheStatus.DOWNLOADING,50,50f),
            "cache-3" to CachedAudio(AudioCacheStatus.FAILED,100,100f),
            "cache-4" to CachedAudio(AudioCacheStatus.PAUSED,20,20f)))
        assertEquals(listOf("track-1","builtin"),state.readySongs(OfflinePlaylist(config,songs)).map { it.id })
        assertTrue(state.pending)
    }
}
