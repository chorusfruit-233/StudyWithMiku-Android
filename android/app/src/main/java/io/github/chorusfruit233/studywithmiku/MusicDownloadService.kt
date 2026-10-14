@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package io.github.chorusfruit233.studywithmiku

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService

class MusicDownloadService : DownloadService(3001,1000,"music_downloads",R.string.music_download_channel,0) {
    override fun getDownloadManager() = (application as MikuApplication).audioCache.manager
    // Requirements changes resume within the running process; after force-stop, resume from the music page.
    override fun getScheduler() = null
    override fun getForegroundNotification(downloads: List<Download>,notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return DownloadNotificationHelper(this,"music_downloads").buildProgressNotification(this,R.drawable.ic_notification,
            open,"正在缓存音乐，完成后可离线播放",downloads,notMetRequirements)
    }
    override fun onTimeout(startId: Int,fgsType: Int) {
        getDownloadManager().pauseDownloads()
        stopSelf()
    }
}
