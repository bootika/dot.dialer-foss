package dev.goodwy.rphone.controller.license

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.PurchaseHelper
import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.license.PlanEntitlements
import io.github.bootika.dotdialer.core.license.PlanFeature
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class PlanAccessState(
    val entitlements: PlanEntitlements = PlanEntitlements(), val ready: Boolean = false,
    val busy: Boolean = false, val unavailable: Boolean = false, val revision: Long = 0,
)

/** Signed plan rights. Telephony actions never wait for verification requests. */
internal class PlanAccessManager internal constructor(
    private val audience: String, private val identity: () -> String,
    private val read: () -> JSONObject?, private val write: (JSONObject) -> Unit,
    private val verifier: PlanGrantVerifier, private val scope: CoroutineScope,
    private val transport: (JSONObject) -> JSONObject,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val invalidate: () -> Unit = { write(JSONObject()) },
    private val expiryObservation: ExpiryObservation? = null,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlanAccessState())
    val state = mutableState.asStateFlow()
    private val requests = MutableSharedFlow<PlanFeature>(extraBufferCapacity = 4)
    val upgradeRequests = requests.asSharedFlow()
    fun requestUpgrade(feature: PlanFeature) { requests.tryEmit(feature) }
    private var record = JSONObject()
    private var dirtyRecord: JSONObject? = null
    private var dirtyAttemptElapsed = Long.MIN_VALUE
    private var installation = ""
    private class CachedPlan(val grant: PlanGrant, val signed: String, val verifiedAt: Long, val wallAtLoad: Long, val elapsedAtLoad: Long) {
        private var observed = wallAtLoad
        var persistedFloor = 0L
        var checkpointAttempt = Long.MIN_VALUE
        var checkpointBoundary = 0L
        @Synchronized fun time(wall: Long, elapsed: Long): Long {
            observed = maxOf(observed, wall, wallAtLoad + (elapsed - elapsedAtLoad).coerceAtLeast(0) / 1000)
            return observed
        }
    }
    @Volatile private var cached: CachedPlan? = null
    private var lastAttempt = Long.MIN_VALUE
    private var purchaseHelper: PurchaseHelper? = null
    val effective: AppPlan get() {
        val wall = now()
        val elapsedNow = elapsed()
        val snapshot = cached
        val current = entitlements(snapshot, wall, elapsedNow)
        val time = snapshot?.time(wall, elapsedNow) ?: wall
        val legacy = if (purchaseHelper?.isPro?.value == true) AppPlan.PREMIUM else AppPlan.FREE
        val purchased = purchaseHelper?.storePlan?.value ?: AppPlan.FREE
        return maxOf(current.effective(time), legacy, purchased)
    }
    val paid: AppPlan get() = maxOf(entitlements(cached, now(), elapsed()).paid,
        if(purchaseHelper?.isPro?.value == true) AppPlan.PREMIUM else AppPlan.FREE,
        purchaseHelper?.storePlan?.value ?: AppPlan.FREE)
    fun allows(feature: PlanFeature): Boolean = PlanEntitlements(effective).allows(feature, now())
    fun attach(helper: PurchaseHelper) {
        if (purchaseHelper != null) return
        purchaseHelper = helper
        scope.launch { combine(helper.isPro, helper.storePlan) { _, _ -> Unit }.collect { publish() } }
    }
    private fun publish(busy: Boolean = false, unavailable: Boolean = false) {
        val snapshot = cached
        val wall = now()
        val elapsedNow = elapsed()
        val current = entitlements(snapshot, wall, elapsedNow)
        val time = snapshot?.time(wall, elapsedNow) ?: wall
        val hasOfflineAccess = snapshot?.grant?.paidTerms != null && current.effective(time) != AppPlan.FREE
        mutableState.value = PlanAccessState(current, installation.isNotEmpty(), busy,
            unavailable && !hasOfflineAccess, mutableState.value.revision + 1)
    }
    init { scope.launch {
        try {
            installation = identity(); record = read() ?: JSONObject()
            restore(); publish(); refresh()
            while (isActive) { delay(30_000); publish(unavailable = !leaseValid(cached, now(), elapsed())); refresh() }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure(error); publish(unavailable = true) }
    } }
    private fun leaseValid(snapshot: CachedPlan?, wall: Long, elapsedNow: Long): Boolean =
        snapshot != null && wall >= snapshot.verifiedAt - 300 && snapshot.time(wall, elapsedNow) < snapshot.grant.expires

    private fun entitlements(snapshot: CachedPlan?, wall: Long, elapsedNow: Long): PlanEntitlements {
        if (snapshot == null) return PlanEntitlements()
        val time = snapshot.time(wall, elapsedNow)
        checkpointExpiry(snapshot, time)
        return snapshot.grant.entitlementsAt(
            time, leaseValid(snapshot, wall, elapsedNow), wall >= snapshot.verifiedAt - 300,
        )
    }

    private fun expiryBoundary(grant: PlanGrant, time: Long): Long = buildList {
        if (grant.paidTerms == null) add(grant.expires)
        else addAll(grant.paidTerms.values.filterNotNull())
        if (grant.entitlements.trialEnds > 0) add(grant.entitlements.trialEnds)
    }.filter { it <= time }.maxOrNull() ?: 0

    private val checkpointLock = Any()
    private fun checkpointExpiry(snapshot: CachedPlan, time: Long) {
        val boundary = expiryBoundary(snapshot.grant, time)
        if (boundary == 0L) return
        synchronized(checkpointLock) {
            if (cached !== snapshot || boundary <= snapshot.persistedFloor) return
            val tick = elapsed()
            if (snapshot.checkpointBoundary == boundary && snapshot.checkpointAttempt != Long.MIN_VALUE &&
                tick - snapshot.checkpointAttempt in 0L until 30_000L) return
            snapshot.checkpointBoundary = boundary
            snapshot.checkpointAttempt = tick
            val updated = JSONObject().put("grant", snapshot.signed).put("last_clock", snapshot.verifiedAt).put("clock_floor", boundary)
            var journalSaved = false
            try { if (expiryObservation != null) { expiryObservation.save(snapshot.signed, boundary); journalSaved = true } }
            catch (error: Exception) { failure(error) }
            if (!journalSaved) {
                if (persistRecord(updated)) snapshot.persistedFloor = boundary
            } else {
                snapshot.persistedFloor = boundary
                scope.launch { mutex.withLock {
                    synchronized(checkpointLock) {
                        if (cached !== snapshot || snapshot.persistedFloor != boundary) return@synchronized
                        persistRecord(updated)
                    }
                } }
            }
            // Only this first crossed boundary performs bounded local IO before returning reduced
            // feature rights. Network/Telecom actions never await it. Both-media failure is explicit;
            // do not delete a mixed grant and destroy surviving permanent rights.
        }
    }

    /** Called only under checkpointLock; a newer signed response replaces any dirty older one. */
    private fun persistRecord(updated: JSONObject): Boolean {
        dirtyAttemptElapsed = elapsed()
        return try {
            write(updated); record = updated; dirtyRecord = null; true
        } catch (error: Exception) {
            dirtyRecord = updated; failure(error); false
        }
    }

    private fun retryDirtyRecord() = synchronized(checkpointLock) {
        val pending = dirtyRecord ?: return@synchronized
        val delta = elapsed() - dirtyAttemptElapsed
        if (delta in 0L until 30_000L) return@synchronized
        if (pending.optString("grant") == cached?.signed) persistRecord(pending)
        else dirtyRecord = null
    }

    private fun restore() {
        try {
            val signed = record.optString("grant")
            if (signed.isEmpty()) return
            val wall = now()
            val verifiedAt = record.getLong("last_clock")
            val grant = verifier.verifyCached(signed, audience, installation, verifiedAt)
            val journal = try { expiryObservation?.floor(signed) ?: 0 } catch (error: Exception) {
                failure(error); 0
            }
            cached = CachedPlan(grant, signed, verifiedAt, maxOf(wall, verifiedAt, record.optLong("clock_floor"), journal), elapsed()).also {
                it.persistedFloor = maxOf(record.optLong("clock_floor"), journal)
            }
        } catch (error: Exception) { failure(error) }
    }
    fun retry() { scope.launch { refresh(force = true) } }
    fun startTrial() { scope.launch { refresh(startTrial = true, force = true) } }
    internal suspend fun refresh(startTrial: Boolean = false, force: Boolean = false, invitation: String? = null): Boolean = mutex.withLock {
        if (installation.isEmpty()) return@withLock false
        retryDirtyRecord() // Local durability recovery is independent of the server throttle/connectivity.
        if (!force && lastAttempt != Long.MIN_VALUE && elapsed() - lastAttempt < 300_000) return@withLock false
        lastAttempt = elapsed(); publish(busy = true)
        try {
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val body = JSONObject().put("package", audience).put("installation", installation).put("nonce", nonce)
                .put("model", android.os.Build.MODEL.take(80)).put("version", BuildConfig.VERSION_NAME.take(40))
                .put("offline_days", "10")
                .put("start_trial", if (startTrial) "1" else "0")
            if(invitation!=null) {require(invitation.matches(Regex("[a-f0-9]{64}")));body.put("invitation",invitation)}
            val signed = transport(body).getString("grant")
            val wall = now()
            val grant = verifier.verify(signed, audience, installation, wall, nonce)
            val updated = JSONObject().put("grant", signed).put("last_clock", wall)
            synchronized(checkpointLock) {
                val reducing = cached?.grant?.let(grant::reduces) == true
                cached = CachedPlan(grant, signed, wall, wall, elapsed())
                // A verified authoritative reduction is not a network outage. Remove the old
                // durable grant before replacement so a failed write/process death cannot revive it.
                if (reducing) try { invalidate() } catch (error: Exception) { failure(error) }
                persistRecord(updated)
            }
            publish()
            true
        } catch (error: CancellationException) { publish(); throw error }
        catch (error: Exception) { failure(error); publish(unavailable = true);false }
    }
    private fun failure(error: Exception) { android.util.Log.w("DotPlans", error.javaClass.simpleName) }
    companion object {
        @Volatile private var instance: PlanAccessManager? = null
        fun get(context: Context): PlanAccessManager = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
        @android.annotation.SuppressLint("HardwareIds")
        private fun create(context: Context): PlanAccessManager {
            val storage = GiftLicenseStorage(context, "dot.dialer.plan-access.v1", "plan-access-v1.bin")
            val journal = GiftLicenseStorage(context, "dot.dialer.plan-expiry.v1", "plan-expiry-v1.bin")
            return PlanAccessManager(context.packageName, {
                val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                require(!id.isNullOrBlank())
                scopedLicenseInstallationId(id, context.packageName)
            }, storage::read, storage::write, PlanGrantVerifier(), CoroutineScope(SupervisorJob() + Dispatchers.IO), ::request,
                invalidate = storage::invalidate, expiryObservation = ExpiryObservation(journal::read, journal::write))
        }
        private fun request(body: JSONObject): JSONObject {
            val connection = URL("https://dialer.dotdesign.ro/api/v1/plan-access").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Dot-Plan-Grant", "2")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                require(connection.responseCode == 200)
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1024)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 8192); output.write(buffer, 0, n) }
                    output.toByteArray()
                }
                return JSONObject(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
    }
}
