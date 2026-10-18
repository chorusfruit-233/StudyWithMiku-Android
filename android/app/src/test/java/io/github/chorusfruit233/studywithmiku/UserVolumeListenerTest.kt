package io.github.chorusfruit233.studywithmiku

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class UserVolumeListenerTest {
    @Test fun stateCallbacksReachControllerWhileVolumeUsesUserSetting() {
        var playing = false; var ready = false; var loading = false; var playbackState = 0; var observedVolume = -1f
        var userVolume = .35f
        val delegate = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int) { ready = playWhenReady }
            override fun onIsLoadingChanged(isLoading: Boolean) { loading = isLoading }
            override fun onPlaybackStateChanged(state: Int) { playbackState = state }
            override fun onVolumeChanged(volume: Float) { observedVolume = volume }
        }
        val bridge = UserVolumeListener(delegate) { userVolume }
        bridge.onIsPlayingChanged(true); bridge.onPlayWhenReadyChanged(true,1); bridge.onIsLoadingChanged(true)
        bridge.onPlaybackStateChanged(Player.STATE_READY); bridge.onVolumeChanged(.1f)
        assertTrue(playing); assertTrue(ready); assertTrue(loading); assertEquals(Player.STATE_READY,playbackState)
        assertEquals(.35f,observedVolume,.001f)
        userVolume = 0f; bridge.onVolumeChanged(.2f)
        assertEquals(0f,observedVolume,0f)
    }
    @Test fun everyMedia3DefaultCallbackIsExplicitlyForwarded() {
        val missing = Player.Listener::class.java.declaredMethods.filter { method ->
            runCatching { UserVolumeListener::class.java.getDeclaredMethod(method.name,*method.parameterTypes) }.isFailure
        }.map { it.name }
        assertTrue("Unforwarded Media3 callbacks: $missing",missing.isEmpty())
    }
}
