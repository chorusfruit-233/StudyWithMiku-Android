@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package io.github.chorusfruit233.studywithmiku

import android.content.Intent
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import android.os.SystemClock

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var preview: ExoPlayer
    private val app get() = application as MikuApplication
    private val store get() = app.store
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var userVolume = 1f
    private lateinit var crossfade: PlaybackCrossfade
    private var mainGain = 1f
    private var previewGain = 0f
    private var breakGain = 1f
    private var duckGain = 1f
    private var breakResume = false
    private var breakJob: Job? = null
    private var duckJob: Job? = null
    override fun onCreate() {
        super.onCreate()
        fun create() = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(app.audioCache.playbackFactory)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),false)
            setHandleAudioBecomingNoisy(true); setWakeMode(C.WAKE_MODE_NETWORK)
        }
        player = create().apply { setAudioAttributes(audioAttributes,true) }
        preview = create().apply { volume = 0f }
        crossfade = PlaybackCrossfade(player,preview,{ store.prefs.value.crossfade },{ breakGain >= 1f }) { main,second ->
            mainGain = main; previewGain = second; gains()
        }
        val publicPlayer = object : ForwardingPlayer(player) {
            override fun getVolume() = userVolume
            override fun setVolume(volume: Float) { userVolume = if (volume.isFinite()) volume.coerceIn(0f,1f) else userVolume; gains(); save(player) }
            override fun pause() { manualControl(); super.pause(); crossfade.cancel(finishManual = true) }
            override fun play() { manualControl(); super.play() }
            override fun setPlayWhenReady(playWhenReady: Boolean) {
                manualControl(); super.setPlayWhenReady(playWhenReady)
                if (!playWhenReady) crossfade.cancel(finishManual = true)
            }
            override fun seekTo(positionMs: Long) { crossfade.cancel(); super.seekTo(positionMs) }
            override fun seekForward() { crossfade.cancel(); super.seekForward() }
            override fun seekBack() { crossfade.cancel(); super.seekBack() }
            override fun seekTo(mediaItemIndex: Int,positionMs: Long) {
                manualControl()
                if (!crossfade.switchTo(mediaItemIndex,positionMs)) { crossfade.cancel(); super.seekTo(mediaItemIndex,positionMs) }
            }
            override fun seekToDefaultPosition() { crossfade.cancel(); super.seekToDefaultPosition() }
            override fun seekToDefaultPosition(mediaItemIndex: Int) = seekTo(mediaItemIndex,0)
            override fun seekToNextMediaItem() {
                val index = crossfade.adjacent(true)
                if (index != C.INDEX_UNSET) seekTo(index,0)
            }
            override fun seekToPreviousMediaItem() {
                val index = crossfade.adjacent(false)
                if (index != C.INDEX_UNSET) seekTo(index,0)
            }
            override fun seekToNext() = seekToNextMediaItem()
            override fun seekToPrevious() {
                if (player.currentPosition > player.maxSeekToPreviousPosition) seekTo(0) else seekToPreviousMediaItem()
            }
            override fun setMediaItems(mediaItems: List<MediaItem>,startIndex: Int,startPositionMs: Long) {
                manualControl()
                if (!crossfade.replace(mediaItems,startIndex,startPositionMs)) {
                    crossfade.cancel(); super.setMediaItems(mediaItems,startIndex,startPositionMs)
                }
            }
            override fun clearMediaItems() { crossfade.cancel(); super.clearMediaItems() }
            override fun stop() { crossfade.cancel(); super.stop() }
            override fun removeMediaItem(index: Int) { crossfade.cancel(); super.removeMediaItem(index) }
            override fun removeMediaItems(fromIndex: Int,toIndex: Int) { crossfade.cancel(); super.removeMediaItems(fromIndex,toIndex) }
            override fun replaceMediaItem(index: Int,mediaItem: MediaItem) { crossfade.cancel(); super.replaceMediaItem(index,mediaItem) }
        }
        session = MediaSession.Builder(this,publicPlayer).build()
        val saved = store.readQueue()
        userVolume = saved.finiteDouble("volume",1.0).toFloat().coerceIn(0f,1f); gains()
        val songs = store.library.value.songs.associateBy { it.id }
        val items = saved.optJSONArray("ids")?.strings()?.mapNotNull { songs[it]?.mediaItem() } ?: emptyList()
        if (items.isNotEmpty()) {
            player.setMediaItems(items,saved.optInt("index").coerceIn(0,items.lastIndex),saved.optLong("position").coerceAtLeast(0))
            player.repeatMode = saved.optInt("repeat",Player.REPEAT_MODE_ALL)
            player.shuffleModeEnabled = saved.optBoolean("shuffle"); player.prepare()
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return
                if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) || events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) crossfade.cancel(finishManual = true)
                save(player)
            }

        })
        scope.launch { while (isActive) { crossfade.tick(); delay(50) } }
        scope.launch {
            combine(app.timer.state,store.prefs) { clock,prefs -> (clock.mode == TimerMode.POMODORO && clock.phase != Phase.FOCUS) to prefs.pauseMusicDuringBreak }
                .distinctUntilChanged().collect { (rest,enabled) ->
                    breakJob?.cancel()
                    if (rest && enabled && player.playWhenReady) {
                        breakResume = true; crossfade.cancel(finishManual = true)
                        breakJob = launch {
                            fadeBreak(0f)
                            if (breakResume) { player.pause(); breakGain = 1f; gains() }
                        }
                    } else if (!rest || !enabled) {
                        if (breakResume) { breakResume = false; breakGain = 0f; player.play(); breakJob = launch { fadeBreak(1f) } }
                        else { breakGain = 1f; gains() }
                    }
                }
        }
        scope.launch { app.timer.completions.collect {
            duckJob?.cancel()
            duckJob = launch { duckGain = .3f; gains(); delay(3000); duckGain = 1f; gains() }
        } }
    }
    private fun manualControl() { breakResume = false; breakJob?.cancel(); breakGain = 1f; gains() }
    private suspend fun fadeBreak(target: Float) {
        val start = breakGain; val begun = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive) {
            val progress = ((SystemClock.elapsedRealtime()-begun)/2000f).coerceIn(0f,1f)
            breakGain = start+(target-start)*progress; gains()
            if (progress >= 1f) return
            delay(50)
        }
    }
    private fun gains() {
        player.volume = userVolume*mainGain*breakGain*duckGain
        preview.volume = userVolume*previewGain*breakGain*duckGain
    }
    private fun save(player: Player) {
        store.saveQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            player.currentMediaItemIndex.coerceAtLeast(0),player.currentPosition,player.repeatMode,player.shuffleModeEnabled,userVolume)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) { session?.player?.let { save(it); if (!it.playWhenReady) stopSelf() } }
    override fun onDestroy() {
        scope.cancel()
        session?.let { save(it.player); crossfade.close(); preview.release(); player.release(); it.release() }; session = null
        super.onDestroy()
    }
}
fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri)
    .setCustomCacheKey(if (online) cacheKey else null)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
        .setArtworkUri(cover.takeIf { it.isNotEmpty() }?.let(Uri::parse)).build()).build()
