package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NetworkPlaylistPersistenceTest {
    private fun isolated(test: (Context,MikuStore) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "network_playlist_fixture_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try { test(context,MikuStore(context)) } finally { target.deleteSharedPreferences(name) }
    }
    private fun songs() = MockResponse().setBody("""[{"name":"Fixture song","url":"https://example.test/song.mp3","id":"song-42"}]""")
    private fun load(online: OnlineMusic,config: MusicConfig): OnlineMusicState = runBlocking {
        withContext(Dispatchers.Main) { online.load(config,saveCustom = true) }
        withTimeout(15_000) { online.state.first { !it.loading } }
    }
    @Test fun savedBookmarksSurviveRecreationAndRemainSeparateFromNativePlaylists() = isolated { context,store ->
        val song = store.library.value.songs.first()
        store.toggleFavorite(song.id)
        store.savePlaylist("Native playlist",listOf(song.id))
        val native = store.library.value
        val revision = store.revision.value
        store.saveNetworkPlaylist("netease","42","Netease fixture")
        store.saveNetworkPlaylist("tencent","42","QQ fixture")
        store.saveNetworkPlaylist("netease","42","")
        val reopened = MikuStore(context)
        assertEquals(2,reopened.networkPlaylists.value.size)
        assertEquals("Netease fixture",reopened.networkPlaylists.value.first().name)
        reopened.deleteNetworkPlaylist("netease","42")
        assertEquals(listOf(SavedNetworkPlaylist("tencent","42","QQ fixture")),MikuStore(context).networkPlaylists.value)
        assertEquals(native.favorites,reopened.library.value.favorites)
        assertEquals(native.playlists,reopened.library.value.playlists)
        assertEquals(revision,store.revision.value)
    }
    @Test fun successfulLoadSavesRemoteNameButFailureKeepsPreviousPlaylist() = isolated { context,store ->
        MockWebServer().use { server ->
            server.start()
            val config = MusicConfig(api = server.url("/meting/").toString(),id = "42",name = "New playlist")
            val online = OnlineMusic(context,store,PlaylistNameService(server.url("/").toString()))
            server.enqueue(songs()); server.enqueue(MockResponse().setBody("{\"name\":\"Remote fixture name\"}"))
            val loaded = load(online,config)
            assertNull(loaded.error)
            assertEquals("Remote fixture name",loaded.config.name)
            assertEquals("Remote fixture name",MikuStore(context).networkPlaylists.value.single().name)
            assertEquals("Remote fixture name",store.library.value.songs.find { it.id == loaded.ids.single() }!!.playlistName)
            server.enqueue(MockResponse().setResponseCode(503))
            val failed = load(online,config.copy(id = "99"))
            assertNotNull(failed.error)
            assertEquals(loaded.config,failed.config); assertEquals(loaded.ids,failed.ids)
            assertEquals(listOf("42"),MikuStore(context).networkPlaylists.value.map { it.id })
            assertEquals(3,server.requestCount)
        }
    }
    @Test fun nameLookupFailureStillSavesPlayablePlaylistWithIdFallback() = isolated { context,store ->
        MockWebServer().use { server ->
            server.start()
            val online = OnlineMusic(context,store,PlaylistNameService(server.url("/").toString()))
            server.enqueue(songs()); server.enqueue(MockResponse().setResponseCode(503))
            val loaded = load(online,MusicConfig(api = server.url("/meting/").toString(),platform = "tencent",id = "42"))
            assertNull(loaded.error)
            assertEquals(1,loaded.ids.size)
            assertEquals("ID:42",loaded.config.name)
            assertEquals(SavedNetworkPlaylist("tencent","42","ID:42"),MikuStore(context).networkPlaylists.value.single())
        }
    }
}
