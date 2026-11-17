package io.github.chorusfruit233.studywithmiku

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StudyRevisionTest {
    private fun mock(test: (MockWebServer,StudyApi) -> Unit) {
        MockWebServer().use { it.start(); test(it,StudyApi(it.url("/").toString().trimEnd('/'))) }
    }
    private fun data(revision: Int,time: Int,text: String = "a",done: Boolean = false) = JSONObject("""{
        "revision":$revision,"stats":{"totalStudyTime":$time},"todos":[{"id":1,"text":"$text","completed":$done}],"settings":{}
    }""")
    private fun response(data: JSONObject,code: Int = 200) = MockResponse().setResponseCode(code).setBody(JSONObject().put("data",data).toString())
    @Test fun conflictRebasesLocalDeltaAndIndependentTodoEditsOntoLatestRevision() = mock { server,api ->
        server.enqueue(response(data(2,130,"web edit"),409))
        server.enqueue(response(data(3,190,"web edit",true)))
        server.enqueue(MockResponse().setResponseCode(204))
        val base = CloudStudy(data(1,100)); val local = CloudStudy(data(1,160,done = true))
        val saved = api.push("token",local,base)
        val first = JSONObject(server.takeRequest().body.readUtf8()); val second = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1,first.getInt("expectedRevision")); assertEquals(2,second.getInt("expectedRevision"))
        assertFalse(first.has("revision")); assertEquals(190,second.getJSONObject("stats").getInt("totalStudyTime"))
        assertEquals("web edit",second.getJSONArray("todos").getJSONObject(0).getString("text"))
        assertTrue(second.getJSONArray("todos").getJSONObject(0).getBoolean("completed"))
        assertEquals(3,saved.data.getInt("revision")); assertEquals("/api/study/calendar",server.takeRequest().path)
        assertEquals(160,local.data.getJSONObject("stats").getInt("totalStudyTime"))
    }
    @Test fun repeatedConflictsAreBoundedAndDoNotWriteCalendar() = mock { server,api ->
        repeat(3) { server.enqueue(response(data(it+2,130+it*10),409)) }
        val error = assertThrows(IOException::class.java) { api.push("token",CloudStudy(data(1,160)),CloudStudy(data(1,100))) }
        assertTrue(error.message!!.contains("持续变化")); assertEquals(3,server.requestCount)
        assertEquals(listOf(160,190,200),List(3) { JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("stats").getInt("totalStudyTime") })
    }
    @Test fun missingOrNonIncreasingRevisionCannotPretendUploadSucceeded() = mock { server,api ->
        for (reply in listOf(JSONObject(),data(1,160),data(0,160))) {
            server.enqueue(response(reply))
            assertThrows(IOException::class.java) { api.push("token",CloudStudy(data(1,160)),CloudStudy(data(1,100))) }
        }
        assertEquals(3,server.requestCount)
    }
    @Test fun malformedConflictDoesNotOverwriteCloudUsingLegacyFallback() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(409).setBody("{}"))
        assertThrows(IOException::class.java) { api.push("token",CloudStudy(data(1,160)),CloudStudy(data(1,100))) }
        assertEquals(1,server.requestCount)
    }
    @Test fun legacySnapshotKeepsOldWireShapeAndNoContentReply() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(204))
        val base = CloudStudy(data(1,100).apply { remove("revision") })
        api.push("token",base,base)
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("expectedRevision"))
    }
    @Test fun newAccountUsesRevisionZeroAndCalendarFailureExposesSavedCheckpoint() = mock { server,api ->
        server.enqueue(MockResponse().setBody("{\"data\":null}")); server.enqueue(MockResponse().setBody("{\"data\":null}"))
        val baseline = api.fetch("token")
        server.enqueue(response(data(1,60))); server.enqueue(MockResponse().setResponseCode(500))
        val error = assertThrows(StudyPartialUploadException::class.java) { api.push("token",CloudStudy(data(0,60)),baseline) }
        assertEquals(1,error.saved.data.getInt("revision")); assertEquals(500,(error.cause as StudyApiException).status)
        repeat(2) { server.takeRequest() }
        assertEquals(0,JSONObject(server.takeRequest().body.readUtf8()).getInt("expectedRevision"))
    }
    @Test fun logoutPostsBearerWithoutCookiesAndTreatsExpiredTokenAsAlreadyLoggedOut() = mock { server,api ->
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(401))
        repeat(2) { api.logout("token")
            val request = server.takeRequest()
            assertEquals("POST",request.method); assertEquals("/api/study/logout",request.path)
            assertEquals("Bearer token",request.getHeader("Authorization")); assertEquals("no-store",request.getHeader("Cache-Control"))
            assertEquals("application/json",request.getHeader("Content-Type")); assertEquals(0L,request.bodySize)
            assertNull(request.getHeader("Cookie"))
        }
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(500,assertThrows(StudyApiException::class.java) { api.logout("token") }.status)
    }
    @Test fun writableProjectionDoesNotDropReadOnlyMetadataFromSavedLocalSnapshot() = mock { server,api ->
        for (versioned in listOf(false,true)) {
            val study = CloudStudy(data(1,60).put("serverOnly","retain locally"),JSONObject().put("updated_at","retain calendar metadata"))
            val base = CloudStudy(data(1,60).apply { if (!versioned) remove("revision") })
            server.enqueue(if (versioned) response(data(2,60).put("serverStamp","new")) else MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            val saved = api.push("token",study,base)
            assertEquals("retain locally",saved.data.getString("serverOnly"))
            assertEquals("retain calendar metadata",saved.calendar.getString("updated_at"))
            if (versioned) assertEquals("new",saved.data.getString("serverStamp"))
            else assertFalse("A legacy server must not inherit a stale local revision",saved.data.has("revision"))
            assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("serverOnly"))
            assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("updated_at"))
        }
    }

}
