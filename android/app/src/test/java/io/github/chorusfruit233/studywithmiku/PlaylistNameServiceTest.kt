package io.github.chorusfruit233.studywithmiku

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PlaylistNameServiceTest {
    private fun mock(test: (MockWebServer,PlaylistNameService) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            test(server,PlaylistNameService(server.url("/").toString()))
        }
    }
    @Test fun usesOriginalWorkerEndpointAndNeverSendsAccountCredentials() = mock { server,service ->
        server.enqueue(MockResponse().setBody("""{"name":"  专注歌单  "}"""))
        assertEquals("专注歌单",service.fetch("tencent","42"))
        val request = server.takeRequest()
        assertEquals("/playlist-name?platform=tencent&id=42",request.path)
        assertEquals(STUDY_SITE,request.getHeader("Origin"))
        assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
    }
    @Test fun upstreamFailureOrMalformedNamesReturnEmptyFallback() = mock { server,service ->
        for (response in listOf(MockResponse().setResponseCode(503),MockResponse().setBody("bad json"),
            MockResponse().setBody("{}"),MockResponse().setBody("{\"name\":null}"),MockResponse().setBody("{\"name\":42}"))) {
            server.enqueue(response)
            assertEquals("",service.fetch("netease","42"))
        }
    }
    @Test fun invalidKeysDoNotMakeRequests() = mock { server,service ->
        assertEquals("",service.fetch("other","42"))
        assertEquals("",service.fetch("netease","bad"))
        assertEquals("",service.fetch("netease","1".repeat(21)))
        assertEquals(0,server.requestCount)
    }
    @Test fun oversizedResponsesAndRedirectsDoNotBlockPlaylistLoading() = mock { server,service ->
        server.enqueue(MockResponse().setBody("{\"name\":\""+"a".repeat(140_000)+"\"}"))
        assertEquals("",service.fetch("netease","42"))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/other")))
        assertEquals("",service.fetch("netease","42"))
        assertEquals(2,server.requestCount)
    }
}
