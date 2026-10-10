package io.github.chorusfruit233.studywithmiku

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class NetworkPlaylistCatalogTest {
    @Test fun repeatedPlaylistMovesToFrontWithoutDuplicatingOrLosingItsName() {
        val first = NetworkPlaylistCatalog.add(emptyList(),"netease"," 42 "," 原歌单 ")
        val second = NetworkPlaylistCatalog.add(first,"tencent","88","QQ 歌单")
        val again = NetworkPlaylistCatalog.add(second,"netease","42","")
        assertEquals(listOf("42","88"),again.map { it.id })
        assertEquals("原歌单",again.first().name)
        assertEquals(2,again.size)
    }
    @Test fun sameNumericIdOnDifferentPlatformsRemainsDistinct() {
        val first = NetworkPlaylistCatalog.add(emptyList(),"netease","42","网易")
        val second = NetworkPlaylistCatalog.add(first,"tencent","42","QQ")
        assertEquals(2,second.size)
        assertEquals(listOf(SavedNetworkPlaylist("tencent","42","QQ")),NetworkPlaylistCatalog.remove(second,"netease","42"))
    }
    @Test fun metadataRoundTripsAndMissingNamesFallBackToId() {
        val first = NetworkPlaylistCatalog.add(emptyList(),"netease","42","")
        assertEquals("ID:42",first.single().name)
        assertEquals(first,NetworkPlaylistCatalog.read(NetworkPlaylistCatalog.write(first)))
        val titled = NetworkPlaylistCatalog.add(first,"netease","42","名".repeat(80))
        assertEquals(50,titled.single().name.length)
    }
    @Test fun invalidSavedEntriesAndDuplicateKeysAreIgnored() {
        val stored = JSONArray("""[null,{}, {"platform":"other","playlistId":"42"},
            {"platform":"netease","playlistId":"bad"}, {"platform":"netease","playlistId":"42","name":"first"},
            {"platform":"netease","playlistId":"42","name":"duplicate"}]""")
        assertEquals(listOf(SavedNetworkPlaylist("netease","42","first")),NetworkPlaylistCatalog.read(stored))
        assertThrows(IllegalArgumentException::class.java) { NetworkPlaylistCatalog.add(emptyList(),"other","42","name") }
    }
}
