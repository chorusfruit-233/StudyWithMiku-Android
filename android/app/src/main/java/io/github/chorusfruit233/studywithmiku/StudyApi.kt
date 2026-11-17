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

class StudyApiException(val status: Int,val operation: String = "请求",val payload: JSONObject? = null): IOException(
    if (status == 401) "登录已过期，请重新登录" else "${operation}失败：账号服务返回 HTTP $status，请稍后重试")
class StudyPartialUploadException(val saved: CloudStudy,cause: Exception): IOException(cause.message,cause)
object StudyNetwork {
    val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .writeTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
}
class StudyApi(private val baseUrl: String = ACCOUNT_API_SITE,client: OkHttpClient = StudyNetwork.client) : StudySyncApi {
    private val client = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false).build()
    fun request(path: String,token: String = "",body: JSONObject? = null): JSONObject {
        return execute(path,token,body?.let(::encodeDocument))
    }
    private fun execute(path: String,token: String,body: ByteArray?,post: Boolean = false): JSONObject {
        require(path.startsWith("/api/study/"))
        val builder = Request.Builder().url(baseUrl+path).header("Cache-Control","no-store").header("Pragma","no-cache")
            .header("User-Agent","StudyWithMiku-Android/1.2").header("Accept","application/json")
            .header("Origin",STUDY_SITE).header("Referer","$STUDY_SITE/")
        if (path.substringBefore('?') in listOf("/api/study/data","/api/study/calendar","/api/study/logout")) builder.header("Content-Type","application/json")
        if (token.isNotBlank()) builder.header("Authorization","Bearer $token")
        // Like the original cross-origin browser fetch: Bearer only, with an exact JSON media type.
        if (post) builder.post(ByteArray(0).toRequestBody(null))
        else if (body != null) builder.put(body.toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val verb = if (body == null) "读取" else "上传"
                val section = when (path.substringBefore('?')) {
                    "/api/study/data" -> "学习资料"
                    "/api/study/calendar" -> "日历"
                    else -> "账号资料"
                }
                val payload = if (response.code == 409) runCatching { JSONObject(response.peekBody(8 * 1024 * 1024).string()) }.getOrNull() else null
                throw StudyApiException(response.code,if (post) "退出登录" else verb+section,payload)
            }
            if (post) return@use JSONObject()
            val input = response.body ?: throw IOException("账号服务返回空响应")
            if (input.contentLength() > 8 * 1024 * 1024) throw IOException("账号服务响应过大")
            val source = input.source()
            source.request(8 * 1024 * 1024L + 1)
            if (source.buffer.size > 8 * 1024 * 1024L) throw IOException("账号服务响应过大")
            val text = source.readUtf8()
            if ((body != null || post) && text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
    override fun fetch(token: String): CloudStudy {
        fun dataAt(path: String): JSONObject {
            val response = request(path,token)
            if (!response.has("data")) throw IOException("账号服务返回了不完整的资料")
            if (response.isNull("data")) return if (path == "/api/study/data") JSONObject().put("revision",0) else JSONObject()
            return response.optJSONObject("data") ?: throw IOException("账号服务返回了无效的资料")
        }
        val data = dataAt("/api/study/data")
        val calendar = dataAt("/api/study/calendar")
        return CloudStudy(data,calendar)
    }
    override fun logout(token: String) {
        try { execute("/api/study/logout",token,null,post = true) }
        catch (e: StudyApiException) { if (e.status != 401) throw e }
    }
    override fun push(token: String,study: CloudStudy) { upload(token,study,null) }
    override fun push(token: String,study: CloudStudy,baseline: CloudStudy?) = upload(token,study,baseline)
    private fun revision(data: JSONObject?): Long? = (data?.opt("revision") as? Number)?.toDouble()?.takeIf {
        it.isFinite() && it >= 0 && it <= 9_007_199_254_740_991.0 && it % 1.0 == 0.0
    }?.toLong()
    private fun upload(token: String,study: CloudStudy,baseline: CloudStudy?): CloudStudy {
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
        fun retained(original: JSONObject,document: JSONObject) = StudyProtocol.deepCopy(original).apply {
            document.keys().forEach { key -> put(key,document.get(key)) }
        }
        // Projection is a wire concern. Keep read-only/forward-compatible fields in the local shadow.
        var saved = retained(study.data,data)
        var expected = revision(baseline?.data)
        if (expected == null) execute("/api/study/data",token,dataBody)
        else {
            var base = baseline?.data
            var pending = data
            for (attempt in 0..2) {
                try {
                    val response = execute("/api/study/data",token,encodeDocument(StudyProtocol.deepCopy(pending).put("expectedRevision",expected)))
                    val result = response.optJSONObject("data") ?: throw IOException("账号服务返回了不完整的保存结果")
                    val next = revision(result) ?: throw IOException("账号服务返回了无效的资料版本")
                    if (next <= expected!!) throw IOException("账号服务返回的资料版本未递增")
                    saved = retained(study.data,result)
                    break
                } catch (e: StudyApiException) {
                    if (e.status != 409) throw e
                    val remote = e.payload?.optJSONObject("data") ?: throw IOException("同步冲突响应缺少云端资料",e)
                    val next = revision(remote) ?: throw IOException("同步冲突响应缺少有效版本",e)
                    if (attempt == 2) throw IOException("云端资料持续变化，请稍后重试同步",e)
                    pending = StudyDataMerge.merge(base,pending,remote)
                    base = remote; expected = next
                }
            }
        }
        try { execute("/api/study/calendar",token,calendarBody) }
        catch (e: Exception) { throw StudyPartialUploadException(CloudStudy(saved,baseline?.calendar ?: JSONObject()),e) }
        return CloudStudy(saved,retained(study.calendar,calendar))
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
