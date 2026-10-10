package io.github.chorusfruit233.studywithmiku

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.util.concurrent.TimeUnit

class StudyApiTest {
    private fun mock(test: (MockWebServer,StudyApi)->Unit) {
        MockWebServer().use { server -> server.start(); test(server,StudyApi(server.url("/").toString().trimEnd('/'),cookies = { "session=test-session" })) }
    }
    @Test fun downloadsBothDocumentsAndSendsOriginalAuthenticationHeaders() = mock { server,api ->
        server.enqueue(MockResponse().setBody("""{"data":{"stats":{"totalStudyTime":60}}}"""))
        server.enqueue(MockResponse().setBody("""{"data":{"dailyLog":{}}}"""))
        val cloud = api.fetch("test-token")
        assertEquals(60,cloud.data.getJSONObject("stats").getInt("totalStudyTime"))
        val first = server.takeRequest(2,TimeUnit.SECONDS)!!; val second = server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("/api/study/data",first.path); assertEquals("/api/study/calendar",second.path)
        assertEquals("GET",first.method); assertEquals("Bearer test-token",first.getHeader("Authorization"))
        assertEquals("session=test-session",first.getHeader("Cookie")); assertEquals("no-store",first.getHeader("Cache-Control"))
    }
    @Test fun uploadsJsonDocumentsAndAcceptsNoContentReplies() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(204))
        api.push("test-token",CloudStudy(JSONObject().put("todos",org.json.JSONArray()),JSONObject().put("dailyLog",JSONObject())))
        val data = server.takeRequest(2,TimeUnit.SECONDS)!!; val calendar = server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("PUT",data.method); assertTrue(data.getHeader("Content-Type")!!.startsWith("application/json"))
        assertTrue(JSONObject(data.body.readUtf8()).has("todos")); assertTrue(JSONObject(calendar.body.readUtf8()).has("dailyLog"))
    }
    @Test fun missingRemoteAccountIsDistinctFromMalformedResponse() = mock { server,api ->
        server.enqueue(MockResponse().setBody("{\"data\":null}")); server.enqueue(MockResponse().setBody("{\"data\":null}"))
        assertEquals(0,api.fetch("token").data.length())
        server.enqueue(MockResponse().setBody("{}"))
        assertThrows(IOException::class.java) { api.fetch("token") }
        assertEquals(3,server.requestCount)
    }
    @Test fun failedFirstReadDoesNotUploadOrContinueToSecondRead() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(503,assertThrows(StudyApiException::class.java) { api.fetch("token") }.status)
        assertEquals(1,server.requestCount)
        assertEquals("GET",server.takeRequest().method)
    }
    @Test fun loginExpirationIsExplicitAndDoesNotEchoServerSecrets() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(401).setBody("secret-body"))
        val error = assertThrows(StudyApiException::class.java) { api.request("/api/study/data","token") }
        assertEquals(401,error.status); assertFalse(error.message!!.contains("secret-body"))
    }
    @Test fun accountRequestsNeverFollowRedirectsWithCredentials() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/other")))
        assertEquals(302,assertThrows(StudyApiException::class.java) { api.request("/api/study/data","token") }.status)
        assertEquals(1,server.requestCount)
        assertThrows(IllegalArgumentException::class.java) { api.request("/other","token") }
        assertEquals(1,server.requestCount)
    }
    @Test fun publicProfilesDoNotReceiveBearerTokens() = mock { server,api ->
        server.enqueue(MockResponse().setBody("{\"profiles\":{}}"))
        api.request("/api/study/profiles?ids=uid")
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
