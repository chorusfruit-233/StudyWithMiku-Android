package io.github.chorusfruit233.studywithmiku

import org.json.JSONArray
import org.json.JSONObject

/** Three-way merge matching upstream studyDataMerge.js. A missing field is distinct from JSON null. */
object StudyDataMerge {
    private fun equal(a: Any?,b: Any?): Boolean = if (a == null || b == null) a === b else StudyProtocol.sameJson(a,b)
    private fun clone(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject(value.toString())
        is JSONArray -> JSONArray(value.toString())
        else -> value
    }
    private fun value(base: Any?,local: Any?,remote: Any?): Any? = when {
        equal(local,base) -> clone(remote)
        equal(remote,base) -> clone(local)
        local is JSONObject && remote is JSONObject -> objects(base as? JSONObject,local,remote)
        else -> clone(local)
    }
    private fun objects(base: JSONObject?,local: JSONObject,remote: JSONObject): JSONObject {
        val result = JSONObject()
        (local.keys().asSequence().toList()+remote.keys().asSequence().toList()).distinct().forEach { key ->
            val b = base?.opt(key); val l = local.opt(key); val r = remote.opt(key)
            when {
                !local.has(key) -> if (!(base?.has(key) == true && equal(r,b))) result.put(key,clone(r))
                !remote.has(key) -> if (!(base?.has(key) == true && equal(l,b))) result.put(key,clone(l))
                else -> result.put(key,value(b,l,r))
            }
        }
        return result
    }
    private fun number(o: JSONObject?,key: String) = (o?.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun stats(base: JSONObject?,local: Any?,remote: Any?): Any? {
        if (remote !is JSONObject) return clone(local)
        if (local !is JSONObject) return clone(remote)
        val result = objects(base,local,remote)
        for (key in listOf("totalStudyTime","totalPomodoros")) {
            val b = number(base,key); val l = number(local,key); val r = number(remote,key)
            if (l != null && r != null) result.put(key,if (b == null) maxOf(l,r) else r+maxOf(0.0,l-b))
        }
        val ld = local.optString("lastDate"); val rd = remote.optString("lastDate"); val bd = base?.optString("lastDate") ?: ""
        if (maxOf(ld,rd).isNotEmpty()) result.put("lastDate",maxOf(ld,rd))
        for (key in listOf("todayStudyTime","todayPomodoros")) {
            val b = number(base,key); val l = number(local,key); val r = number(remote,key)
            if (l != null && r != null) result.put(key,when {
                ld == rd -> if (bd == ld && b != null) r+maxOf(0.0,l-b) else maxOf(l,r)
                ld > rd -> l
                else -> r
            })
        }
        return result
    }
    private fun byId(items: Any?): LinkedHashMap<String,JSONObject> {
        val result = linkedMapOf<String,JSONObject>()
        if (items is JSONArray) for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.opt("id")
            // JS Map distinguishes a numeric id from a string id.
            val key = when (id) { is String -> "s:$id"; is Number -> "n:${id.toDouble()}"; else -> continue }
            result[key] = item
        }
        return result
    }
    private fun todos(base: Any?,local: Any?,remote: Any?): Any? {
        if (remote !is JSONArray) return clone(local)
        if (local !is JSONArray) return clone(remote)
        val l = byId(local); val r = byId(remote)
        val merged = objects(JSONObject(byId(base)),JSONObject(l),JSONObject(r))
        return JSONArray().apply {
            (l.keys+r.keys).distinct().forEach { if (merged.has(it)) put(merged.get(it)) }
        }
    }
    fun merge(base: JSONObject?,local: JSONObject,remote: JSONObject): JSONObject = JSONObject()
        .put("stats",stats(base?.optJSONObject("stats"),local.opt("stats"),remote.opt("stats")))
        .put("todos",todos(base?.opt("todos"),local.opt("todos"),remote.opt("todos")))
        .put("settings",value(base?.opt("settings"),local.opt("settings"),remote.opt("settings")))
}
