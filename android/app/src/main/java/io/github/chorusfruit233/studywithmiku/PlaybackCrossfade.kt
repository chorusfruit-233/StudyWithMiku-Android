@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package io.github.chorusfruit233.studywithmiku

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** The public player keeps its queue; a second decoder bridges switches and buffering. */
internal class PlaybackCrossfade(
    private val primary: ExoPlayer,private val secondary: ExoPlayer,
    private val enabled: () -> Boolean,private val allowed: () -> Boolean,
    private val gains: (Float,Float) -> Unit
) {
    private enum class Stage { IDLE, PRELOAD, MANUAL_WAIT, MANUAL_FADE, AUTO_FADE, HANDOFF_WAIT, HANDOFF }
    private var stage = Stage.IDLE
    private var target: MediaItem? = null
    private var targetIndex = C.INDEX_UNSET
    private var replacement: List<MediaItem>? = null
    private var started = 0L
    private var handoffDeadline = 0L
    private var autoRetryAt = 0L
    private var changing = false
    private var primaryGain = 1f
    private var secondaryGain = 0f
    private val manual get() = stage == Stage.MANUAL_WAIT || stage == Stage.MANUAL_FADE
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?,reason: Int) {
            if (changing || stage == Stage.IDLE) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && same(mediaItem,target) && secondary.isPlaying) {
                handoff()
            } else if (manual) cancel(finishManual = true) else cancel()
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int) {
            if (!playWhenReady && !changing) cancel(finishManual = true)
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,newPosition: Player.PositionInfo,reason: Int) {
            if (!changing && reason == Player.DISCONTINUITY_REASON_SEEK) cancel()
        }
    }
    init { primary.addListener(listener) }
    private fun mix(main: Float,preview: Float) {
        primaryGain = main.coerceIn(0f,1f); secondaryGain = preview.coerceIn(0f,1f)
        gains(primaryGain,secondaryGain)
    }
    fun refreshGains() = gains(primaryGain,secondaryGain)
    fun cancel(finishManual: Boolean = false) {
        if (finishManual && manual) commit(0)
        stage = Stage.IDLE; target = null; replacement = null; targetIndex = C.INDEX_UNSET
        secondary.stop(); secondary.clearMediaItems(); mix(1f,0f)
    }
    fun close() { primary.removeListener(listener); cancel() }
    private fun same(first: MediaItem?,second: MediaItem?) = first != null && second != null &&
        first.mediaId == second.mediaId && first.localConfiguration == second.localConfiguration
    private fun canSwitch(position: Long) = enabled() && allowed() && primary.isPlaying &&
        (position == 0L || position == C.TIME_UNSET)
    fun switchTo(index: Int,position: Long = 0): Boolean {
        if (index !in 0 until primary.mediaItemCount || index == primary.currentMediaItemIndex || !canSwitch(position)) return false
        request(primary.getMediaItemAt(index),index,null); return true
    }
    fun replace(items: List<MediaItem>,index: Int,position: Long): Boolean {
        if (index !in items.indices || !canSwitch(position)) return false
        if (same(primary.currentMediaItem,items[index])) return false
        request(items[index],index,items.toList()); return true
    }
    fun adjacent(forward: Boolean): Int {
        if (replacement != null && manual) cancel(finishManual = true)
        val index = if (manual) targetIndex else primary.currentMediaItemIndex
        val timeline = primary.currentTimeline
        if (timeline.isEmpty || index !in 0 until timeline.windowCount) return C.INDEX_UNSET
        val repeat = if (primary.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else primary.repeatMode
        return if (forward) timeline.getNextWindowIndex(index,repeat,primary.shuffleModeEnabled)
        else timeline.getPreviousWindowIndex(index,repeat,primary.shuffleModeEnabled)
    }
    private fun request(item: MediaItem,index: Int,items: List<MediaItem>?) {
        autoRetryAt = 0
        val reuse = stage == Stage.PRELOAD && same(target,item) && secondary.playerError == null
        if (!reuse) { cancel(); secondary.setMediaItem(item); secondary.prepare() }
        target = item; targetIndex = index; replacement = items
        stage = Stage.MANUAL_WAIT; started = SystemClock.elapsedRealtime(); mix(1f,0f)
    }
    private fun commit(position: Long) {
        changing = true
        try {
            replacement?.let { primary.setMediaItems(it,targetIndex,position) } ?: primary.seekTo(targetIndex,position)
            primary.prepare()
        } finally { changing = false }
    }
    private fun handoff() {
        mix(0f,1f)
        changing = true
        try { primary.seekTo(secondary.currentPosition) } finally { changing = false }
        replacement = null; stage = Stage.HANDOFF_WAIT; started = SystemClock.elapsedRealtime()
        handoffDeadline = started+7000
    }
    fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (!enabled() || !allowed() || !primary.playWhenReady) { if (stage != Stage.IDLE) cancel(finishManual = true); return }
        if (primary.playerError != null) { if (stage != Stage.IDLE) cancel(finishManual = true); return }
        // A decoder without audio focus must never keep the secondary voice audible.
        if (primary.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
            secondary.pause(); gains(0f,0f); return
        }
        if (secondary.playerError != null && stage != Stage.IDLE) {
            if (!manual) autoRetryAt = now+5000
            cancel(finishManual = true); return
        }
        when (stage) {
            Stage.MANUAL_WAIT -> {
                if (now-started > 8000) { cancel(finishManual = true); return }
                if (secondary.playbackState == Player.STATE_READY) {
                    if (secondary.duration in 1..2000) { cancel(finishManual = true); return }
                    secondary.play()
                    if (secondary.isPlaying) { stage = Stage.MANUAL_FADE; started = now }
                }
            }
            Stage.MANUAL_FADE -> {
                if (!secondary.isPlaying) {
                    if (now-started > 8000 || secondary.playbackState == Player.STATE_ENDED) { cancel(finishManual = true); return }
                    secondary.play(); mix(1f,0f); return
                }
                val progress = ((now-started)/1500f).coerceIn(0f,1f)
                val fade = fadeCurve(progress); mix(1f-fade,fade)
                if (progress >= 1f) { commit(secondary.currentPosition); handoff() }
            }
            Stage.HANDOFF_WAIT -> {
                if (now > handoffDeadline || !same(primary.currentMediaItem,target)) { cancel(); return }
                if (!secondary.playWhenReady) secondary.play()
                mix(0f,1f)
                if (primary.isPlaying) {
                    changing = true
                    try { primary.seekTo(secondary.currentPosition) } finally { changing = false }
                    stage = Stage.HANDOFF; started = now
                }
            }
            Stage.HANDOFF -> {
                if (now > handoffDeadline) { cancel(); return }
                if (!primary.isPlaying) { stage = Stage.HANDOFF_WAIT; started = now; mix(0f,1f); return }
                if (!secondary.playWhenReady) secondary.play()
                val progress = ((now-started)/400f).coerceIn(0f,1f)
                val fade = fadeCurve(progress); mix(fade,1f-fade)
                if (progress >= 1f) cancel()
            }
            Stage.IDLE,Stage.PRELOAD,Stage.AUTO_FADE -> autoTick()
        }
        refreshGains()
    }
    private fun autoTick() {
        if (SystemClock.elapsedRealtime() < autoRetryAt) return
        if (stage == Stage.AUTO_FADE && !secondary.isPlaying) { autoRetryAt = SystemClock.elapsedRealtime()+5000; cancel(); return }
        val index = primary.nextMediaItemIndex
        if (primary.repeatMode == Player.REPEAT_MODE_ONE || primary.duration <= 8000 ||
            index == C.INDEX_UNSET || index == primary.currentMediaItemIndex) {
            if (stage != Stage.IDLE) cancel(); return
        }
        if (!primary.isPlaying) return
        val remaining = primary.duration-primary.currentPosition
        val item = primary.getMediaItemAt(index)
        val preload = remaining in 1..20_000 || primary.duration-primary.bufferedPosition <= 15_000
        if (preload && !same(target,item)) {
            cancel(); target = item; targetIndex = index; stage = Stage.PRELOAD
            secondary.setMediaItem(item); secondary.prepare()
        }
        val fadeMs = if (secondary.duration > 500) (secondary.duration-400).coerceIn(200,6000) else 6000L
        if (same(target,item) && remaining in 1..fadeMs && secondary.playbackState == Player.STATE_READY) {
            if (!secondary.playWhenReady) secondary.play()
            if (secondary.isPlaying) {
                stage = Stage.AUTO_FADE
                val fade = fadeCurve(1f-(remaining/fadeMs.toFloat()).coerceIn(0f,1f)); mix(1f-fade,fade)
            }
        } else if (stage == Stage.AUTO_FADE && remaining > 6000) cancel()
    }
}

internal fun fadeCurve(progress: Float): Float {
    val p = progress.coerceIn(0f,1f)
    return if (p < .5f) 2*p*p else 1-2*(1-p)*(1-p)
}
