package com.shshouse.studywithmiku

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.io.IOException
import kotlin.concurrent.thread

class MusicProtocolTest {
    private fun payload(br: String = "2000") = """[
      {"name":"flos - STUDY WITH MIKU ver. -","artist":"STUDY WITH MIKU",
       "url":"https://music.example/meting/?server=netease&type=url&id=123&br=$br",
       "pic":"https://music.example/meting/?type=pic&id=456","lrc":"https://music.example/meting/?type=lrc&id=123"},
      {"title":"第二首","author":"作者","url":"/song.mp3","cover":"/cover.jpg","lrc":"[00:01.50]歌词"}
    ]"""

    @Test fun followsOriginalMetingBitrateContract() {
        assertEquals("https://api.qijieya.cn/meting/?server=netease&type=playlist&id=17543418420&br=2000",MusicProtocol.playlistUrl(MusicConfig()))
        assertEquals("https://api.qijieya.cn/meting/?server=tencent&type=playlist&id=9515739794",
            MusicProtocol.playlistUrl(MusicConfig(platform = "tencent",id = "9515739794",bitrate = "320")))
    }
    @Test fun qualityChangePreservesFavoriteAndQueueIdentity() {
        val high = MusicProtocol.songs(payload(),MusicConfig())
        val standard = MusicProtocol.songs(payload("320"),MusicConfig(bitrate = "320"))
        assertEquals(high.map { it.id },standard.map { it.id })
        assertNotEquals(high.first().uri,standard.first().uri)
        val qq = MusicProtocol.songs(payload(),MusicConfig(platform = "tencent"))
        assertNotEquals(high.first().id,qq.first().id)
    }
    @Test fun recognizesBothMetingFormatsAndResolvesRelativeResources() {
        val list = MusicProtocol.songs(payload(),MusicConfig())
        assertEquals(2,list.size)
        assertEquals("作者",list[1].artist)
        assertEquals("https://api.qijieya.cn/song.mp3",list[1].uri)
        assertEquals("https://api.qijieya.cn/cover.jpg",list[1].cover)
        assertEquals("[00:01.50]歌词",list[1].lyrics)
        assertTrue(list.all { it.online && !it.builtin })
    }
    @Test fun onlyDefaultPlaylistUsesOriginalTranslatedNames() {
        val names = mapOf("flos" to "花 flos")
        assertEquals("花 flos",MusicProtocol.songs(payload(),MusicConfig(),names).first().title)
        assertEquals("flos - STUDY WITH MIKU ver. -",MusicProtocol.songs(payload(),MusicConfig(id = "42"),names).first().title)
    }
    @Test fun dropsUnplayableRowsAndDeduplicatesSongs() {
        val json = """[null,{}, {"name":"bad","url":"javascript:alert(1)"},
          {"name":"good","url":"https://example.org/a.mp3","id":"7"},
          {"name":"good again","url":"https://example.org/b.mp3","id":"7"}]"""
        assertEquals(1,MusicProtocol.songs(json,MusicConfig()).size)
    }
    @Test fun understandsNeteaseAndQqShareLinksAndPlainIds() {
        assertEquals("17543418420",MusicProtocol.playlistId(" 17543418420 "))
        assertEquals("1234",MusicProtocol.playlistId("https://music.163.com/#/playlist?id=1234&userid=99"))
        assertEquals("9515739794",MusicProtocol.playlistId("https://y.qq.com/n/ryqq/playlist/9515739794"))
        assertEquals("42",MusicProtocol.playlistId("https://y.qq.com/n/yqq/playsquare/42.html?disstid=42"))
        assertNull(MusicProtocol.playlistId("abc"))
    }
    @Test fun rejectsInvalidEndpointOrPlatformBeforeMakingRequests() {
        for (config in listOf(MusicConfig(api = "file:///tmp/a"),MusicConfig(api = "https://user:pass@example.org/"),
            MusicConfig(api = "https://example.org/?id=other"),MusicConfig(platform = "other"),MusicConfig(id = "bad"))) {
            assertThrows(IllegalArgumentException::class.java) { MusicProtocol.playlistUrl(config) }
        }
    }
    @Test fun synchronizesMultiTimestampLyricsWithFractionsAndOffset() {
        val lines = MusicProtocol.lyrics("[ar:Miku]\n[offset:-50]\n[00:02.5][01:03.025]同一句\n[00:01.00]第一句\n[00:04.00]\n")
        assertEquals(listOf(950L,2450L,62975L),lines.map { it.timeMs })
        assertEquals(listOf("第一句","同一句","同一句"),lines.map { it.text })
        assertTrue(MusicProtocol.lyrics("纯音乐").isEmpty())
    }
    @Test fun httpClientHandlesRedirectsErrorsAndResponseLimits() {
        val server = ServerSocket(0,50,InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) runCatching {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    val path = input.readLine().split(' ')[1]
                    while (!input.readLine().isNullOrEmpty()) { /* consume HTTP headers */ }
                    val response = when (path) {
                        "/redirect" -> "HTTP/1.1 302 Found\r\nLocation: /ok\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        "/ok" -> "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello"
                        else -> "HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    }
                    socket.getOutputStream().write(response.toByteArray())
                }
            }
        }
        try {
            val base = "http://127.0.0.1:${server.localPort}"
            assertEquals("hello",MusicHttp.text("$base/redirect"))
            assertThrows(IOException::class.java) { MusicHttp.bytes("$base/ok",4) }
            assertTrue(assertThrows(IOException::class.java) { MusicHttp.text("$base/error") }.message!!.contains("503"))
        } finally { server.close(); worker.join(1000) }
    }
}
