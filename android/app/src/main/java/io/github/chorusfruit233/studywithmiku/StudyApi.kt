package io.github.chorusfruit233.studywithmiku

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class StudyApiException(val status: Int): IOException(if (status == 401) "登录已过期，请重新登录" else "账号服务返回 HTTP $status，请稍后重试")
object StudyNetwork {
    val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .writeTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
}
class StudyApi(private val baseUrl: String = ACCOUNT_SITE,client: OkHttpClient = StudyNetwork.client,
    private val cookies: () -> String = { "" }) : StudySyncApi {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    fun request(path: String,token: String = "",body: JSONObject? = null): JSONObject {
        require(path.startsWith("/api/study/"))
        val builder = Request.Builder().url(baseUrl+path).header("Cache-Control","no-store").header("Pragma","no-cache")
            .header("User-Agent","StudyWithMiku-Android/1.2").header("Accept","application/json")
            .header("Origin",STUDY_SITE).header("Referer","$STUDY_SITE/")
        if (token.isNotBlank()) builder.header("Authorization","Bearer $token")
        cookies().takeIf { it.isNotBlank() }?.let { builder.header("Cookie",it) }
        if (body != null) builder.put(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw StudyApiException(response.code)
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
        request("/api/study/data",token,study.data)
        request("/api/study/calendar",token,study.calendar)
    }
}
