package com.shshouse.studywithmiku

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONArray
import kotlin.random.Random

data class Quote(val text: String,val source: String)
class OnlineExtras(context: Context) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val quotes = JSONArray(context.assets.open("quotes.json").bufferedReader().use { it.readText() }).objects().map { Quote(it.optString("text"),it.optString("source")) }
    private val disk = context.getSharedPreferences("online_extras",Context.MODE_PRIVATE)
    private val _quote = MutableStateFlow(quotes.random())
    val quote = _quote.asStateFlow()
    private val _location = MutableStateFlow(disk.getString("location","") ?: "")
    val location = _location.asStateFlow()
    var shareLocation: Boolean
        get() = disk.getBoolean("share_location",true)
        set(value) { disk.edit().putBoolean("share_location",value).apply() }
    private var active: Job? = null
    fun start() {
        if (active != null) return
        refreshLocation()
        active = scope.launch { while (isActive) { refreshQuote(); delay(5*60_000) } }
    }
    fun stop() { active?.cancel(); active = null }
    fun refreshQuote(forceOnline: Boolean = false) { scope.launch {
        _quote.value = quotes.random()
        if (!forceOnline && Random.nextBoolean()) return@launch
        try {
            val o = withContext(Dispatchers.IO) { JSONObject(MusicHttp.text("https://v1.hitokoto.cn/?c=a&c=d&c=i&c=k")) }
            if (o.optString("hitokoto").isNotBlank()) _quote.value = Quote(o.optString("hitokoto"),
                listOf(o.optString("from_who"),o.optString("from").takeIf { it.isNotBlank() }?.let { "《$it》" } ?: "").filter { it.isNotBlank() }.joinToString(" "))
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Local quote is the same fallback as the website. */ }
    } }
    fun refreshLocation(force: Boolean = false) { scope.launch {
        if (!force && System.currentTimeMillis()-disk.getLong("location_time",0) < 30*60_000) return@launch
        try {
            val o = withContext(Dispatchers.IO) { JSONObject(MusicHttp.text("https://myip.ipip.net/json")) }
            if (o.optString("ret") != "ok") return@launch
            val arr = o.optJSONObject("data")?.optJSONArray("location") ?: return@launch
            val country = arr.optString(0)
            val region = arr.optString(1)
            val value = when {
                country != "中国" -> country
                region in listOf("香港","澳门","台湾") -> "中国$region"
                region.isNotBlank() -> region.replace(Regex("(壮族|回族|维吾尔)?自治区$|特别行政区$|省$|市$|地区$"),"")
                else -> "中国"
            }
            _location.value = value
            disk.edit().putString("location",value).putLong("location_time",System.currentTimeMillis()).apply()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Location is optional, as on the website. */ }
    } }
    fun messageLocation() = if (shareLocation) _location.value else ""
}
