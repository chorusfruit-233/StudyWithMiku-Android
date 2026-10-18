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
        override fun request() = original
        override fun queueSize() = 0L
        override fun send(text: String) = accepts
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
    @Test fun socketRejectingSendReportsFailureAndSuccessfulRetryClearsIt() {
        val factory = Factory(); val chat = ChatClient(Account(),StudyApi(),factory)
        try {
            chat.start(); factory.open(); factory.socket.accepts = false
            assertFalse(chat.sendChat("Fixture message"))
            assertEquals("发送失败：连接已断开，请稍后重试",chat.state.value.error)
            factory.socket.accepts = true
            assertTrue(chat.sendChat("Fixture retry")); assertNull(chat.state.value.error)
            chat.stop()
            assertFalse(chat.sendChat("[sticker:12]"))
            assertEquals("发送失败：连接已断开，请稍后重试",chat.state.value.error)
        } finally { chat.close() }
    }
}
