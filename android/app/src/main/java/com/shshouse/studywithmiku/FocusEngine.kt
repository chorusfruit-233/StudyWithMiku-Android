package com.shshouse.studywithmiku

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow

class FocusEngine(private val context: Context, private val store: MikuStore) {
    private val boot = Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
    private val _state = MutableStateFlow(store.readClock(boot))
    val state = _state.asStateFlow()
    private val _completions = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val completions = _completions.asSharedFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var ticksSinceSave = 0
    private val loop = object : Runnable {
        override fun run() {
            sync()
            if (_state.value.running) handler.postDelayed(this,1000)
        }
    }
    init { if (_state.value.running) { sync(); if (_state.value.running) handler.post(loop) } }
    fun sync() {
        val now = SystemClock.elapsedRealtime()
        var finishedAny = false
        var turns = 0
        while (_state.value.running && turns++ < 10000) {
        val before = _state.value
        val result = before.tick(now,store.prefs.value)
        finishedAny = finishedAny || result.finished
        val completedAt = if (result.finished) before.lastTickMs + before.remainingMs else now
        _state.value = if (result.finished && before.mode == TimerMode.POMODORO && store.prefs.value.autoAdvance)
            result.clock.copy(running = true,lastTickMs = completedAt) else result.clock
        if (result.studiedMs > 0 || result.completedFocus) {
            // A resumed process may span multiple days. Credit only actual timer duration.
            val ending = System.currentTimeMillis() - ((now - before.lastTickMs).coerceAtLeast(0) - result.studiedMs).coerceAtLeast(0)
            store.record(result.studiedMs,result.completedFocus,ending)
        }
        if (result.finished && now-completedAt < 2000) notifyCompletion(before)
        if (!result.finished || !_state.value.running || _state.value.lastTickMs >= now) break
        }
        ticksSinceSave++
        if (finishedAny || ticksSinceSave >= 15) checkpoint()
    }
    private fun checkpoint() { ticksSinceSave = 0; store.saveClock(_state.value,boot) }
    fun toggle() {
        sync()
        if (_state.value.running) pause()
        else {
            _state.value = _state.value.start(SystemClock.elapsedRealtime())
            checkpoint()
            ContextCompat.startForegroundService(context,Intent(context,FocusService::class.java))
            handler.removeCallbacks(loop)
            handler.postDelayed(loop,1000)
        }
    }
    fun pause() {
        sync()
        _state.value = _state.value.copy(running = false)
        handler.removeCallbacks(loop)
        checkpoint()
        store.markLearningChanged()
    }
    fun reset(mode: TimerMode = _state.value.mode, phase: Phase = _state.value.phase) {
        pause()
        _state.value = FocusClock(mode = mode,phase = if (mode == TimerMode.POMODORO) phase else Phase.FOCUS,
            remainingMs = FocusClock.duration(mode,if (mode == TimerMode.POMODORO) phase else Phase.FOCUS,store.prefs.value),round = _state.value.round)
        checkpoint()
        store.updatePreferences(store.prefs.value.copy(timerMode = mode.name.lowercase()))
    }
    fun skipBreak() { if (_state.value.phase != Phase.FOCUS) reset(phase = Phase.FOCUS) }
    fun flush() { sync(); checkpoint() }
    fun restoreFromStore() {
        handler.removeCallbacks(loop)
        _state.value = store.readClock(boot).copy(running = false)
        checkpoint()
    }
    private fun notifyCompletion(before: FocusClock) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("focus_done","专注完成",NotificationManager.IMPORTANCE_DEFAULT))
        val title = if (before.phase == Phase.FOCUS) "辛苦啦，一起休息一下吧！" else "准备好了吗？开始下一次专注吧"
        val intent = PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context,"focus_done").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText("Miku 会一直陪着你 · 点此回到学习空间")
            .setContentIntent(intent).setAutoCancel(true).build()
        runCatching { manager.notify(39,notification) }
        if (store.prefs.value.completionSound) {
            _completions.tryEmit(Unit)
            runCatching {
                MediaPlayer.create(context,R.raw.completion)?.apply {
                    setVolume(store.prefs.value.notificationVolume,store.prefs.value.notificationVolume)
                    setOnCompletionListener { it.release() }
                    setOnErrorListener { player,_,_ -> player.release(); true }
                    start()
                }
            }
        }
    }
}
