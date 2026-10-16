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

/** Signed bounded rights; no telephony action waits for these requests. */
internal class PlanAccessManager internal constructor(
    private val audience: String, private val identity: () -> String,
    private val read: () -> JSONObject?, private val write: (JSONObject) -> Unit,
    private val verifier: PlanGrantVerifier, private val scope: CoroutineScope,
    private val transport: (JSONObject) -> JSONObject,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlanAccessState())
    val state = mutableState.asStateFlow()
    private val requests = MutableSharedFlow<PlanFeature>(extraBufferCapacity = 4)
    val upgradeRequests = requests.asSharedFlow()
    fun requestUpgrade(feature: PlanFeature) { requests.tryEmit(feature) }
    private var record = JSONObject()
    private var installation = ""
    private var deadline = 0L
    private var wallExpiry = 0L
    private var lastClock = 0L
    private var verified = PlanEntitlements()
    private var lastAttempt = Long.MIN_VALUE
    private var purchaseHelper: PurchaseHelper? = null
    val effective: AppPlan get() {
        val current = if (elapsed() < deadline && now() < wallExpiry && now() >= lastClock - 300) verified else PlanEntitlements()
        val legacy = if (purchaseHelper?.isPro?.value == true) AppPlan.PREMIUM else AppPlan.FREE
        val purchased = purchaseHelper?.storePlan?.value ?: AppPlan.FREE
        return maxOf(current.effective(now()), legacy, purchased)
    }
    val paid: AppPlan get() = maxOf(if(valid()) verified.paid else AppPlan.FREE,
        if(purchaseHelper?.isPro?.value == true) AppPlan.PREMIUM else AppPlan.FREE,
        purchaseHelper?.storePlan?.value ?: AppPlan.FREE)
    fun allows(feature: PlanFeature): Boolean = PlanEntitlements(effective).allows(feature, now())
    fun attach(helper: PurchaseHelper) {
        if (purchaseHelper != null) return
        purchaseHelper = helper
        scope.launch { combine(helper.isPro, helper.storePlan) { _, _ -> Unit }.collect { publish() } }
    }
    private fun publish(busy: Boolean = false, unavailable: Boolean = false) {
        mutableState.value = PlanAccessState(verified, installation.isNotEmpty(), busy, unavailable, mutableState.value.revision + 1)
    }
    init { scope.launch {
        try {
            installation = identity(); record = read() ?: JSONObject()
            restore(); publish(); refresh()
            while (isActive) { delay(30_000); publish(unavailable = !valid()); refresh() }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure(error); publish(unavailable = true) }
    } }
    private fun valid() = elapsed() < deadline && now() < wallExpiry && now() >= lastClock - 300
    private fun restore() {
        try {
            require(now() >= record.optLong("last_clock") - 300)
            val grant = verifier.verify(record.getString("grant"), audience, installation, now())
            verified = grant.entitlements; wallExpiry = grant.expires; lastClock = record.optLong("last_clock")
            deadline = elapsed() + (grant.expires - now()) * 1000
        } catch (error: Exception) { failure(error);deadline = 0; wallExpiry = 0; verified = PlanEntitlements() }
    }
    fun retry() { scope.launch { refresh(force = true) } }
    fun startTrial() { scope.launch { refresh(startTrial = true, force = true) } }
    internal suspend fun refresh(startTrial: Boolean = false, force: Boolean = false, invitation: String? = null): Boolean = mutex.withLock {
        if (installation.isEmpty()) return@withLock false
        if (!force && lastAttempt != Long.MIN_VALUE && elapsed() - lastAttempt < 300_000) return@withLock false
        lastAttempt = elapsed(); publish(busy = true)
        try {
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val body = JSONObject().put("package", audience).put("installation", installation).put("nonce", nonce)
                .put("model", android.os.Build.MODEL.take(80)).put("version", BuildConfig.VERSION_NAME.take(40))
                .put("start_trial", if (startTrial) "1" else "0")
            if(invitation!=null) {require(invitation.matches(Regex("[a-f0-9]{64}")));body.put("invitation",invitation)}
            val signed = transport(body).getString("grant")
            val grant = verifier.verify(signed, audience, installation, now(), nonce)
            val updated = JSONObject().put("grant", signed).put("last_clock", now())
            write(updated)
            record = updated; verified = grant.entitlements; wallExpiry = grant.expires; lastClock = now()
            deadline = elapsed() + (grant.expires - now()) * 1000
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
            return PlanAccessManager(context.packageName, {
                val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                require(!id.isNullOrBlank())
                TestAccessManager.installationId(id, context.packageName)
            }, storage::read, storage::write, PlanGrantVerifier(), CoroutineScope(SupervisorJob() + Dispatchers.IO), ::request)
        }
        private fun request(body: JSONObject): JSONObject {
            val connection = URL("https://dialer.dotdesign.ro/api/v1/plan-access").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
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
