@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package io.github.chorusfruit233.studywithmiku

import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

/** Java default callbacks need explicit forwarding: Kotlin `by` inherits their no-op bodies. */
internal class UserVolumeListener(private val delegate: Player.Listener,private val volume: () -> Float) : Player.Listener {
    override fun onEvents(player: Player,events: Player.Events) = delegate.onEvents(player,events)
    override fun onTimelineChanged(timeline: Timeline,reason: Int) = delegate.onTimelineChanged(timeline,reason)
    override fun onMediaItemTransition(mediaItem: MediaItem?,reason: Int) = delegate.onMediaItemTransition(mediaItem,reason)
    override fun onTracksChanged(tracks: Tracks) = delegate.onTracksChanged(tracks)
    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = delegate.onMediaMetadataChanged(mediaMetadata)
    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = delegate.onPlaylistMetadataChanged(mediaMetadata)
    override fun onIsLoadingChanged(isLoading: Boolean) = delegate.onIsLoadingChanged(isLoading)
    override fun onLoadingChanged(isLoading: Boolean) = delegate.onLoadingChanged(isLoading)
    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = delegate.onAvailableCommandsChanged(availableCommands)
    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = delegate.onTrackSelectionParametersChanged(parameters)
    override fun onPlayerStateChanged(playWhenReady: Boolean,playbackState: Int) = delegate.onPlayerStateChanged(playWhenReady,playbackState)
    override fun onPlaybackStateChanged(playbackState: Int) = delegate.onPlaybackStateChanged(playbackState)
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int) = delegate.onPlayWhenReadyChanged(playWhenReady,reason)
    override fun onPlaybackSuppressionReasonChanged(reason: Int) = delegate.onPlaybackSuppressionReasonChanged(reason)
    override fun onIsPlayingChanged(isPlaying: Boolean) = delegate.onIsPlayingChanged(isPlaying)
    override fun onRepeatModeChanged(repeatMode: Int) = delegate.onRepeatModeChanged(repeatMode)
    override fun onShuffleModeEnabledChanged(enabled: Boolean) = delegate.onShuffleModeEnabledChanged(enabled)
    override fun onPlayerError(error: PlaybackException) = delegate.onPlayerError(error)
    override fun onPlayerErrorChanged(error: PlaybackException?) = delegate.onPlayerErrorChanged(error)
    override fun onPositionDiscontinuity(reason: Int) = delegate.onPositionDiscontinuity(reason)
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,newPosition: Player.PositionInfo,reason: Int) =
        delegate.onPositionDiscontinuity(oldPosition,newPosition,reason)
    override fun onPlaybackParametersChanged(parameters: PlaybackParameters) = delegate.onPlaybackParametersChanged(parameters)
    override fun onSeekBackIncrementChanged(increment: Long) = delegate.onSeekBackIncrementChanged(increment)
    override fun onSeekForwardIncrementChanged(increment: Long) = delegate.onSeekForwardIncrementChanged(increment)
    override fun onMaxSeekToPreviousPositionChanged(position: Long) = delegate.onMaxSeekToPreviousPositionChanged(position)
    override fun onAudioSessionIdChanged(audioSessionId: Int) = delegate.onAudioSessionIdChanged(audioSessionId)
    override fun onAudioAttributesChanged(attributes: AudioAttributes) = delegate.onAudioAttributesChanged(attributes)
    override fun onVolumeChanged(volume: Float) = delegate.onVolumeChanged(this.volume())
    override fun onSkipSilenceEnabledChanged(enabled: Boolean) = delegate.onSkipSilenceEnabledChanged(enabled)
    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = delegate.onDeviceInfoChanged(deviceInfo)
    override fun onDeviceVolumeChanged(volume: Int,muted: Boolean) = delegate.onDeviceVolumeChanged(volume,muted)
    override fun onVideoSizeChanged(videoSize: VideoSize) = delegate.onVideoSizeChanged(videoSize)
    override fun onSurfaceSizeChanged(width: Int,height: Int) = delegate.onSurfaceSizeChanged(width,height)
    override fun onRenderedFirstFrame() = delegate.onRenderedFirstFrame()
    override fun onCues(cues: List<Cue>) = delegate.onCues(cues)
    override fun onCues(cues: CueGroup) = delegate.onCues(cues)
    override fun onMetadata(metadata: Metadata) = delegate.onMetadata(metadata)
}
