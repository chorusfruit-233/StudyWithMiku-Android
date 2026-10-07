package com.shshouse.studywithmiku

import android.content.Intent
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
    private var preparedNext = ""
    private var fading = false
    private var crossGain = 1f
    private var breakGain = 1f
    private var duckGain = 1f
    private var breakResume = false
    private var breakJob: Job? = null
    private var duckJob: Job? = null
    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setUserAgent("StudyWithMiku-Android/1.2")
            .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        fun create() = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,http))).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),false)
            setHandleAudioBecomingNoisy(true); setWakeMode(C.WAKE_MODE_NETWORK)
        }
        player = create().apply { setAudioAttributes(audioAttributes,true) }
        preview = create().apply { volume = 0f }
        val publicPlayer = object : ForwardingPlayer(player) {
            override fun getVolume() = userVolume
            override fun setVolume(volume: Float) { userVolume = volume.coerceIn(0f,1f); gains(); save(player) }
            override fun pause() { manualControl(); preview.pause(); super.pause() }
            override fun play() { manualControl(); super.play() }
            override fun setPlayWhenReady(playWhenReady: Boolean) { manualControl(); if (!playWhenReady) preview.pause(); super.setPlayWhenReady(playWhenReady) }
        }
        session = MediaSession.Builder(this,publicPlayer).build()
        val saved = store.readQueue()
        userVolume = saved.optDouble("volume",1.0).toFloat().coerceIn(0f,1f); gains()
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
                if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) || events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_TIMELINE_CHANGED)) clearPreview()
                save(player)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?,reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && fading && mediaItem?.mediaId == preparedNext) {
                    val position = preview.currentPosition
                    clearPreview(); player.seekTo(position)
                } else clearPreview()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,newPosition: Player.PositionInfo,reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) clearPreview()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) preview.pause() }
        })
        scope.launch { while (isActive) { crossfadeTick(); delay(100) } }
        scope.launch {
            combine(app.timer.state,store.prefs) { clock,prefs -> (clock.mode == TimerMode.POMODORO && clock.phase != Phase.FOCUS) to prefs.pauseMusicDuringBreak }
                .distinctUntilChanged().collect { (rest,enabled) ->
                    breakJob?.cancel()
                    if (rest && enabled && player.playWhenReady) {
                        breakResume = true; clearPreview()
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
        player.volume = userVolume*crossGain*breakGain*duckGain
        preview.volume = userVolume*(if (fading) 1f-crossGain else 0f)*breakGain*duckGain
    }
    private fun clearPreview() {
        preview.stop(); preview.clearMediaItems(); preparedNext = ""; fading = false; crossGain = 1f; gains()
    }
    private fun crossfadeTick() {
        if (!store.prefs.value.crossfade || player.repeatMode == Player.REPEAT_MODE_ONE || player.duration <= 6000) {
            if (preparedNext.isNotEmpty()) clearPreview(); return
        }
        if (!player.isPlaying || breakGain < 1f) return
        val remaining = player.duration-player.currentPosition
        val index = player.nextMediaItemIndex
        if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) return
        val next = player.getMediaItemAt(index)
        if (remaining in 1..20_000 && preparedNext != next.mediaId) {
            clearPreview(); preparedNext = next.mediaId; preview.setMediaItem(next); preview.prepare()
        }
        if (preparedNext == next.mediaId && remaining in 1..6000 && preview.playbackState == Player.STATE_READY) {
            if (!fading) fading = true
            if (!preview.playWhenReady) preview.play()
            crossGain = (remaining/6000f).coerceIn(0f,1f); gains()
        } else if (remaining > 20_000 && preparedNext.isNotBlank()) clearPreview()
    }
    private fun save(player: Player) {
        store.saveQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            player.currentMediaItemIndex.coerceAtLeast(0),player.currentPosition,player.repeatMode,player.shuffleModeEnabled,userVolume)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) { session?.player?.let { save(it); if (!it.playWhenReady) stopSelf() } }
    override fun onDestroy() {
        scope.cancel()
        session?.let { save(it.player); preview.release(); player.release(); it.release() }; session = null
        super.onDestroy()
    }
}
fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
        .setArtworkUri(cover.takeIf { it.isNotEmpty() }?.let(Uri::parse)).build()).build()
