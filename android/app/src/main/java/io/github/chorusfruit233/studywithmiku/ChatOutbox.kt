package io.github.chorusfruit233.studywithmiku

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class ChatDraft(val clientId: String,val userId: String,val username: String,val content: String,
    val location: String,val createdAt: Long,val attempts: Int = 0,val failure: String = "") {
    fun message(id: String = "pending:$clientId") = ChatMessage(id,userId,username,content,Instant.ofEpochMilli(createdAt).toString(),location,clientId = clientId)
}

/** Persist before sending. Retries require BOTH upstream deduplication and acknowledgement capabilities. */
class ChatOutbox(private val read: () -> String = { "[]" },private val write: (String) -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis) {
    private var entries = runCatching { JSONArray(read()).objects().map { o ->
        ChatDraft(o.getString("clientId"),o.getString("userId"),o.optString("username"),o.getString("content"),
            o.optString("location"),o.getLong("createdAt"),o.optInt("attempts"),o.optString("failure"))
    } }.getOrDefault(emptyList())
    private var inFlight: String? = null
    private var deadline = 0L
    private var nextSend = 0L
    var error: String? = null; private set
    fun pending(user: String) = entries.filter { it.userId == user && it.failure.isBlank() }
    fun failed(user: String) = entries.filter { it.userId == user && it.failure.isNotBlank() }
    private fun save(next: List<ChatDraft>): Boolean {
        val json = JSONArray().apply { next.forEach { d -> put(JSONObject().put("clientId",d.clientId).put("userId",d.userId)
            .put("username",d.username).put("content",d.content).put("location",d.location).put("createdAt",d.createdAt)
            .put("attempts",d.attempts).put("failure",d.failure)) } }
        if (!runCatching { write(json.toString()) }.getOrDefault(false)) { error = "无法保存待发送消息，请检查存储空间"; return false }
        entries = next; error = null; return true
    }
    fun enqueue(user: String,name: String,content: String,location: String): Boolean {
        if (entries.count { it.userId == user } >= 100) { error = "待发送消息过多，请先处理草稿"; return false }
        return save(entries+ChatDraft(UUID.randomUUID().toString(),user,name,content,location,now()))
    }
    fun disconnect() { inFlight = null; deadline = 0 }
    fun discard(user: String,id: String) = save(entries.filterNot { it.userId == user && it.clientId == id })
    fun confirm(user: String,id: String): ChatDraft? {
        val found = entries.find { it.userId == user && it.clientId == id } ?: return null
        if (!discard(user,id)) return null
        if (inFlight == id) { inFlight = null; deadline = 0 }
        return found
    }
    private fun fail(draft: ChatDraft,reason: String) {
        save(entries.map { if (it.clientId == draft.clientId) it.copy(failure = reason) else it })
        if (inFlight == draft.clientId) { inFlight = null; deadline = 0 }
    }
    fun rejected(user: String,id: String,code: String): Boolean {
        val draft = entries.find { it.userId == user && it.clientId == id } ?: return false
        if (inFlight == id) { inFlight = null; deadline = 0 }
        if (code in listOf("rate_limited","persistence_failed","server_misconfigured") && draft.attempts < 3) {
            nextSend = now()+if (code == "rate_limited") 1600 else 2000
        } else fail(draft,if (code == "unauthorized") "登录已失效，请重新登录后恢复草稿" else "发送失败，请恢复草稿后重试")
        return true
    }
    fun pump(user: String,ready: Boolean,reliable: Boolean,send: (ChatDraft) -> Boolean) {
        val time = now()
        pending(user).filter { time-it.createdAt > 24*60*60_000L }.forEach { fail(it,"消息已超过 24 小时，请恢复草稿后重试") }
        if (inFlight != null) {
            if (time < deadline) return
            val draft = entries.find { it.clientId == inFlight }
            inFlight = null
            if (draft != null && draft.attempts >= 3) fail(draft,"未收到服务器确认，请恢复草稿后重试")
        }
        if (!ready || time < nextSend) return
        val draft = pending(user).firstOrNull() ?: return
        if (draft.attempts >= 3 || (!reliable && draft.attempts > 0)) {
            fail(draft,"无法确认是否送达，请核对聊天记录后恢复草稿")
            return
        }
        val attempted = draft.copy(attempts = draft.attempts+1)
        if (!save(entries.map { if (it.clientId == draft.clientId) attempted else it })) return
        nextSend = time+1600
        // A false socket result means no frame was accepted; it is safe to keep waiting for a connection.
        if (!send(attempted)) {
            save(entries.map { if (it.clientId == draft.clientId) draft else it }); return
        }
        if (reliable) { inFlight = draft.clientId; deadline = time+10_000 }
        else confirm(user,draft.clientId)
    }
}
