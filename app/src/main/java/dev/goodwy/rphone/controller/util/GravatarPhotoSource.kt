package dev.goodwy.rphone.controller.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.io.ByteArrayOutputStream

/** Opt-in source. Does not receive names/numbers or use default/generated avatar images. */
internal interface ContactAvatarSource {
    val sourceName: String
    suspend fun load(email: String): ByteArray?
}
internal class GravatarPhotoSource : ContactAvatarSource {
    override val sourceName = "Gravatar"
    override suspend fun load(email: String): ByteArray? = withContext(Dispatchers.IO) {
        val hash = emailHash(email) ?: return@withContext null
        val connection = URI.create("https://www.gravatar.com/avatar/" + hash + "?s=1024&d=404&r=g")
            .toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            if (connection.responseCode == 404) return@withContext null
            if (connection.responseCode != 200) throw java.io.IOException("Avatar source unavailable")
            if (
                !connection.contentType.orEmpty().startsWith("image/") ||
                connection.contentLengthLong > 8 * 1024 * 1024) return@withContext null
            val bytes = connection.inputStream.use { input ->
                ByteArrayOutputStream().use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        if (out.size() + n > 8 * 1024 * 1024) return@withContext null
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            }
            bytes.takeIf { it.toContactPhotoImageInfo() != null }
        } finally { connection.disconnect() }
    }
    companion object {
        fun emailHash(email: String): String? {
            val normalized = email.trim().lowercase(Locale.ROOT)
            if (!Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(normalized)) return null
            return MessageDigest.getInstance("SHA-256").digest(normalized.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
