package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlaylistRaceTest {
    @Test fun lateSuccessCannotReplaceNewRemoteSelectionOrRunOldPlaybackCallback() = scenario(false)
    @Test fun lateFailureCannotReplaceOfflineSelectionOrItsSavedConfig() = scenario(true)
    private fun scenario(offline: Boolean) = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "playlist_race_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        val firstArrived = CountDownLatch(1); val releaseFirst = CountDownLatch(1); val firstReturned = CountDownLatch(1)
        fun body(id: String) = """[{"id":"track-$id","name":"Track $id","url":"https://example.test/$id.mp3"}]"""
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val id = request.requestUrl!!.queryParameter("id")!!
                    if (id == "42") {
                        firstArrived.countDown(); releaseFirst.await(10,TimeUnit.SECONDS); firstReturned.countDown()
                        if (offline) return MockResponse().setResponseCode(503)
                    }
                    return MockResponse().setBody(body(id))
                }
            }
            server.start()
            try {
                val store = MikuStore(context)
                val first = MusicConfig(api = server.url("/meting/").toString(),id = "42",name = "Old request")
                val latest = first.copy(id = "99",name = "Latest selection")
                if (offline) store.cacheOnline(latest,MusicProtocol.songs(body("99"),latest))
                val online = OnlineMusic(context,store)
                var oldCallbacks = 0; var latestCallbacks = 0
                withContext(Dispatchers.Main) { online.load(first,saveCustom = true,forceRefresh = true) { oldCallbacks++ } }
                assertTrue(firstArrived.await(10,TimeUnit.SECONDS))
                withContext(Dispatchers.Main) { online.load(latest) { latestCallbacks++ } }
                withTimeout(15_000) { online.state.first { !it.loading && it.config.id == "99" } }
                releaseFirst.countDown(); assertTrue(firstReturned.await(10,TimeUnit.SECONDS)); delay(300)
                assertEquals("99",online.state.value.config.id); assertNull(online.state.value.error)
                assertEquals("99",MikuStore(context).readMusicConfig().id)
                assertNull(store.readNetworkSnapshot(first))
                assertTrue(store.networkPlaylists.value.none { it.id == "42" })
                assertEquals(0,oldCallbacks); assertEquals(1,latestCallbacks)
                assertEquals(listOf("Track 99"),store.library.value.songs.filter { it.online }.map { it.title })
            } finally { releaseFirst.countDown(); target.deleteSharedPreferences(name) }
        }
    }
}
