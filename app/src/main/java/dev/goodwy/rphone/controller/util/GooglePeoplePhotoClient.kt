package dev.goodwy.rphone.controller.util

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val PEOPLE_CONNECTIONS_ENDPOINT =
    "https://people.googleapis.com/v1/people/me/connections"
private const val PEOPLE_PAGE_SIZE = 1000
private const val MAX_PEOPLE_PAGES = 20
private const val MAX_JSON_BYTES = 12 * 1024 * 1024
private const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
private const val PHOTO_SIZE_PX = 1024

internal sealed interface GooglePeoplePhotoResult {
    data class Found(val bytes: ByteArray) : GooglePeoplePhotoResult
    data object NotFound : GooglePeoplePhotoResult
    data object Ambiguous : GooglePeoplePhotoResult
    data object AuthorizationExpired : GooglePeoplePhotoResult
    data class Failed(val reason: String, val abortScan: Boolean = false) : GooglePeoplePhotoResult
}

/** Read-only People API client. Tokens and contact payloads are never persisted or logged. */
internal interface GooglePeoplePhotoSource {
    suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult
}

internal class GooglePeoplePhotoClient(
    private val transport: PeopleApiTransport = UrlConnectionPeopleApiTransport(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val cacheConnections: Boolean = false,
) : GooglePeoplePhotoSource {
    // A scan owns one client. Keep its index in memory, never on disk, and discard after the run.
    private var scanConnections: List<PeoplePerson>? = null
    override suspend fun loadPhoto(
        accessToken: String,
        phoneNumber: String,
    ): GooglePeoplePhotoResult = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() || phoneNumber.isBlank()) {
            return@withContext GooglePeoplePhotoResult.Failed("Missing authorization or number")
        }
        try {
            var pageToken: String? = null
            val matches = linkedMapOf<String, PeoplePerson>()
            var pagesRead = 0
            val cached = scanConnections
            val connections = cached?.toMutableList() ?: mutableListOf()
            while (cached == null && pagesRead < MAX_PEOPLE_PAGES) {
                coroutineContext.ensureActive()
                pagesRead++
                val response = transport.get(
                    url = connectionsUrl(pageToken),
                    accessToken = accessToken,
                    maxBytes = MAX_JSON_BYTES,
                )
                if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    response.code == HttpURLConnection.HTTP_FORBIDDEN
                ) {
                    return@withContext GooglePeoplePhotoResult.AuthorizationExpired
                }
                if (response.code !in 200..299) {
                    return@withContext GooglePeoplePhotoResult.Failed(
                        "People API returned HTTP ${response.code}",
                        abortScan = true,
                    )
                }
                val page = json.decodeFromString(PeopleConnectionsPage.serializer(), response.body.decodeToString())
                connections.addAll(page.connections.orEmpty())
                pageToken = page.nextPageToken?.takeIf(String::isNotBlank)
                if (pageToken == null) break
            }
            if (pageToken != null) {
                return@withContext GooglePeoplePhotoResult.Failed(
                    "Google Contacts exceeds the supported pagination limit",
                    abortScan = true,
                )
            }
            if (cacheConnections) scanConnections = connections
            connections.forEach { person ->
                if (person.phoneNumbers.orEmpty().any { numbersMatch(phoneNumber, it.value) }) {
                    person.resourceName?.let { matches[it] = person }
                }
            }
            if (matches.isEmpty()) return@withContext GooglePeoplePhotoResult.NotFound
            if (matches.size > 1) return@withContext GooglePeoplePhotoResult.Ambiguous
            val photoUrl = selectPhotoUrl(matches.values.single())
                ?: return@withContext GooglePeoplePhotoResult.NotFound
            val photoResponse = transport.get(
                url = sizedPhotoUrl(photoUrl),
                accessToken = null,
                maxBytes = MAX_PHOTO_BYTES,
                requireGoogleImageHost = true,
            )
            if (photoResponse.code !in 200..299) {
                return@withContext GooglePeoplePhotoResult.Failed(
                    "Google photo service returned HTTP ${photoResponse.code}",
                    abortScan = photoResponse.code == 429 || photoResponse.code >= 500,
                )
            }
            if (photoResponse.body.toContactPhotoImageInfo() == null) {
                return@withContext GooglePeoplePhotoResult.Failed("Google returned an invalid image")
            }
            GooglePeoplePhotoResult.Found(photoResponse.body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            GooglePeoplePhotoResult.Failed("Network unavailable", abortScan = true)
        } catch (error: Exception) {
            GooglePeoplePhotoResult.Failed("Unable to read Google Contacts", abortScan = true)
        }
    }

    private fun connectionsUrl(pageToken: String?): String = buildString {
        append(PEOPLE_CONNECTIONS_ENDPOINT)
        append("?personFields=phoneNumbers%2Cphotos%2Cmetadata")
        append("&pageSize=")
        append(PEOPLE_PAGE_SIZE)
        pageToken?.let {
            append("&pageToken=")
            append(URLEncoder.encode(it, StandardCharsets.UTF_8.name()))
        }
    }
}

