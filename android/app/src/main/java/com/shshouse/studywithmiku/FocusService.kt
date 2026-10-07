package com.shshouse.studywithmiku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLeaseStarted = 0L
    private val app get() = application as MikuApplication
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("focus_active","正在专注",NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this,25,notification(app.timer.state.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"StudyWithMiku:Focus")
        renewWakeLease()
        scope.launch {
            app.timer.state.collect { state ->
                if (state.running) {
                    if (SystemClock.elapsedRealtime() - wakeLeaseStarted >= 9 * 60_000L) renewWakeLease()
                    getSystemService(NotificationManager::class.java).notify(25,notification(state))
                }
                else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
        }
    }
    private fun renewWakeLease() {
        wakeLock?.let { if (it.isHeld) it.release(); it.acquire(10 * 60_000L) }
        wakeLeaseStarted = SystemClock.elapsedRealtime()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "pause") app.timer.pause()
        return START_STICKY
    }
    private fun notification(s: FocusClock): Notification {
        val open = PendingIntent.getActivity(this,25,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause = PendingIntent.getService(this,26,Intent(this,FocusService::class.java).setAction("pause"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val time = timeLabel(if (s.mode == TimerMode.STOPWATCH) s.elapsedMs else s.remainingMs)
        return NotificationCompat.Builder(this,"focus_active").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${s.phase.label} · $time").setContentText("Study With Miku · ${s.mode.label}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notification,"暂停",pause).build()
    }
    override fun onDestroy() {
        app.timer.flush()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
