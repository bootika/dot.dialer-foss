package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class CompanyNameMatch(val name: String, val domain: String)
internal data class CompanyNameSearchResult(val matches: List<CompanyNameMatch> = emptyList(), val unavailable: Boolean = false)
internal fun interface CompanyNameSearch {
    suspend fun search(company: String, account: String): CompanyNameSearchResult
    fun refreshUnavailable(account: String) {}
}

/** Only the Organization name is sent. The provider secret belongs in the server's private config. */
internal class HttpCompanyNameSearch(private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
    private val request: (String) -> CompanyNameSearchResult = ::requestCompanyName) : CompanyNameSearch {
    override fun refreshUnavailable(account: String) {
        File(ContactOptimizationProfiles(context).directory(account),"company-search").listFiles()
            ?.filter { it.name.matches(Regex("[a-f0-9]{64}\\.json")) && it.length()<=16*1024 }
            ?.forEach { file ->
                val unavailable=runCatching { JSONObject(file.readText()).optString("status")=="unavailable" }.getOrDefault(false)
                if(unavailable) AtomicFile(file).delete()
            }
    }
    override suspend fun search(company: String, account: String) = withContext(Dispatchers.IO) {
        require(company.length in 3..120 && company.none(Char::isISOControl))
        val directory=File(ContactOptimizationProfiles(context).directory(account),"company-search").apply { mkdirs() }
        val cache=AtomicFile(File(directory,ContactsOptimizerState.digest(company.encodeToByteArray())+".json"))
        val cached=if (cache.baseFile.isFile && cache.baseFile.length()<=16*1024) runCatching {
            JSONObject(cache.openRead().bufferedReader().use { it.readText() })
        }.getOrNull() else null
        if(cached!=null && now()-cached.optLong("at") in 0 until cached.optLong("ttl")) return@withContext parseCompanyMatches(cached)
        val result=request(company)
        val json=JSONObject().put("at",now()).put("ttl",if(result.unavailable || result.matches.isEmpty()) DAY else 7*DAY)
            .put("status",if(result.unavailable) "unavailable" else "ok")
            .put("data",JSONArray().apply { result.matches.take(5).forEach { put(JSONObject().put("name",it.name).put("domain",it.domain)) } })
        val stream=cache.startWrite()
        try { stream.write(json.toString().encodeToByteArray());cache.finishWrite(stream) }
        catch(error:Exception) { cache.failWrite(stream);throw error }
        directory.listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}\\.json")) }?.sortedByDescending(File::lastModified)
            ?.drop(200)?.forEach { it.delete() }
        result
    }
    companion object { private const val DAY=24*60*60*1000L }
}

internal fun parseCompanyMatches(json: JSONObject): CompanyNameSearchResult {
    if(json.optString("status")!="ok") return CompanyNameSearchResult(unavailable=true)
    val data=json.optJSONArray("data") ?: return CompanyNameSearchResult(unavailable=true)
    if(data.length()>5) return CompanyNameSearchResult(unavailable=true)
    val matches=(0 until data.length()).mapNotNull { index ->
        val item=data.optJSONObject(index) ?: return@mapNotNull null
        val name=item.optString("name").takeIf { it.length in 3..120 && it.none(Char::isISOControl) } ?: return@mapNotNull null
        val domain=item.optString("domain")
        val valid=CompanyLogoDomains.website("https://$domain")?.takeIf { it==domain } ?: return@mapNotNull null
        CompanyNameMatch(name,valid)
    }.distinct()
    // A malformed result is not an empty search or proof of a unique remaining candidate.
    return if(matches.size!=data.length()) CompanyNameSearchResult(unavailable=true) else CompanyNameSearchResult(matches)
}

private fun requestCompanyName(company: String): CompanyNameSearchResult {
    val connection=URL("https://dialer.dotdesign.ro/api/v1/company-search").openConnection() as HttpURLConnection
    try {
        connection.requestMethod="POST";connection.doOutput=true;connection.instanceFollowRedirects=false
        connection.connectTimeout=7_000;connection.readTimeout=7_000;connection.setRequestProperty("Content-Type","application/json")
        connection.outputStream.use { it.write(JSONObject().put("company",company).toString().encodeToByteArray()) }
        if(connection.responseCode!=200) return CompanyNameSearchResult(unavailable=true)
        if(!connection.contentType.orEmpty().startsWith("application/json") || connection.contentLengthLong>16*1024)
            return CompanyNameSearchResult(unavailable=true)
        val bytes=connection.inputStream.use { input -> java.io.ByteArrayOutputStream().use { out ->
            val buffer=ByteArray(2048)
            while(true) { val count=input.read(buffer);if(count<0)break
                if(out.size()+count>16*1024) return CompanyNameSearchResult(unavailable=true)
                out.write(buffer,0,count) }
            out.toByteArray()
        } }
        return parseCompanyMatches(JSONObject(bytes.decodeToString()))
    } finally { connection.disconnect() }
}
