package dev.goodwy.rphone.controller.util

import io.github.bootika.dotdialer.core.preferences.PreferenceBackupPolicy
import org.json.JSONArray
import org.json.JSONObject

/** Type metadata prevents JSON's small Long / integral Float narrowing on restore. */
internal object BackupPreferencesJson {
    fun encode(values: Map<String, Any>): String {
        val data = JSONObject()
        val meta = JSONObject()
        values.filterKeys(PreferenceBackupPolicy::isPortable).forEach { (key, value) ->
            val type = when (value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> { require(value.isFinite()); "float" }
                is Double -> { require(value.isFinite()); "double" }
                is String -> "string"
                is Set<*> -> { require(value.all { it is String }); "string_set" }
                else -> error("Unsupported preference type")
            }
            meta.put(key, type)
            data.put(key, if (value is Set<*>) JSONArray(value.sortedBy { it.toString() }) else value)
        }
        return JSONObject().put("data", data).put("meta", meta).put("complete", true).toString()
    }

    fun isComplete(json: String): Boolean = JSONObject(json).let { raw ->
        if (!raw.has("complete")) false else {
            require(raw.get("complete") is Boolean && raw.has("data"))
            raw.getBoolean("complete")
        }
    }

    fun decode(json: String): Map<String, Any> {
        val raw = JSONObject(json)
        val data = if (raw.has("data")) raw.getJSONObject("data") else raw
        val meta = if (raw.has("meta")) raw.getJSONObject("meta") else JSONObject()
        return buildMap {
            data.keys().forEach { key ->
                if (!PreferenceBackupPolicy.isPortable(key)) return@forEach
                val value = data.get(key)
                val restored: Any = when (meta.optString(key)) {
                    "boolean" -> { require(value is Boolean); value }
                    "string" -> { require(value is String); value }
                    "int" -> exactInteger(value).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
                    "long" -> exactInteger(value)
                    "float" -> (value as Number).toFloat().also { require(it.isFinite()) }
                    "double" -> (value as Number).toDouble().also { require(it.isFinite()) }
                    "string_set" -> {
                        require(value is JSONArray)
                        (0 until value.length()).map { value.get(it).also { item -> require(item is String) } as String }.toSet()
                    }
                    "" -> when (value) {
                        is Double -> value.toFloat().also { require(it.isFinite()) } // legacy format
                        is Boolean, is Int, is Long, is String -> value
                        else -> error("Unsupported legacy preference type")
                    }
                    else -> error("Unknown preference type")
                }
                put(key, restored)
            }
        }
    }

    private fun exactInteger(value: Any): Long {
        require(value is Int || value is Long) { "Expected an integer" }
        return (value as Number).toLong()
    }
}
