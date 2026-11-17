package io.github.chorusfruit233.studywithmiku

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class ChatState(val connected: Boolean = false,val authenticated: Boolean = false,val online: Int = 0,
    val adminOnline: Boolean = false,val messages: List<ChatMessage> = emptyList(),val hasMore: Boolean = false,
    val loadingHistory: Boolean = false,val error: String? = null,
    val pending: List<ChatDraft> = emptyList(),val drafts: List<ChatDraft> = emptyList())
data class UserProfile(val username: String = "",val avatar: String = "")
class ChatClient(private val auth: StudySyncAccount,private val api: StudyApi,
    private val client: WebSocket.Factory = StudyNetwork.client,private val wsUrl: String = STUDY_WS,private val outbox: ChatOutbox = ChatOutbox()) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()
    private val _profiles = MutableStateFlow<Map<String,UserProfile>>(emptyMap())
    val profiles = _profiles.asStateFlow()
    private val profileExpiry = ConcurrentHashMap<String,Long>()
    private val fetching = mutableSetOf<String>()
    private var socket: WebSocket? = null
    private var active = false
    private var generation = 0
    private var retry: Job? = null
    private var heartbeat: Job? = null
    private var historyTimeout: Job? = null
    private var backoff = 1000L
    private var reliable = false
    private var lastInbound = 0L
    init {
        scope.launch { auth.state.map { Triple(it.userId,it.expiresAt,it.expired) }.distinctUntilChanged().collect {
            outbox.disconnect(); publishOutbox()
            if (active) { disconnect(); connect() }
            if (auth.state.value.userId.isNotBlank()) ensureProfiles(listOf(auth.state.value.userId))
        } }
    }
    private fun publishOutbox() {
        val user = auth.state.value.userId
        _state.value = _state.value.copy(pending = outbox.pending(user),drafts = outbox.failed(user),error = outbox.error ?: _state.value.error)
    }
    fun discardDraft(id: String): Boolean = outbox.discard(auth.state.value.userId,id).also { publishOutbox() }
    private fun pump() {
        outbox.pump(auth.state.value.userId,active && state.value.connected && state.value.authenticated && auth.token().isNotBlank(),reliable) {
            send(JSONObject().put("type","chat").put("content",it.content).put("location",it.location).put("clientId",it.clientId))
        }
        publishOutbox()
    }
    fun start() { if (!active) { active = true; connect() } }
    fun stop() { active = false; disconnect() }
    internal fun close() { stop(); scope.cancel() }
    private fun disconnect() {
        reliable = false; outbox.disconnect()
        generation++; retry?.cancel(); heartbeat?.cancel(); historyTimeout?.cancel()
        socket?.close(1000,"screen inactive"); socket = null
        _state.value = _state.value.copy(connected = false,authenticated = false,online = 0,adminOnline = false,loadingHistory = false)
    }
    private fun connect() {
        if (!active || socket != null) return
        val serial = ++generation
        val request = Request.Builder().url(wsUrl).header("Origin",STUDY_SITE).build()
        socket = client.newWebSocket(request,object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket,response: Response) { scope.launch {
                if (serial != generation || !active) { webSocket.close(1000,"inactive"); return@launch }
                backoff = 1000L; lastInbound = System.currentTimeMillis()
                _state.value = _state.value.copy(connected = true,error = null)
                val token = auth.token()
                if (token.isNotEmpty()) webSocket.send(JSONObject().put("type","auth").put("token",token).toString())
                webSocket.send(JSONObject().put("type","username").put("username",auth.state.value.username).toString())
                heartbeat?.cancel()
                heartbeat = scope.launch {
                    var lastPing = System.currentTimeMillis()
                    while (isActive) {
                        delay(500)
                        val now = System.currentTimeMillis()
                        if (now-lastInbound > 45_000) { webSocket.cancel(); lost(serial,"连接超时，正在重连…"); return@launch }
                        if (now-lastPing >= 25_000) { send(JSONObject().put("type","ping")); lastPing = now }
                        pump()
                    }
                }
            } }
            override fun onMessage(webSocket: WebSocket,text: String) { scope.launch {
                if (serial != generation || !active) return@launch
                lastInbound = System.currentTimeMillis()
                runCatching { receive(JSONObject(text)) }.onFailure { _state.value = _state.value.copy(error = "聊天消息格式异常，请重新连接") }
            } }
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) { webSocket.close(code,reason) }
            override fun onClosed(webSocket: WebSocket,code: Int,reason: String) { scope.launch { lost(serial,null) } }
            override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) { scope.launch {
                lost(serial,if (response != null) "聊天室连接失败（HTTP ${response.code}）" else "聊天室连接中断，正在重连…")
            } }
        })
    }
    private fun lost(serial: Int,error: String?) {
        if (serial != generation) return
        generation++; reliable = false; outbox.disconnect()
        socket = null; heartbeat?.cancel(); historyTimeout?.cancel()
        _state.value = _state.value.copy(connected = false,authenticated = false,online = 0,adminOnline = false,loadingHistory = false,error = error)
        if (active) { retry?.cancel(); retry = scope.launch { delay(backoff); backoff = (backoff*2).coerceAtMost(15_000); connect() } }
    }
    fun reconnect() { disconnect(); if (active) connect() }
    private fun send(o: JSONObject) = socket?.send(o.toString()) == true
    fun sendChat(content: String,location: String = ""): Boolean {
        val text = content.trim()
        if (text.isEmpty() || text.length > 500) { _state.value = _state.value.copy(error = "消息需为 1–500 个字符"); return false }
        if (auth.token().isBlank()) {
            _state.value = _state.value.copy(error = "登录并连接聊天室后才能发送"); return false
        }
        _state.value = _state.value.copy(error = null)
        val queued = outbox.enqueue(auth.state.value.userId,auth.state.value.username,text,location)
        pump()
        return queued
    }
    fun history() {
        val s = _state.value
        val cursor = s.messages.firstOrNull()?.timestamp ?: return
        if (!s.connected || !s.hasMore || s.loadingHistory || cursor <= 0) return
        if (send(JSONObject().put("type","load_history").put("before",cursor).put("limit",50))) {
            _state.value = s.copy(loadingHistory = true,error = null)
            historyTimeout?.cancel(); historyTimeout = scope.launch { delay(15_000); _state.value = _state.value.copy(loadingHistory = false,error = "历史消息加载超时，请重试") }
        }
    }
    private fun receive(o: JSONObject) {
        val s = _state.value
        when (o.optString("type")) {
            "count" -> _state.value = s.copy(online = o.optInt("count").coerceAtLeast(0),adminOnline = o.optBoolean("adminOnline"))
            "hello","auth" -> {
                if (o.optString("type") == "hello" || o.optBoolean("ok")) {
                    o.optJSONArray("caps")?.let { caps ->
                        val names = (0 until caps.length()).map { caps.optString(it) }
                        reliable = "client_id_v1" in names && "chat_ack_v1" in names
                    }
                }
                if (o.optString("type") == "auth") _state.value = s.copy(authenticated = o.optBoolean("ok"),error = if (o.optBoolean("ok")) null else "聊天身份验证失败，请重新登录")
                pump()
            }
            "chat_ack" -> {
                val id = o.optString("id")
                if (id.isNotBlank()) outbox.confirm(auth.state.value.userId,o.optString("clientId"))?.let { draft ->
                    if (s.messages.none { it.id == id }) _state.value = s.copy(messages = StudyProtocol.mergeMessages(s.messages,listOf(draft.message(id))))
                }
                publishOutbox()
            }
            "history","history_chunk" -> {
                val incoming = o.optJSONArray("messages")?.objects()?.mapNotNull(StudyProtocol::chat) ?: emptyList()
                val messages = StudyProtocol.mergeMessages(incoming,s.messages)
                historyTimeout?.cancel()
                _state.value = s.copy(messages = messages,hasMore = o.optBoolean("hasMore"),loadingHistory = false)
                incoming.filter { it.userId == auth.state.value.userId && it.clientId.isNotBlank() }.forEach { outbox.confirm(it.userId,it.clientId) }
                publishOutbox()
                ensureProfiles(messages.map { it.userId })
            }
            "chat" -> o.optJSONObject("message")?.let(StudyProtocol::chat)?.let { message ->
                _state.value = s.copy(messages = StudyProtocol.mergeMessages(s.messages,listOf(message)))
                if (message.userId == auth.state.value.userId && message.clientId.isNotBlank()) outbox.confirm(message.userId,message.clientId)
                publishOutbox()
                ensureProfiles(listOf(message.userId))
            }
            "error" -> {
                historyTimeout?.cancel()
                outbox.rejected(auth.state.value.userId,o.optString("clientId"),o.optString("code"))
                _state.value = s.copy(error = o.optString("message","连接异常"),loadingHistory = false)
                if (o.optString("code") == "unauthorized") {
                    _state.value = _state.value.copy(authenticated = false)
                    val token = auth.token()
                    if (token.isNotBlank()) send(JSONObject().put("type","auth").put("token",token))
                }
                publishOutbox()
            }
        }
    }
    fun ensureProfiles(ids: List<String>) {
        val now = System.currentTimeMillis()
        val wanted = ids.distinct().filter { it.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) &&
            (profileExpiry[it] ?: 0) < now && it !in fetching }
        fetching.addAll(wanted)
        wanted.chunked(50).forEach { batch -> scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { api.request("/api/study/profiles?ids=${batch.sorted().joinToString(",")}") }.optJSONObject("profiles") ?: JSONObject()
                val profiles = _profiles.value.toMutableMap()
                batch.forEach { id ->
                    result.optJSONObject(id)?.let { profiles[id] = UserProfile(it.optString("username"),it.optString("avatar_url")) }
                    profileExpiry[id] = System.currentTimeMillis() + if (result.has(id)) 6*60*60_000 else 10*60_000
                }
                _profiles.value = profiles
            } catch (_: Exception) { batch.forEach { profileExpiry[it] = System.currentTimeMillis()+60_000 } }
            finally { fetching.removeAll(batch.toSet()) }
        } }
    }
}
