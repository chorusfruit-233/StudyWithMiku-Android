package io.github.chorusfruit233.studywithmiku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinkPlatformTest {
    @Test fun recognizesQqPlaylistHosts() {
        for (link in listOf(
            "https://y.qq.com/n/ryqq/playlist/9515739794",
            "https://i.y.qq.com/n2/m/share/details/taoge.html?id=42",
            "https://c.y.qq.com/base/fcgi-bin/u?disstid=42",
            "https://music.qq.com/playlist/42",
            "HTTPS://Y.QQ.COM/n/yqq/playsquare/42.html?disstid=42"
        )) assertEquals(link,"tencent",playlistLinkPlatform(link))
    }

    @Test fun recognizesNeteasePlaylistHosts() {
        for (link in listOf(
            "https://music.163.com/#/playlist?id=1234&userid=99",
            "https://m.music.163.com/playlist?id=1234",
            "HTTPS://MUSIC.163.COM/#/playlist?id=1234"
        )) assertEquals(link,"netease",playlistLinkPlatform(link))
    }

    @Test fun recognizesLinksEmbeddedInShareText() {
        assertEquals("tencent",playlistLinkPlatform("分享歌单「和未来一起喝茶」： https://y.qq.com/n/ryqq/playlist/42 来自 QQ 音乐"))
        assertEquals("netease",playlistLinkPlatform("网易云音乐歌单\nhttps://music.163.com/#/playlist?id=42\n一起听歌吧"))
    }

    @Test fun leavesPlainIdsAndMissingLinksForManualSelection() {
        for (input in listOf("17543418420"," 9515739794 ","","只有歌单名称"))
            assertNull(input,playlistLinkPlatform(input))
    }

    @Test fun doesNotMistakeSimilarOrSpoofedDomainsForMusicPlatforms() {
        for (link in listOf(
            "https://y.qq.com.example.org/playlist/42",
            "https://not-y.qq.com/playlist/42",
            "https://music.163.com.example.org/playlist?id=42",
            "https://mymusic.163.com/playlist?id=42",
            "https://y.qq.com@other.example/playlist/42",
            "https://other.example/playlist/42?site=music.163.com"
        )) assertNull(link,playlistLinkPlatform(link))
    }

    @Test fun recognizesOfficialLinksWithoutAScheme() {
        assertEquals("tencent",playlistLinkPlatform("y.qq.com/n/ryqq/playlist/42"))
        assertEquals("netease",playlistLinkPlatform("music.163.com/#/playlist?id=42"))
    }
}
