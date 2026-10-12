package io.github.chorusfruit233.studywithmiku

import okhttp3.OkHttpClient
import okhttp3.CookieJar
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class StudyApiException(val status: Int,val operation: String = "请求"): IOException(
    if (status == 401) "登录已过期，请重新登录" else "${operation}失败：账号服务返回 HTTP $status，请稍后重试")
object StudyNetwork {
    val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .writeTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
}
class StudyApi(private val baseUrl: String = ACCOUNT_SITE,client: OkHttpClient = StudyNetwork.client) : StudySyncApi {
    private val client = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false).build()
    fun request(path: String,token: String = "",body: JSONObject? = null): JSONObject {
        return execute(path,token,body?.let(::encodeDocument))
    }
    private fun execute(path: String,token: String,body: ByteArray?): JSONObject {
        require(path.startsWith("/api/study/"))
        val builder = Request.Builder().url(baseUrl+path).header("Cache-Control","no-store").header("Pragma","no-cache")
            .header("User-Agent","StudyWithMiku-Android/1.2").header("Accept","application/json")
            .header("Origin",STUDY_SITE).header("Referer","$STUDY_SITE/")
        if (path.substringBefore('?') in listOf("/api/study/data","/api/study/calendar")) builder.header("Content-Type","application/json")
        if (token.isNotBlank()) builder.header("Authorization","Bearer $token")
        // Like the original cross-origin browser fetch: Bearer only, with an exact JSON media type.
        if (body != null) builder.put(body.toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val verb = if (body == null) "读取" else "上传"
                val section = when (path.substringBefore('?')) {
                    "/api/study/data" -> "学习资料"
                    "/api/study/calendar" -> "日历"
                    else -> "账号资料"
                }
                throw StudyApiException(response.code,verb+section)
            }
            val input = response.body ?: throw IOException("账号服务返回空响应")
            if (input.contentLength() > 8 * 1024 * 1024) throw IOException("账号服务响应过大")
            val source = input.source()
            source.request(8 * 1024 * 1024L + 1)
            if (source.buffer.size > 8 * 1024 * 1024L) throw IOException("账号服务响应过大")
            val text = source.readUtf8()
            if (body != null && text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
    override fun fetch(token: String): CloudStudy {
        fun dataAt(path: String): JSONObject {
            val response = request(path,token)
            if (!response.has("data")) throw IOException("账号服务返回了不完整的资料")
            if (response.isNull("data")) return JSONObject()
            return response.optJSONObject("data") ?: throw IOException("账号服务返回了无效的资料")
        }
        val data = dataAt("/api/study/data")
        val calendar = dataAt("/api/study/calendar")
        return CloudStudy(data,calendar)
    }
    override fun push(token: String,study: CloudStudy) {
        fun objectField(source: JSONObject,key: String): JSONObject {
            val value = source.opt(key)
            if (value == null || value == JSONObject.NULL) return JSONObject()
            if (value is JSONObject) return value
            throw IOException("学习资料中的 $key 格式无效，请先检查资料")
        }
        val todos = study.data.opt("todos")
        if (todos != null && todos != JSONObject.NULL && todos !is JSONArray) throw IOException("待办资料格式无效，请先检查资料")
        val data = JSONObject().put("stats",objectField(study.data,"stats"))
            .put("todos",todos ?: JSONArray()).put("settings",objectField(study.data,"settings"))
        if (todos == JSONObject.NULL) data.put("todos",JSONArray())
        val calendar = JSONObject().put("dailyLog",objectField(study.calendar,"dailyLog"))
            .put("plans",objectField(study.calendar,"plans"))
        // Validate and encode both documents before the first write to avoid preventable partial uploads.
        val dataBody = encodeDocument(data)
        val calendarBody = encodeDocument(calendar)
        execute("/api/study/data",token,dataBody)
        execute("/api/study/calendar",token,calendarBody)
    }
    private fun encodeDocument(document: JSONObject): ByteArray {
        fun validate(value: Any?) {
            when (value) {
                is Number -> if (!value.toDouble().isFinite()) throw IOException("学习资料含无效数值，请检查后重试")
                is JSONObject -> value.keys().forEach { validate(value.opt(it)) }
                is JSONArray -> for (index in 0 until value.length()) validate(value.opt(index))
            }
        }
        validate(document)
        return document.toString().toByteArray(Charsets.UTF_8)
    }
}
