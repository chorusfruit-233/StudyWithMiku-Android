@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** A single cache instance is shared by downloads, playback and crossfade preview. */
class AudioCache(private val context: Context,private val store: MikuStore,
    directory: File = File(context.filesDir,"offline_audio"),databaseName: String = "offline_audio") {
    private val database = StandaloneDatabaseProvider(object : android.content.ContextWrapper(context) {
        override fun getDatabasePath(name: String) = context.getDatabasePath("${databaseName}_$name")
        override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
            context.openOrCreateDatabase("${databaseName}_$name",mode,factory)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler: android.database.DatabaseErrorHandler?) =
            context.openOrCreateDatabase("${databaseName}_$name",mode,factory,errorHandler)
    })
    val cache = SimpleCache(directory,NoOpCacheEvictor(),database)
    private val executor = Executors.newFixedThreadPool(2)
    private val http = DefaultHttpDataSource.Factory().setUserAgent("StudyWithMiku-Android/${BuildConfig.VERSION_NAME}")
        .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        .setContentTypePredicate { type ->
            val mime = type.lowercase().substringBefore(';').trim()
            !mime.startsWith("text/") && !mime.contains("json") && !mime.contains("mpegurl")
        }
    val manager = DownloadManager(context,database,cache,http,executor).apply {
        maxParallelDownloads = 2
        // A reopened queue waits for a user action before starting a foreground service.
        pauseDownloads()
    }
    // Playback never grows the offline store implicitly. Only explicit downloads write bytes.
    val playbackFactory = CacheDataSource.Factory().setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context,http)).setCacheWriteDataSinkFactory(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val _state = MutableStateFlow(AudioCacheState(playlists = store.readOfflinePlaylists()))
    val state = _state.asStateFlow()
    init {
        manager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) { refresh() }
            override fun onDownloadChanged(downloadManager: DownloadManager,download: Download,finalException: Exception?) { refresh() }
            override fun onDownloadRemoved(downloadManager: DownloadManager,download: Download) { refresh() }
            override fun onDownloadsPausedChanged(downloadManager: DownloadManager,downloadsPaused: Boolean) { refresh() }
            override fun onWaitingForRequirementsChanged(downloadManager: DownloadManager,waitingForRequirements: Boolean) { refresh() }
        })
        scope.launch {
            for (ignored in refreshes) {
                val current = manager.currentDownloads.associateBy { it.request.id }
                val paused = manager.downloadsPaused
                val waiting = manager.isWaitingForRequirements
                val songs = withContext(Dispatchers.IO) {
                    buildMap {
                        manager.downloadIndex.getDownloads().use { cursor ->
                            while (cursor.moveToNext()) {
                                val download = current[cursor.download.request.id] ?: cursor.download
                                val key = download.request.id
                                val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                                val complete = download.state == Download.STATE_COMPLETED && length > 0 && cache.isCached(key,0,length)
                                val status = when {
                                    complete -> AudioCacheStatus.COMPLETE
                                    download.state == Download.STATE_FAILED || download.state == Download.STATE_COMPLETED -> AudioCacheStatus.FAILED
                                    download.state == Download.STATE_REMOVING -> AudioCacheStatus.REMOVING
                                    paused || download.state == Download.STATE_STOPPED -> AudioCacheStatus.PAUSED
                                    download.state == Download.STATE_DOWNLOADING -> AudioCacheStatus.DOWNLOADING
                                    else -> AudioCacheStatus.QUEUED
                                }
                                put(key,CachedAudio(status,download.bytesDownloaded,download.percentDownloaded))
                            }
                        }
                    }
                }
                _state.value = AudioCacheState(songs,store.readOfflinePlaylists(),cache.cacheSpace,paused,waiting,manager.isInitialized)
            }
        }
        scope.launch { while (isActive) { refresh(); delay(1000) } }
    }
    private fun refresh() { refreshes.trySend(Unit) }
    fun download(playlist: OfflinePlaylist) {
        store.saveOfflinePlaylist(playlist)
        playlist.songs.filter { song -> song.online && (_state.value.songs[song.cacheKey]?.status.let {
            it == null || it == AudioCacheStatus.FAILED
        }) }.distinctBy { it.cacheKey }.forEach { song ->
            DownloadService.sendAddDownload(context,MusicDownloadService::class.java,request(song),true)
        }
        DownloadService.sendResumeDownloads(context,MusicDownloadService::class.java,true)
        refresh()
    }
    fun pause() = DownloadService.sendPauseDownloads(context,MusicDownloadService::class.java,false)
    fun resume() = DownloadService.sendResumeDownloads(context,MusicDownloadService::class.java,true)
    fun clear() {
        DownloadService.sendRemoveAllDownloads(context,MusicDownloadService::class.java,true)
        store.clearOfflinePlaylists(); refresh()
    }
    /** Used by isolated instrumentation fixtures only; the application singleton lives for its process. */
    internal suspend fun release() {
        scope.coroutineContext[Job]?.cancelAndJoin()
        manager.release(); cache.release(); database.close(); executor.shutdownNow()
    }
    companion object {
        fun request(song: Song) = DownloadRequest.Builder(song.cacheKey,Uri.parse(song.uri)).setCustomCacheKey(song.cacheKey)
            .setMimeType(MimeTypes.AUDIO_MPEG).setData(song.title.toByteArray(Charsets.UTF_8)).build()
    }
}
