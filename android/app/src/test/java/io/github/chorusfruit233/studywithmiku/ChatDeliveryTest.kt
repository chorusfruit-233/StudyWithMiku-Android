package io.github.chorusfruit233.studywithmiku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDeliveryTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private class Account : StudySyncAccount {
        override val state = MutableStateFlow(AccountState("fixture","Fixture",Long.MAX_VALUE))
        override fun token() = "fixture-token"
        override fun expire() {}
        override fun logout() {}
    }
    private class Socket(private val original: Request) : WebSocket {
        var accepts = true
        val frames = mutableListOf<String>()
        override fun request() = original
        override fun queueSize() = 0L
        override fun send(text: String): Boolean { if (accepts) frames.add(text); return accepts }
        override fun send(bytes: ByteString) = accepts
        override fun close(code: Int,reason: String?) = true
        override fun cancel() {}
    }
    private class Factory : WebSocket.Factory {
        lateinit var socket: Socket
        lateinit var listener: WebSocketListener
        override fun newWebSocket(request: Request,listener: WebSocketListener): WebSocket {
            this.listener = listener; socket = Socket(request); return socket
        }
        fun open() {
            listener.onOpen(socket,Response.Builder().request(socket.request()).protocol(Protocol.HTTP_1_1).code(101).message("Fixture").build())
            receive("""{"type":"auth","ok":true}""")
        }
        fun receive(json: String) { listener.onMessage(socket,json) }
    }
    @Test fun initialAndRepeatedHistoryPreserveLiveMessagesAndRemoveDuplicates() {
        val factory = Factory(); val chat = ChatClient(Account(),StudyApi(),factory)
        try {
            chat.start(); factory.open()
            factory.receive("""{"type":"chat","message":{"id":"live","content":"Latest live text","createdAt":"2026-10-12T00:00:00Z"}}""")
            factory.receive("""{"type":"history","messages":[{"id":"old","content":"History","createdAt":"2026-10-11T00:00:00Z"},{"id":"live","content":"Stale copy","createdAt":"2026-10-12T00:00:00Z"}],"hasMore":true}""")
            assertEquals(listOf("old","live"),chat.state.value.messages.map { it.id })
            assertEquals("Latest live text",chat.state.value.messages.last().content)
            factory.receive("""{"type":"history","messages":[],"hasMore":false}""")
            assertEquals(2,chat.state.value.messages.size)
            factory.receive("""{"type":"history_chunk","messages":[{"id":"earliest","createdAt":"2026-10-10T00:00:00Z"},{"id":"old","createdAt":"2026-10-11T00:00:00Z"}]}""")
            assertEquals(listOf("earliest","old","live"),chat.state.value.messages.map { it.id })
        } finally { chat.close() }
    }
    @Test fun disconnectedAndRejectedMessagesStayQueuedAndAckWithoutBroadcastAddsConfirmedBubble() {
        val factory = Factory(); val chat = ChatClient(Account(),StudyApi(),factory)
        try {
            assertTrue(chat.sendChat("Offline fixture")); assertEquals(1,chat.state.value.pending.size)
            chat.start()
            factory.receive("""{"type":"hello","caps":["client_id_v1","chat_ack_v1"]}""")
            factory.open()
            val sent = factory.socket.frames.map { JSONObject(it) }.single { it.optString("type") == "chat" }
            val id = sent.getString("clientId")
            factory.receive("""{"type":"chat_ack","clientId":"unknown","id":"unknown"}""")
            assertEquals(1,chat.state.value.pending.size); assertTrue(chat.state.value.messages.isEmpty())
            factory.receive("""{"type":"chat_ack","clientId":"$id","id":"$id"}""")
            assertTrue(chat.state.value.pending.isEmpty()); assertEquals("Offline fixture",chat.state.value.messages.single().content)
            factory.receive("""{"type":"chat","message":{"id":"$id","clientId":"$id","userId":"fixture","content":"Offline fixture","createdAt":"2026-11-17T00:00:00Z"}}""")
            assertEquals(1,chat.state.value.messages.size)
            chat.stop(); assertTrue(chat.sendChat("[sticker:21]")); assertEquals(1,chat.state.value.pending.size)
        } finally { chat.close() }
    }
    @Test fun missingServerCapabilitiesUsesSingleSendWithoutPendingAck() {
        val factory = Factory(); val chat = ChatClient(Account(),StudyApi(),factory)
        try {
            chat.start(); factory.open()
            assertTrue(chat.sendChat("Legacy fixture")); assertTrue(chat.state.value.pending.isEmpty())
            assertEquals(1,factory.socket.frames.map { JSONObject(it) }.count { it.optString("type") == "chat" })
            factory.socket.accepts = false
            assertTrue(chat.sendChat("Waiting fixture")); assertEquals(1,chat.state.value.pending.size)
        } finally { chat.close() }
    }
}
