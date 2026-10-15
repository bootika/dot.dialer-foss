package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Local account/raw-contact namespace; no transferable consent or contact contents. */
internal class ContactsOptimizerState(context: Context, account: String) {
    private val directory = File(context.noBackupFilesDir, "contact-optimization/" + ContactOptimizationProfiles.accountKey(account)).apply { mkdirs() }
    private val file = AtomicFile(File(directory, "optimizer-state.json"))
    private val mutex = locks.computeIfAbsent(file.baseFile.absolutePath) { Any() }
    private fun read(): JSONObject = synchronized(mutex) { if (!file.baseFile.exists()) JSONObject() else {
        check(file.baseFile.length() <= 1024 * 1024)
        JSONObject(file.openRead().bufferedReader().use { it.readText() })
    } }
    private fun update(change: (JSONObject) -> Unit) = synchronized(mutex) {
        val json = read(); change(json)
        val bytes = json.toString().encodeToByteArray(); check(bytes.size <= 1024 * 1024)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    fun pinned(rawId: Long) = read().optJSONObject("pins")?.optBoolean(rawId.toString()) == true
    fun pinnedIds(): Set<Long> = read().optJSONObject("pins")?.keys()?.asSequence()?.mapNotNull(String::toLongOrNull)?.toSet().orEmpty()
    fun observed(rawId: Long, source: String) = read().optJSONObject("observations")?.has("$rawId:$source") == true
    fun pin(rawId: Long, value: Boolean) = update { json ->
        val pins = json.optJSONObject("pins") ?: JSONObject()
        if (value) pins.put(rawId.toString(), true) else pins.remove(rawId.toString())
        json.put("pins", pins)
    }
    fun observe(rawId: Long, source: String, bytes: ByteArray, now: Long): Long {
        val key = "$rawId:$source"; val hash = digest(bytes)
        val previous = read().optJSONObject("observations")?.optJSONObject(key)
        if (previous?.optString("hash") == hash) return previous.optLong("at")
        update { json ->
            val entries = json.optJSONObject("observations") ?: JSONObject()
            entries.put(key, JSONObject().put("hash", hash).put("at", now))
            if (entries.length() > 4000) {
                val oldest = entries.keys().asSequence().minByOrNull { entries.getJSONObject(it).optLong("at") }
                oldest?.let(entries::remove)
            }
            json.put("observations", entries)
        }
        return now
    }
    fun consumed(rawId: Long) = read().optLong("consumed:$rawId")
    fun consume(rawId: Long, at: Long, source: String) = update { json ->
        json.put("consumed:$rawId", at).put("source:$rawId", source)
        val history=json.keys().asSequence().filter { it.startsWith("consumed:") }.sortedBy { json.optLong(it) }.toList()
        history.take((history.size-2000).coerceAtLeast(0)).forEach { key ->
            json.remove(key); json.remove("source:"+key.removePrefix("consumed:"));json.remove("logo:"+key.removePrefix("consumed:"))
        }
    }
    fun source(rawId: Long) = read().optString("source:$rawId")
    fun logoMatches(rawId: Long, bytes: ByteArray) = read().optString("logo:$rawId")==digest(bytes)
    fun rememberLogo(rawId: Long, bytes: ByteArray?) = update { json ->
        if(bytes==null) json.remove("logo:$rawId") else json.put("logo:$rawId",digest(bytes))
    }
    fun cursor() = read().optLong("cursor")
    fun cursor(rawId: Long) = update { it.put("cursor", rawId) }
    companion object {
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
