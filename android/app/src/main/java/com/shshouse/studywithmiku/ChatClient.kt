package com.shshouse.studywithmiku

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class ChatState(val connected: Boolean = false,val authenticated: Boolean = false,val online: Int = 0,
    val adminOnline: Boolean = false,val messages: List<ChatMessage> = emptyList(),val hasMore: Boolean = false,
    val loadingHistory: Boolean = false,val error: String? = null)
data class UserProfile(val username: String = "",val avatar: String = "")
class ChatClient(private val auth: StudyAuth,private val api: StudyApi) {
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
    private var backoff = 3000L
    init {
        scope.launch { auth.state.map { Triple(it.userId,it.expiresAt,it.expired) }.distinctUntilChanged().collect {
            if (active) { disconnect(); connect() }
            if (auth.state.value.userId.isNotBlank()) ensureProfiles(listOf(auth.state.value.userId))
        } }
    }
    fun start() { if (!active) { active = true; connect() } }
    fun stop() { active = false; disconnect() }
    private fun disconnect() {
        generation++; retry?.cancel(); heartbeat?.cancel(); historyTimeout?.cancel()
        socket?.close(1000,"screen inactive"); socket = null
        _state.value = _state.value.copy(connected = false,authenticated = false,online = 0,adminOnline = false,loadingHistory = false)
    }
    private fun connect() {
        if (!active || socket != null) return
        val serial = ++generation
        val request = Request.Builder().url(STUDY_WS).header("Origin",STUDY_SITE).build()
        socket = StudyNetwork.client.newWebSocket(request,object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket,response: Response) { scope.launch {
                if (serial != generation || !active) { webSocket.close(1000,"inactive"); return@launch }
                backoff = 3000L
                _state.value = _state.value.copy(connected = true,error = null)
                val token = auth.token()
                if (token.isNotEmpty()) webSocket.send(JSONObject().put("type","auth").put("token",token).toString())
                webSocket.send(JSONObject().put("type","username").put("username",auth.state.value.username).toString())
                heartbeat?.cancel()
                heartbeat = scope.launch { while (isActive) { delay(30_000); send(JSONObject().put("type","ping")) } }
            } }
            override fun onMessage(webSocket: WebSocket,text: String) { scope.launch {
                if (serial != generation || !active) return@launch
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
        socket = null; heartbeat?.cancel(); historyTimeout?.cancel()
        _state.value = _state.value.copy(connected = false,authenticated = false,online = 0,adminOnline = false,loadingHistory = false,error = error)
        if (active) { retry?.cancel(); retry = scope.launch { delay(backoff); backoff = (backoff*2).coerceAtMost(30_000); connect() } }
    }
    fun reconnect() { disconnect(); if (active) connect() }
    private fun send(o: JSONObject) = socket?.send(o.toString()) == true
    fun sendChat(content: String,location: String = ""): Boolean {
        val text = content.trim()
        if (text.isEmpty() || text.length > 500) { _state.value = _state.value.copy(error = "消息需为 1–500 个字符"); return false }
        if (!_state.value.connected || !_state.value.authenticated || auth.token().isBlank()) {
            _state.value = _state.value.copy(error = "登录并连接聊天室后才能发送"); return false
        }
        _state.value = _state.value.copy(error = null)
        return send(JSONObject().put("type","chat").put("content",text).put("location",location))
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
            "auth" -> _state.value = s.copy(authenticated = o.optBoolean("ok"),error = if (o.optBoolean("ok")) null else "聊天身份验证失败，请重新登录")
            "history","history_chunk" -> {
                val incoming = o.optJSONArray("messages")?.objects()?.mapNotNull(StudyProtocol::chat) ?: emptyList()
                val messages = if (o.optString("type") == "history") StudyProtocol.mergeMessages(incoming) else StudyProtocol.mergeMessages(incoming,s.messages)
                historyTimeout?.cancel()
                _state.value = s.copy(messages = messages,hasMore = o.optBoolean("hasMore"),loadingHistory = false)
                ensureProfiles(messages.map { it.userId })
            }
            "chat" -> o.optJSONObject("message")?.let(StudyProtocol::chat)?.let { message ->
                _state.value = s.copy(messages = StudyProtocol.mergeMessages(s.messages,listOf(message)))
                ensureProfiles(listOf(message.userId))
            }
            "error" -> { historyTimeout?.cancel(); _state.value = s.copy(error = o.optString("message","连接异常"),loadingHistory = false) }
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
