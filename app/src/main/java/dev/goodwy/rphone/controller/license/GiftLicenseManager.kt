package dev.goodwy.rphone.controller.license

import android.content.Context
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class GiftLicenseError { INVALID, LIMIT, UNAVAILABLE }
class GiftLicenseException(val reason: GiftLicenseError) : Exception("License activation failed")

/** Network requests are restricted to explicit activation or refresh of an existing grant. */
class GiftLicenseManager internal constructor(private val audience: String,
    private val storage: GiftLicenseStorage, private val verifier: GiftGrantVerifier,
    private val scope: CoroutineScope,
    private val wallClock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val expiryObservation: ExpiryObservation? = null,
    private val writeRecord: (JSONObject) -> Unit = storage::write,
    private val invalidateRecord: () -> Unit = storage::invalidate,
    private val transport: ((String, JSONObject, String?) -> JSONObject)? = null) {
    constructor(context: Context): this(context.applicationContext.packageName, GiftLicenseStorage(context),
        GiftGrantVerifier(), CoroutineScope(SupervisorJob() + Dispatchers.IO), expiryObservation = giftExpiryObservation(context))
    private val mutex = Mutex()
    private var record = JSONObject()
    private var observedTime = 0L
    private var observedElapsed = 0L
    private var lastRefreshAttempt = 0L
    private var refreshedElapsed: Long? = null
    private var refreshLeaseExpires = 0L
    private val _isPro = MutableStateFlow(false)
    val isPro = _isPro.asStateFlow()
    @Volatile var initialized: Boolean = false
        private set

    init {
        scope.launch {
            mutex.withLock {
                record = readStored()
                if (!record.has("installation")) record.put("installation", UUID.randomUUID().toString())
                validateCached()
                initialized = true
            }
            while (isActive) { refreshIfNeeded(); delay(60_000) }
        }
    }

    private fun now() = wallClock()
    private fun failure(operation: String, error: Exception) {
        // Exception messages/stack traces can contain secrets. Keep only a fixed stage and type.
        android.util.Log.w("DotLicense", "$operation: ${error.javaClass.simpleName}")
    }
    private fun readStored(): JSONObject = try { storage.read() ?: JSONObject() }
        catch (error: Exception) { failure("read", error); JSONObject() }
    private fun installation() = record.getString("installation")
    private fun validateCached() {
        if (!record.has("grant")) { _isPro.value = false; return }
        _isPro.value = try {
            val signed = record.getString("grant")
            val verifiedAt = record.getLong("last_clock")
            val grant = verifier.verify(signed, audience, installation(), verifiedAt)
            val tick = elapsedClock()
            if (refreshedElapsed == null) refreshedElapsed = tick
            refreshLeaseExpires = grant.expires
            val end = grant.accessUntil
            val journal = try { expiryObservation?.floor(signed) ?: 0 } catch (error: Exception) {
                failure("expiry-read", error); 0
            }
            observedTime = maxOf(now(), verifiedAt, record.optLong("clock_floor"), journal,
                observedTime + if (observedElapsed == 0L) 0 else (tick - observedElapsed).coerceAtLeast(0) / 1000)
            observedElapsed = tick
            if (end != null && observedTime >= end && record.optLong("clock_floor") < end) {
                val updated = JSONObject(record.toString()).put("clock_floor", end)
                try { writeRecord(updated); record = updated } catch (error: Exception) { failure("expiry-write", error) }
                try { expiryObservation?.save(signed, end) } catch (error: Exception) { failure("expiry-journal", error) }
            }
            require(end == null || now() >= verifiedAt - 300 && observedTime < end)
            true
        } catch (error: Exception) { failure("verify", error); false }
    }

    suspend fun activate(code: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!initialized) {
                record = readStored()
                if (!record.has("installation")) record.put("installation", UUID.randomUUID().toString())
                initialized = true
            }
            val clean = code.trim().uppercase(java.util.Locale.ROOT)
            if (!clean.matches(Regex("DD-[A-F0-9]{40}"))) throw GiftLicenseException(GiftLicenseError.INVALID)
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val body = identity(nonce).put("code", clean)
            val response = request("redeem", body)
            val token = response.getString("refresh_token")
            require(token.matches(Regex("[A-Za-z0-9_-]{43}")))
            commit(response.getString("grant"), token, nonce)
        }
    }

    private fun identity(nonce: String) = JSONObject().put("package", audience).put("installation", installation()).put("nonce", nonce)
    private fun commit(grant: String, token: String, nonce: String) {
        val current = now(); val verified = verifier.verify(grant, audience, installation(), current, nonce)
        val updated = JSONObject().put("installation", installation()).put("grant", grant).put("refresh_token", token)
            .put("refreshed", current).put("last_clock", current)
        val previous = try { record.optString("grant").takeIf { it.isNotEmpty() }?.let {
            verifier.verify(it, audience, installation(), record.getLong("last_clock"))
        } } catch (error: Exception) { failure("previous", error); null }
        val reducing = previous != null && verified.accessUntil != null &&
            (previous.accessUntil == null || verified.accessUntil < previous.accessUntil)
        if (reducing) {
            // A signed reduction is authoritative even when replacement persistence fails.
            record = updated
            _isPro.value = current < verified.accessUntil
            try { invalidateRecord() } catch (error: Exception) { failure("invalidate-reduction", error) }
            try { writeRecord(updated) } catch (error: Exception) { failure("write-reduction", error) }
        } else writeRecord(updated) // New activation never publishes before durable commit.
        record = updated
        observedTime = current
        observedElapsed = elapsedClock()
        refreshedElapsed = elapsedClock()
        refreshLeaseExpires = verified.expires
        _isPro.value = true
    }

    private suspend fun refreshIfNeeded() = mutex.withLock {
        validateCached()
        if (!record.has("refresh_token")) return@withLock
        val current = now()
        val refreshed = record.optLong("refreshed")
        val monotonicAge = refreshedElapsed?.let { (elapsedClock() - it).coerceAtLeast(0) / 1000 } ?: Long.MAX_VALUE
        if (_isPro.value && current >= refreshed && current - refreshed < 86400 && monotonicAge < 86400 && current < refreshLeaseExpires) return@withLock
        if (lastRefreshAttempt != 0L && elapsedClock() - lastRefreshAttempt < 900_000) return@withLock
        lastRefreshAttempt = elapsedClock()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        try {
            val response = request("refresh", identity(nonce), record.getString("refresh_token"))
            commit(response.getString("grant"), record.getString("refresh_token"), nonce)
        } catch (error: GiftLicenseException) {
            if (error.reason == GiftLicenseError.INVALID) {
                _isPro.value = false; observedTime = 0; observedElapsed = 0
                val cleared = JSONObject().put("installation", installation())
                record = cleared
                try { invalidateRecord() } catch (error: Exception) { failure("invalidate", error) }
                try { writeRecord(cleared) } catch (error: Exception) { failure("clear", error) }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure("refresh", error) /* keep only the bounded verified grant */ }
    }

    companion object {
        private fun giftExpiryObservation(context: Context): ExpiryObservation {
            val storage = GiftLicenseStorage(context, "dot.dialer.gift-expiry.v1", "gift-expiry-v1.bin")
            return ExpiryObservation(storage::read, storage::write)
        }
    }

    private fun request(action: String, body: JSONObject, token: String? = null): JSONObject {
        transport?.let { return it(action, body, token) }
        val connection = URL("https://dialer.dotdesign.ro/api/v1/$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Dot-Gift-Grant", "2")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (connection.responseCode) {
                200 -> {
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            require(output.size() + count <= 8192); output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    return JSONObject(String(bytes, Charsets.UTF_8))
                }
                401 -> throw GiftLicenseException(GiftLicenseError.INVALID)
                409 -> throw GiftLicenseException(GiftLicenseError.LIMIT)
                else -> throw GiftLicenseException(GiftLicenseError.UNAVAILABLE)
            }
        } finally { connection.disconnect() }
    }
}
