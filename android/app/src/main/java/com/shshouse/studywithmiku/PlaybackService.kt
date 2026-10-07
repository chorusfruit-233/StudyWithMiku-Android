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

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val store get() = (application as MikuApplication).store
    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setUserAgent("StudyWithMiku-Android/1.1")
            .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,http))).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA).build(),true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
        }
        session = MediaSession.Builder(this,player).build()
        val saved = store.readQueue()
        player.volume = saved.optDouble("volume",1.0).toFloat().coerceIn(0f,1f)
        val songs = store.library.value.songs.associateBy { it.id }
        val items = saved.optJSONArray("ids")?.strings()?.mapNotNull { songs[it]?.mediaItem() } ?: emptyList()
        if (items.isNotEmpty()) {
            player.setMediaItems(items,saved.optInt("index").coerceIn(0,items.lastIndex),saved.optLong("position").coerceAtLeast(0))
            player.repeatMode = saved.optInt("repeat",Player.REPEAT_MODE_ALL)
            player.shuffleModeEnabled = saved.optBoolean("shuffle")
            player.prepare()
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { save(player) }
        })
    }
    private fun save(player: Player) {
        store.saveQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            player.currentMediaItemIndex.coerceAtLeast(0),player.currentPosition,player.repeatMode,player.shuffleModeEnabled,player.volume)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.let { save(it); if (!it.playWhenReady) stopSelf() }
    }
    override fun onDestroy() {
        session?.let { save(it.player); it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
        .setArtworkUri(cover.takeIf { it.isNotEmpty() }?.let(Uri::parse)).build()).build()