internal fun numbersMatch(first: String, second: String): Boolean {
    val left = first.filter(Char::isDigit)
    val right = second.filter(Char::isDigit)
    if (left.isEmpty() || right.isEmpty()) return false
    if (left == right) return true
    return left.length >= 9 && right.length >= 9 && left.takeLast(9) == right.takeLast(9)
}

private fun selectPhotoUrl(person: PeoplePerson): String? = person.photos.orEmpty()
    .asSequence()
    .filterNot { it.isDefault == true }
    .sortedWith(
        compareByDescending<PeoplePhoto> { it.metadata?.source?.type == "CONTACT" }
            .thenByDescending { it.metadata?.sourcePrimary == true }
            .thenByDescending { it.metadata?.primary == true },
    )
    .mapNotNull(PeoplePhoto::url)
    .firstOrNull()

private fun sizedPhotoUrl(value: String): String {
    val original = value.toUri()
    val path = original.encodedPath.orEmpty()
    val queryWithoutSize = original.encodedQuery
        ?.split('&')
        ?.filterNot { it.substringBefore('=') == "sz" }
        ?.joinToString("&")
        ?.takeIf(String::isNotEmpty)
    val builder = original.buildUpon().encodedQuery(queryWithoutSize)

    // People API photos may arrive as ...=s100 or ...=s100-c. In that format,
    // appending ?sz=1024 leaves the small suffix in control of the image size.
    val suffixStart = path.lastIndexOf('=')
    if (suffixStart >= 0) {
        val tokens = path.substring(suffixStart + 1).split('-')
        val sizeToken = Regex("[swh]\\d+")
        if (tokens.any(sizeToken::matches)) {
            val modifiers = tokens.filterNot(sizeToken::matches)
            val resizedPath = buildString {
                append(path, 0, suffixStart)
                append("=s")
                append(PHOTO_SIZE_PX)
                modifiers.forEach { append('-').append(it) }
            }
            return builder.encodedPath(resizedPath).build().toString()
        }
    }

    val resizedPath = path.replace(Regex("/s\\d+(?=/|$)"), "/s$PHOTO_SIZE_PX")
    if (resizedPath != path) return builder.encodedPath(resizedPath).build().toString()

    return builder.appendQueryParameter("sz", PHOTO_SIZE_PX.toString()).build().toString()
}

internal data class PeopleHttpResponse(val code: Int, val body: ByteArray)

internal interface PeopleApiTransport {
    fun get(
        url: String,
        accessToken: String?,
        maxBytes: Int,
        requireGoogleImageHost: Boolean = false,
    ): PeopleHttpResponse
}

internal class UrlConnectionPeopleApiTransport : PeopleApiTransport {
    override fun get(
        url: String,
        accessToken: String?,
        maxBytes: Int,
        requireGoogleImageHost: Boolean,
    ): PeopleHttpResponse {
        val initial = URL(url)
        require(initial.protocol == "https") { "Only HTTPS is allowed" }
        if (requireGoogleImageHost) require(isGoogleImageHost(initial.host)) {
            "Unexpected Google photo host"
        }
        val connection = initial.openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", if (requireGoogleImageHost) "image/*" else "application/json")
        accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        return try {
            val code = connection.responseCode
            if (requireGoogleImageHost && !isGoogleImageHost(connection.url.host)) {
                throw IOException("Google photo redirected to an unexpected host")
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            PeopleHttpResponse(code, stream?.use { it.readLimited(maxBytes) } ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }

    private fun isGoogleImageHost(host: String): Boolean =
        host == "googleusercontent.com" || host.endsWith(".googleusercontent.com") ||
            host == "ggpht.com" || host.endsWith(".ggpht.com")
}

private fun java.io.InputStream.readLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("Response exceeds the allowed size")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@Serializable
private data class PeopleConnectionsPage(
    val connections: List<PeoplePerson>? = null,
    val nextPageToken: String? = null,
)

@Serializable
private data class PeoplePerson(
    val resourceName: String? = null,
    val phoneNumbers: List<PeoplePhone>? = null,
    val photos: List<PeoplePhoto>? = null,
)

@Serializable
private data class PeoplePhone(val value: String = "")

@Serializable
private data class PeoplePhoto(
    val metadata: PeopleFieldMetadata? = null,
    val url: String? = null,
    @SerialName("default") val isDefault: Boolean? = null,
)

@Serializable
private data class PeopleFieldMetadata(
    val primary: Boolean? = null,
    val sourcePrimary: Boolean? = null,
    val source: PeopleSource? = null,
)

@Serializable
private data class PeopleSource(val type: String? = null)
