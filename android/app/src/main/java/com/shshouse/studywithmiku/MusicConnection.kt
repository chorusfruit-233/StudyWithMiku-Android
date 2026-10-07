package com.shshouse.studywithmiku

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(val connected: Boolean = false, val songId: String? = null,
    val playing: Boolean = false, val loading: Boolean = false, val position: Long = 0,
    val duration: Long = 0, val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_ALL,
    val error: String? = null)
class MusicConnection(context: Context) {
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()
    private var controller: MediaController? = null
    private val future = MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private val loop = object : Runnable {
        override fun run() { update(); if (!closed) handler.postDelayed(this,500) }
    }
    init {
        future.addListener({
            if (!closed) runCatching { controller = future.get() }.onSuccess {
                controller?.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { update() }
                    override fun onPlayerError(error: PlaybackException) { _state.value = _state.value.copy(error = "这首音乐无法播放，请尝试下一首") }
                })
                handler.post(loop)
            }.onFailure { _state.value = _state.value.copy(error = "播放器连接失败，请重新打开应用") }
        },ContextCompat.getMainExecutor(context))
    }
    private fun update() {
        val p = controller ?: return
        _state.value = PlaybackState(true,p.currentMediaItem?.mediaId,p.isPlaying,
            p.playbackState == Player.STATE_BUFFERING,p.currentPosition,p.duration.coerceAtLeast(0),
            p.shuffleModeEnabled,p.repeatMode,if (p.playerError == null) null else _state.value.error)
    }
    fun play(songs: List<Song>, id: String? = songs.firstOrNull()?.id) {
        val p = controller ?: return
        if (songs.isEmpty()) return
        p.setMediaItems(songs.map { it.mediaItem() },songs.indexOfFirst { it.id == id }.coerceAtLeast(0),0)
        p.prepare(); p.play()
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun next() { controller?.seekToNextMediaItem() }
    fun seek(position: Long) { controller?.seekTo(position) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL } }
    fun remove(id: String) { controller?.let { p -> for (i in p.mediaItemCount-1 downTo 0) if (p.getMediaItemAt(i).mediaId == id) p.removeMediaItem(i) } }
    fun close() { closed = true; handler.removeCallbacks(loop); MediaController.releaseFuture(future) }
}
