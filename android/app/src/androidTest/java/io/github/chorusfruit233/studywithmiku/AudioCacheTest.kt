@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

class AudioCacheTest {
    @get:Rule val compose = createComposeRule()
    @Test fun productionDownloadServiceAndMediaControllerPlayOffline() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MikuApplication
        val server = server(wave())
        val track = song(server.url("/production.wav").toString())
        val playlist = OfflinePlaylist(MusicConfig(id = "42",name = "Service fixture"),listOf(track))
        lateinit var music: MusicConnection
        compose.setContent { Text("Offline download service fixture") }
        compose.runOnIdle { music = MusicConnection(app) }
        try {
            compose.waitUntil(15_000) { music.state.value.connected && app.audioCache.state.value.initialized }
            compose.runOnIdle { app.audioCache.download(playlist) }
            compose.waitUntil(30_000) { app.audioCache.state.value.ready(track) }
            assertEquals(playlist,app.store.readOfflinePlaylists().single())
            server.shutdown()
            compose.runOnIdle { music.play(listOf(track)) }
            compose.waitUntil(15_000) { music.state.value.playing }
            compose.runOnIdle { music.seek(2000) }
            compose.waitUntil(5000) { music.state.value.position >= 2000 }
            assertNull(music.state.value.error)
            assertEquals(1,server.requestCount)
        } finally {
            compose.runOnIdle { if (music.state.value.playing) music.toggle(); music.close(); app.audioCache.clear() }
            compose.waitUntil(15_000) { app.audioCache.state.value.songs.isEmpty() && app.audioCache.state.value.bytes == 0L }
            runCatching { server.shutdown() }
        }
    }
    private fun wave(seconds: Int = 4): ByteArray {
        val samples = 22_050 * seconds
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(22_050).putInt(44_100).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { i -> b.putShort((kotlin.math.sin(i * 2.0 * Math.PI * 440 / 22_050) * 800).toInt().toShort()) }
        return b.array()
    }
    private fun server(bytes: ByteArray,slow: Boolean = false,omitType: Boolean = false) = MockWebServer().apply {
        val requests = java.util.concurrent.atomic.AtomicInteger()
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                val response = MockResponse().setHeader("Accept-Ranges","bytes")
                    .setBody(Buffer().write(bytes,start,bytes.size-start))
                if (!omitType) response.setHeader("Content-Type","audio/wav")
                if (start > 0) response.setResponseCode(206).setHeader("Content-Range","bytes $start-${bytes.lastIndex}/${bytes.size}")
                if (slow && requests.incrementAndGet() == 1) response.throttleBody(8192,100,TimeUnit.MILLISECONDS)
                return response
            }
        }
        start()
    }
    private suspend fun await(cache: AudioCache,test: (AudioCacheState) -> Boolean) = withTimeout(30_000) { cache.state.first(test) }
    private fun fixture(test: suspend (Context,MikuStore,File,String) -> Unit) = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audio_cache_fixture_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        val directory = File(target.filesDir,name)
        try { test(context,MikuStore(context),directory,name) }
        finally { directory.deleteRecursively(); target.deleteSharedPreferences(name); target.deleteDatabase("${name}_exoplayer_internal.db") }
    }
    private fun song(uri: String) = Song("fixture-audio","Offline fixture","Fixture",uri,platform = "netease",audioCacheKey = "fixture-key")

    @Test fun completeAudioSurvivesRecreationAndPlaysWithServerGoneAndRenewedUrl() = fixture { context,store,directory,name ->
        val server = server(wave(),omitType = true)
        val track = song(server.url("/track.wav").toString())
        var cache = withContext(Dispatchers.Main) { AudioCache(context,store,directory,name) }
        try {
            await(cache) { it.initialized }
            withContext(Dispatchers.Main) { cache.manager.addDownload(AudioCache.request(track)); cache.manager.resumeDownloads() }
            val downloaded = await(cache) { it.ready(track) }
            assertTrue(downloaded.bytes > 0)
            withContext(Dispatchers.Main) { cache.release() }
            server.shutdown()
            cache = withContext(Dispatchers.Main) { AudioCache(context,store,directory,name) }
            await(cache) { it.initialized && it.ready(track) }
            val player = withContext(Dispatchers.Main) {
                ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(cache.playbackFactory)).build().apply {
                    setMediaItem(track.copy(uri = track.uri + "?renewed=true").mediaItem()); prepare(); play()
                }
            }
            try {
                withTimeout(10_000) { while (!withContext(Dispatchers.Main) { player.isPlaying }) {
                    withContext(Dispatchers.Main) { assertNull(player.playerError) }; delay(50)
                } }
                withContext(Dispatchers.Main) { assertTrue(player.duration >= 3900); player.seekTo(2500) }
                delay(150)
                withContext(Dispatchers.Main) { assertNull(player.playerError); assertTrue(player.currentPosition >= 2500) }
                assertEquals(1,server.requestCount)
            } finally { withContext(Dispatchers.Main) { player.release() } }
        } finally { withContext(Dispatchers.Main) { cache.release() }; runCatching { server.shutdown() } }
    }
    @Test fun interruptedDownloadIsNotOfflineReadyAndResumesFromPersistedBytes() = fixture { context,store,directory,name ->
        server(wave(64),slow = true).use { server ->
            val track = song(server.url("/slow.wav").toString())
            var cache = withContext(Dispatchers.Main) { AudioCache(context,store,directory,name) }
            try {
                await(cache) { it.initialized }
                withContext(Dispatchers.Main) { cache.manager.addDownload(AudioCache.request(track)); cache.manager.resumeDownloads() }
                await(cache) { it.bytes >= 256 * 1024 && !it.ready(track) }
                withContext(Dispatchers.Main) { cache.manager.pauseDownloads() }
                await(cache) { it.paused }
                withTimeout(5000) { while (withContext(Dispatchers.Main) { cache.manager.currentDownloads.any { it.state == Download.STATE_DOWNLOADING } }) delay(50) }
                assertFalse("Paused partial audio must not be offline ready",cache.state.value.ready(track))
                val before = cache.cache.getCachedBytes(track.cacheKey,0,Long.MAX_VALUE)
                assertTrue("Pause must commit some audio bytes; cached=$before",before > 0)
                withContext(Dispatchers.Main) { cache.release() }
                cache = withContext(Dispatchers.Main) { AudioCache(context,store,directory,name) }
                await(cache) { it.initialized }
                assertFalse("Reopened partial audio must not be offline ready",cache.state.value.ready(track))
                withContext(Dispatchers.Main) { cache.manager.resumeDownloads() }
                await(cache) { it.ready(track) }
                assertTrue("Resume must make a second request; requests=${server.requestCount}",server.requestCount >= 2)
                val requests = (0 until server.requestCount).mapNotNull { server.takeRequest(1,TimeUnit.SECONDS) }
                assertTrue("Resume should use saved bytes ($before); ranges=${requests.map { it.getHeader("Range") }}",
                    requests.drop(1).any { it.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull()?.let { start -> start > 0 } == true })
                withContext(Dispatchers.Main) { cache.manager.removeAllDownloads() }
                await(cache) { it.initialized && it.songs.isEmpty() && it.bytes == 0L }
                assertFalse(cache.state.value.ready(track))
            } finally { withContext(Dispatchers.Main) { cache.release() } }
        }
    }
    @Test fun errorResponseCannotBecomeAnOfflineSong() = fixture { context,store,directory,name ->
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{\"error\":\"unavailable\"}")); server.start()
            val track = song(server.url("/invalid").toString())
            val cache = withContext(Dispatchers.Main) { AudioCache(context,store,directory,name).also { it.manager.minRetryCount = 0 } }
            try {
                await(cache) { it.initialized }
                withContext(Dispatchers.Main) { cache.manager.addDownload(AudioCache.request(track)); cache.manager.resumeDownloads() }
                val failed = await(cache) { it.songs[track.cacheKey]?.status == AudioCacheStatus.FAILED }
                assertFalse(failed.ready(track)); assertEquals(0,failed.bytes)
            } finally { withContext(Dispatchers.Main) { cache.release() } }
        }
    }
}
