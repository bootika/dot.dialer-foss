package dev.goodwy.rphone.controller.license

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dev.goodwy.rphone.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class TestAccessState { CHECKING, ACTIVE, BLOCKED, UNAVAILABLE }

/** Separate from Pro. No action/call waits for a network request or holds this mutex. */
class TestAccessManager internal constructor(
    private val enabled: Boolean,
    private val identity: () -> String,
    private val read: () -> JSONObject?,
    private val write: (JSONObject) -> Unit,
    private val verifier: GiftGrantVerifier,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val transport: (JSONObject) -> JSONObject,
) {
    private val mutex = Mutex()
    private var installation = ""
    private var record = JSONObject()
    @Volatile private var deadline = 0L
    @Volatile private var expiresWall = 0L
    @Volatile private var lastClock = 0L
    private var lastAttempt: Long? = null
    private val mutableState = MutableStateFlow(if (enabled) TestAccessState.CHECKING else TestAccessState.ACTIVE)
    val state = mutableState.asStateFlow()
    val allowed: Boolean get() = !enabled || (state.value == TestAccessState.ACTIVE &&
        elapsed() < deadline && now() < expiresWall && now() >= lastClock - 300)

    init {
        if (enabled) scope.launch {
            mutex.withLock {
                installation = try { identity() } catch (error: Exception) { failure("identity", error); mutableState.value = TestAccessState.UNAVAILABLE; return@launch }
                record = try { read() ?: JSONObject() } catch (error: Exception) { failure("read", error); JSONObject() }
                if (cachedValid()) mutableState.value = TestAccessState.ACTIVE
                else if (record.optBoolean("denied")) mutableState.value = TestAccessState.BLOCKED
            }
            refresh()
            while (isActive) { delay(30_000); refresh() }
        }
    }

    private fun failure(stage: String, error: Exception) {
        android.util.Log.w("DotTestAccess", "$stage: ${error.javaClass.simpleName}")
    }
    private fun cachedValid(): Boolean = try {
        if (!record.has("grant")) false else {
        val current = now()
        require(current >= record.optLong("last_clock") - 300)
        val grant = verifier.verify(record.getString("grant"), "dot.dialer.test", installation, current)
        expiresWall = grant.expires; lastClock = record.optLong("last_clock")
        if (deadline == 0L) deadline = elapsed() + (grant.expires - current) * 1000
        elapsed() < deadline
        }
    } catch (error: Exception) { failure("verify", error); false }

    fun retry() { if (enabled) scope.launch { refresh(manual = true) } }

    internal suspend fun refresh(manual: Boolean = false) = mutex.withLock {
        if (!enabled || installation.isEmpty()) return@withLock
        if (state.value == TestAccessState.ACTIVE && !cachedValid()) mutableState.value = TestAccessState.UNAVAILABLE
        val previous = lastAttempt
        if (previous != null && elapsed() - previous < (if (manual) 30_000 else 300_000)) return@withLock
        lastAttempt = elapsed()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        try {
            val response = transport(JSONObject().put("package", "dot.dialer.test").put("installation", installation)
                .put("nonce", nonce).put("model", android.os.Build.MODEL.take(80))
                .put("version", BuildConfig.VERSION_NAME.take(40)))
            val grant = response.getString("grant")
            val verified = verifier.verify(grant, "dot.dialer.test", installation, now(), nonce)
            val updated = JSONObject().put("grant", grant).put("last_clock", now())
            write(updated)
            record = updated
            deadline = elapsed() + (verified.expires - now()) * 1000
            expiresWall = verified.expires; lastClock = now()
            mutableState.value = TestAccessState.ACTIVE
        } catch (error: CancellationException) { throw error }
        catch (_: AccessDenied) {
            record = JSONObject().put("denied", true); deadline = 0
            mutableState.value = TestAccessState.BLOCKED
            try { write(record) } catch (error: Exception) { failure("deny-write", error) }
        } catch (error: Exception) {
            failure("refresh", error)
            if (!cachedValid() && state.value != TestAccessState.BLOCKED) mutableState.value = TestAccessState.UNAVAILABLE
        }
    }

    internal class AccessDenied : Exception()

    companion object {
        @Volatile private var instance: TestAccessManager? = null
        fun get(context: Context): TestAccessManager = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
        private fun create(context: Context): TestAccessManager {
            val storage = GiftLicenseStorage(context, "dot.dialer.test-access.v1", "test-access-v1.bin")
            return TestAccessManager(BuildConfig.DEBUG && context.packageName == "dot.dialer.test", {
                scopedInstallationId(context)
            }, storage::read, storage::write,
                GiftGrantVerifier(entitlement = "test_access", maxSeconds = 86400),
                CoroutineScope(SupervisorJob() + Dispatchers.IO), transport = ::request)
        }
        // Explicit test-license requirement: stable scoped pseudonym survives ordinary reinstall.
        // Raw Android ID never leaves the device; release builds do not invoke this function.
        @android.annotation.SuppressLint("HardwareIds")
        private fun scopedInstallationId(context: Context): String {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            require(!androidId.isNullOrBlank())
            return installationId(androidId, context.packageName)
        }
        internal fun installationId(androidId: String, packageName: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest("dot-test-access-v1|$packageName|$androidId".toByteArray())
            bytes[6] = ((bytes[6].toInt() and 15) or 64).toByte()
            bytes[8] = ((bytes[8].toInt() and 63) or 128).toByte()
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            return UUID(buffer.long, buffer.long).toString()
        }
        private fun request(body: JSONObject): JSONObject {
            val connection = URL("https://dialer.dotdesign.ro/api/v1/test-access").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode == 403) throw AccessDenied()
                require(connection.responseCode == 200)
                val bytes = connection.inputStream.use { it.readBytesBounded() }
                return JSONObject(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
        private fun java.io.InputStream.readBytesBounded(): ByteArray {
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1024)
            while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= 8192); output.write(buffer, 0, count) }
            return output.toByteArray()
        }
    }
}
