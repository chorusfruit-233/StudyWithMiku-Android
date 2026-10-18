@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package io.github.chorusfruit233.studywithmiku

import android.content.ComponentName
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

class PlaybackCrossfadeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun tracks(directory: File): List<MediaItem> = (0..2).map { index ->
        val samples = 22_050*20
        val b = ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(22_050).putInt(44_100).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples*2)
        repeat(samples) { i -> b.putShort((kotlin.math.sin(i*2.0*Math.PI*(220+index*110)/22_050)*800).toInt().toShort()) }
        val file = File(directory,"track-$index.wav").apply { writeBytes(b.array()) }
        MediaItem.Builder().setMediaId("crossfade-fixture-$index").setUri(Uri.fromFile(file)).build()
    }
    private suspend fun until(check: () -> Boolean) = withTimeout(15_000) {
        while (!withContext(Dispatchers.Main) { check() }) delay(25)
    }
    private class Fixture(val main: ExoPlayer,val preview: ExoPlayer,val mixer: PlaybackCrossfade,val job: Job)
    private fun fixture(test: suspend (Fixture,List<MediaItem>) -> Unit) = runBlocking {
        compose.setContent { Text("Native crossfade fixture") }
        val directory = File(context.cacheDir,"crossfade_${UUID.randomUUID()}").apply { mkdirs() }
        val tracks = tracks(directory)
        val fixture = withContext(Dispatchers.Main) {
            val main = ExoPlayer.Builder(context).build(); val preview = ExoPlayer.Builder(context).build()
            val mixer = PlaybackCrossfade(main,preview,{ true },{ true }) { a,b -> main.volume = .4f*a; preview.volume = .4f*b }
            val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            val job = scope.launch { while (isActive) { mixer.tick(); delay(20) } }
            main.setMediaItems(tracks); main.prepare(); main.play()
            Fixture(main,preview,mixer,job)
        }
        try {
            until { fixture.main.isPlaying }
            test(fixture,tracks)
        } finally {
            withContext(Dispatchers.Main) { fixture.job.cancelAndJoin(); fixture.mixer.close(); fixture.preview.release(); fixture.main.release() }
            directory.deleteRecursively()
        }
    }
    @Test fun manualSwitchOverlapsDecodersAndHandsOffWithoutResettingIncomingPosition() = fixture { f,_ ->
        withContext(Dispatchers.Main) { assertTrue(f.mixer.switchTo(1)) }
        until { f.main.isPlaying && f.preview.isPlaying && f.main.volume in 0.01f..0.39f && f.preview.volume in 0.01f..0.39f }
        withContext(Dispatchers.Main) { assertEquals(.4f,f.main.volume+f.preview.volume,.005f) }
        until { f.main.currentMediaItemIndex == 1 && f.main.isPlaying && !f.preview.isPlaying && f.main.volume >= .399f }
        withContext(Dispatchers.Main) { assertTrue(f.main.currentPosition >= 1400); assertEquals(.4f,f.main.volume,.001f) }
    }
    @Test fun naturalAdvanceKeepsIncomingPositionAndFinishesSecondaryDecoder() = fixture { f,_ ->
        withContext(Dispatchers.Main) { f.main.seekTo(13_900) }
        until { f.preview.isPlaying && f.main.volume < .39f }
        until { f.main.currentMediaItemIndex == 1 && f.main.isPlaying && !f.preview.isPlaying && f.main.volume >= .399f }
        withContext(Dispatchers.Main) { assertTrue(f.main.currentPosition >= 5500); assertEquals(.4f,f.main.volume,.001f) }
    }
    @Test fun rapidNextThenPauseCommitsLatestChoiceAndStopsBothVoices() = fixture { f,_ ->
        withContext(Dispatchers.Main) {
            assertTrue(f.mixer.switchTo(f.mixer.adjacent(true)))
            assertTrue(f.mixer.switchTo(f.mixer.adjacent(true)))
            f.main.pause()
        }
        until { !f.main.isPlaying && !f.preview.isPlaying && f.preview.mediaItemCount == 0 }
        withContext(Dispatchers.Main) { assertEquals(2,f.main.currentMediaItemIndex); assertEquals(.4f,f.main.volume,.001f) }
    }
    @Test fun productionMediaControllerKeepsUserVolumeAndMuteAcrossQueueSwitches() = runBlocking {
        compose.setContent { Text("Media session crossfade fixture") }
        val app = context.applicationContext as MikuApplication
        val previous = app.store.prefs.value
        val saved = app.store.readQueue()
        val directory = File(context.cacheDir,"session_crossfade_${UUID.randomUUID()}").apply { mkdirs() }
        val tracks = tracks(directory)
        val future = withContext(Dispatchers.Main) {
            app.store.updatePreferences(previous.copy(crossfade = true))
            MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
        }
        val player = withContext(Dispatchers.IO) { future.get(15,TimeUnit.SECONDS) }
        try {
            withContext(Dispatchers.Main) { player.setMediaItems(tracks,0,0); player.volume = .35f; player.prepare(); player.play() }
            until { player.isPlaying }
            withContext(Dispatchers.Main) { player.seekToNextMediaItem() }
            delay(700)
            withContext(Dispatchers.Main) {
                assertEquals(.35f,player.volume,.001f)
                assertEquals(.35,app.store.readQueue().getDouble("volume"),.001)
                player.volume = 0f
            }
            until { player.currentMediaItemIndex == 1 && player.isPlaying }
            withContext(Dispatchers.Main) { player.setMediaItems(tracks,2,0); player.prepare(); player.play() }
            until { player.currentMediaItemIndex == 2 && player.isPlaying }
            withContext(Dispatchers.Main) {
                assertEquals(0f,player.volume,.001f)
                assertEquals(0.0,app.store.readQueue().getDouble("volume"),.001)
                player.pause()
            }
        } finally {
            withContext(Dispatchers.Main) {
                player.pause(); player.clearMediaItems(); player.volume = saved.finiteDouble("volume",1.0).toFloat()
                MediaController.releaseFuture(future); app.store.updatePreferences(previous)
                app.store.saveQueue(saved.optJSONArray("ids")?.strings() ?: emptyList(),saved.optInt("index"),saved.optLong("position"),
                    saved.optInt("repeat",Player.REPEAT_MODE_ALL),saved.optBoolean("shuffle"),saved.finiteDouble("volume",1.0).toFloat())
            }
            directory.deleteRecursively()
        }
    }
}
