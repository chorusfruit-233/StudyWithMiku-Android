package io.github.chorusfruit233.studywithmiku

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

/** The original browser sends three study fields, two calendar fields and an exact JSON media type. */
class SyncWireContractTest {
    private fun mock(test: (MockWebServer,StudyApi) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            test(server,StudyApi(server.url("/").toString().trimEnd('/')))
        }
    }

    @Test fun uploadProjectsWritableDocumentsAndRetainsExtrasInsideRecordsAndLocalSnapshots() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(204))
        val study = CloudStudy(JSONObject("""{"stats":{"totalStudyTime":1.001},"todos":[{"id":1,"text":"任务","extra":"keep"}],"settings":{"focusDuration":6.1},"serverOnly":"retain locally"}"""),
            JSONObject("""{"dailyLog":{"2026-10-10":{"studyTime":1.001,"extra":"keep"}},"plans":{},"updated_at":"retain locally"}"""))
        val before = study.data.toString()+study.calendar.toString()
        api.push("fixture-token",study)
        val data = JSONObject(server.takeRequest().body.readUtf8())
        val calendar = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(setOf("stats","todos","settings"),data.keys().asSequence().toSet())
        assertEquals(setOf("dailyLog","plans"),calendar.keys().asSequence().toSet())
        assertEquals("keep",data.getJSONArray("todos").getJSONObject(0).getString("extra"))
        assertEquals("keep",calendar.getJSONObject("dailyLog").getJSONObject("2026-10-10").getString("extra"))
        assertEquals(1.001,data.getJSONObject("stats").getDouble("totalStudyTime"),0.0)
        assertEquals(before,study.data.toString()+study.calendar.toString())
    }

    @Test fun uploadUsesOriginalExactJsonContentTypeUtf8AndBearerWithoutCookies() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(204))
        api.push("fixture-token",CloudStudy(JSONObject("""{"todos":[{"id":1,"text":"中文 ✓"}]}""")))
        val first = server.takeRequest(); val second = server.takeRequest()
        assertEquals("application/json",first.getHeader("Content-Type"))
        assertEquals("application/json",second.getHeader("Content-Type"))
        assertEquals("Bearer fixture-token",first.getHeader("Authorization"))
        assertNull(first.getHeader("Cookie")); assertNull(second.getHeader("Cookie"))
        assertEquals("中文 ✓",JSONObject(first.body.readUtf8()).getJSONArray("todos").getJSONObject(0).getString("text"))
    }

    @Test fun firstWriteFailureNamesStudyDataAndDoesNotAttemptCalendarOrRetry() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(500).setBody("server-private-details"))
        val error = assertThrows(StudyApiException::class.java) { api.push("fixture-token",CloudStudy()) }
        assertEquals(500,error.status); assertTrue(error.message!!.contains("上传学习资料"))
        assertFalse(error.message!!.contains("server-private-details"))
        assertEquals(1,server.requestCount); assertEquals("/api/study/data",server.takeRequest().path)
    }

    @Test fun secondWriteFailureNamesCalendarAndDoesNotPretendUploadSucceeded() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(500))
        val error = assertThrows(StudyApiException::class.java) { api.push("fixture-token",CloudStudy()) }
        assertEquals(500,error.status); assertTrue(error.message!!.contains("上传日历"))
        assertEquals(2,server.requestCount)
        assertEquals("/api/study/data",server.takeRequest().path)
        assertEquals("/api/study/calendar",server.takeRequest().path)
    }

    @Test fun invalidCalendarNumberIsRejectedBeforeEitherDocumentIsWritten() = mock { server,api ->
        // JSON can hold this BigDecimal, but the browser/server number range cannot represent it.
        val invalidDay = JSONObject().put("studyTime",BigDecimal("1e10000"))
        val calendar = JSONObject().put("dailyLog",JSONObject().put("2026-10-10",invalidDay))
        val error = assertThrows(IOException::class.java) { api.push("fixture-token",CloudStudy(calendar = calendar)) }
        assertTrue(error.message!!.contains("无效数值")); assertEquals(0,server.requestCount)
    }

    @Test fun sharedClientCookieJarCannotAddBrowserCredentialsToAccountRequests() {
        MockWebServer().use { server ->
            server.start()
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl,cookies: List<Cookie>) {}
                override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().domain(url.host).name("session").value("fixture-cookie").build())
            }
            val api = StudyApi(server.url("/").toString().trimEnd('/'),OkHttpClient.Builder().cookieJar(jar).build())
            server.enqueue(MockResponse().setBody("{\"data\":{}}")); server.enqueue(MockResponse().setBody("{\"data\":{}}"))
            api.fetch("fixture-token")
            repeat(2) {
                val request = server.takeRequest()
                assertNull(request.getHeader("Cookie")); assertEquals("Bearer fixture-token",request.getHeader("Authorization"))
            }
        }
    }
}
