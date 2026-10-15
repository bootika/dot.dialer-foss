package dev.goodwy.rphone.controller.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.net.URI
import java.net.HttpURLConnection
import java.net.InetAddress
import java.io.ByteArrayOutputStream

internal interface CompanyLogoSource {
    suspend fun load(website: String): ByteArray?
    suspend fun matchesCompany(website: String, company: String): Boolean = false
}
/** Official stored site identity only. No company-name search, guessed domains, OpenGraph/media or JS. */
internal class OfficialCompanyLogoSource : CompanyLogoSource {
    override suspend fun matchesCompany(website: String, company: String): Boolean = withContext(Dispatchers.IO) {
        val origin=publicOrigin(website) ?: return@withContext false
        if(!publicAddresses(origin)) return@withContext false
        val html=fetch(origin,512*1024,false)?.toString(Charsets.UTF_8) ?: return@withContext false
        val title=Regex("<title\\b[^>]*>([^<]{1,300})</title>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1) ?: return@withContext false
        normalizedCompanyName(title)==normalizedCompanyName(company)
    }
    override suspend fun load(website: String): ByteArray? = withContext(Dispatchers.IO) {
        val origin = publicOrigin(website) ?: return@withContext null
        if (!publicAddresses(origin)) return@withContext null
        val html = fetch(origin, 512 * 1024, false)?.toString(Charsets.UTF_8) ?: return@withContext null
        val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { tag ->
            val attrs = Regex("(\\w+)\\s*=\\s*[\"']([^\"']+)[\"']").findAll(tag.value)
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val rel = attrs["rel"]?.lowercase()?.split(Regex("\\s+")).orEmpty()
            if ("apple-touch-icon" !in rel && "icon" !in rel) return@mapNotNull null
            val uri = runCatching { URI.create(origin).resolve(attrs["href"]) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host != URI.create(origin).host || uri.userInfo != null ||
                (uri.port != -1 && uri.port != 443)) return@mapNotNull null
            uri.toString()
        }.distinct().take(8).toList()
        var best: ByteArray? = null
        var bestEdge = 0
        for (url in links) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val image = fetch(url, 4 * 1024 * 1024, true) ?: continue
            val info = image.toContactPhotoImageInfo() ?: continue
            if (info.width >= 48 && info.width == info.height && info.width > bestEdge) {
                best = image; bestEdge = info.width
            }
        }
        best
    }
    private fun publicAddresses(origin: String) = InetAddress.getAllByName(URI.create(origin).host).none {
        it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress
    }
    private fun fetch(url: String, maximum: Int, image: Boolean): ByteArray? {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000; connection.instanceFollowRedirects = false
            if (connection.responseCode != 200 || connection.contentLengthLong > maximum) return null
            val type = connection.contentType.orEmpty()
            if (image && (!type.startsWith("image/") || type.contains("svg"))) return null
            if (!image && !type.startsWith("text/html")) return null
            return connection.inputStream.use { input -> ByteArrayOutputStream().use { out ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    if (out.size() + n > maximum) return null
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } }
        } finally { connection.disconnect() }
    }
    companion object {
        fun publicOrigin(website: String): String? = runCatching {
            val uri = URI.create(website.trim())
            val host = uri.host?.lowercase() ?: return null
            if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443) ||
                !Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)+").matches(host) || host.last().isDigit() ||
                host.endsWith(".local") || host.endsWith(".localhost") || host.endsWith(".internal")) return null
            "https://" + host + "/"
        }.getOrNull()
    }
}
