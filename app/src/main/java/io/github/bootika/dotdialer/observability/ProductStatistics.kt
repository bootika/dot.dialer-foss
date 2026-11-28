package io.github.bootika.dotdialer.observability

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.AtomicFile
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.license.scopedLicenseInstallationId
import dev.goodwy.rphone.controller.util.PreferenceManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** No contact/call parameters are accepted here. Pseudonyms rotate every UTC day. */
class ProductStatistics internal constructor(context: Context, private val prefs: PreferenceManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val transport: ((JSONObject) -> Boolean)? = null) {
    private val app = context.applicationContext as android.app.Application
    private val mutex = Mutex()
    private val file = AtomicFile(File(app.noBackupFilesDir, "product-statistics-v1.json"))
    private var queued: Job? = null
    private var lastSentDay = ""
    private var lastSentAction = 0L
    private var dirty = false
    private fun failure(operation: String, error: Exception) {
        // Never log exception messages, payloads, SIM values or pseudonyms.
        android.util.Log.w("DotStatistics", "$operation: ${error.javaClass.simpleName}")
    }
    init {
        if (BuildConfig.FLAVOR == "gplay") scope.launch {
            prefs.settingsChanged.collect {
                if (!allowed()) mutex.withLock {
                    queued?.cancel(); queued=null; file.delete(); lastSentDay=""; lastSentAction=0; dirty=false
                    ConnectedCallStatisticsJob.cancel(app)
                }
            }
        }
    }
    private fun allowed() = automaticGate && BuildConfig.FLAVOR == "gplay" && ReportingMode.parse(prefs.getString(AppTelemetry.REPORTING_KEY,null)) == ReportingMode.Automatic
    fun homeVisible() = update(null)
    fun record(event: Event) = update(event)
    enum class Event(val key: String) { Favorite("favorites"), Swipe("swipes"), Dialpad("dialpad"), ConnectedCall("calls") }
    private fun update(event: Event?) {
        if (!allowed()) return
        scope.launch {
            try { mutex.withLock {
                if (!allowed()) return@withLock
                val state = read()
                val connectedCall = event == Event.ConnectedCall
                if (event != null) {
                    state.getJSONObject("counts").apply { put(event.key,(optInt(event.key)+1).coerceAtMost(10000)) }
                    if (connectedCall) state.put(CONNECTED_CALL_PENDING, true)
                    if (!connectedCall && activityReportingEnabled()) {
                        state.put("last_action_at", System.currentTimeMillis() / 1000)
                    }
                    dirty = true
                }
                write(state)
                // Register recovery before attempting the send, so process loss during I/O
                // does not leave a persisted call waiting for the next Home visit.
                val retryScheduled = connectedCall && ConnectedCallStatisticsJob.schedule(app)
                if (event == null || connectedCall) {
                    val callPending = state.optBoolean(CONNECTED_CALL_PENDING)
                    if (needsSend(state)) send(state)
                    if (callPending && !state.optBoolean(CONNECTED_CALL_PENDING)) ConnectedCallStatisticsJob.cancel(app)
                    val stillPending = needsSend(state)
                    if (!stillPending) {
                        queued?.cancel()
                        queued = null
                    }
                    if (event == null || retryScheduled || !stillPending) return@withLock
                }
                if (queued?.isActive != true) queued = scope.launch {
                    delay(60_000)
                    try { mutex.withLock { if (allowed()) read().let { if (needsSend(it)) send(it) } } }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { failure("queued", error) }
                }
            } } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure("update", error) }
        }
    }
    internal suspend fun retryConnectedCalls(): Boolean = mutex.withLock {
        if (!allowed() || !file.baseFile.exists()) return@withLock false
        try {
            val state = read()
            if (!state.optBoolean(CONNECTED_CALL_PENDING)) return@withLock false
            send(state)
            state.optBoolean(CONNECTED_CALL_PENDING)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure("connected_call_retry", error); true }
    }
    private fun needsSend(state: JSONObject) = dirty || lastSentDay != state.getString("day") ||
        state.optBoolean(CONNECTED_CALL_PENDING) || pendingActivity(state) > 0
    private fun pendingActivity(state: JSONObject): Long {
        val action = state.optLong("last_action_at")
        return action.takeIf { activityReportingEnabled() && transport == null && it > lastSentAction && it >= System.currentTimeMillis() / 1000 - 30 * 86400 } ?: 0
    }
    private fun activityReportingEnabled() = BuildConfig.BUILD_TYPE == "internal" && app.packageName == "ro.dotdesign.dotdialer"
    private fun read(): JSONObject {
        val old = if (!file.baseFile.exists()) JSONObject() else try { JSONObject(String(file.readFully(),Charsets.UTF_8)) }
            catch (error: Exception) { failure("read", error); JSONObject() }
        val day = LocalDate.now(ZoneOffset.UTC).toString()
        if (old.optString("day") == day && old.has("counts") && old.optString("seed").matches(Regex("[a-f0-9-]{36}"))) return old
        return JSONObject().put("day",day).put("seed",old.optString("seed").takeIf { it.matches(Regex("[a-f0-9-]{36}")) } ?: UUID.randomUUID().toString())
            .put("last_action_at",old.optLong("last_action_at"))
            .put("counts",JSONObject().put("favorites",0).put("swipes",0).put("dialpad",0))
    }
    private fun write(state: JSONObject) {
        val output=file.startWrite()
        try { output.write(state.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    private fun sims(): JSONArray {
        val result=JSONArray()
        fun add(manager: TelephonyManager) {
            val country=manager.simCountryIso.orEmpty().uppercase(Locale.ROOT).takeIf { it in Locale.getISOCountries() }.orEmpty()
            val operator=manager.simOperator.orEmpty().takeIf { it.matches(Regex("[0-9]{5,6}")) }.orEmpty()
            if (country.isNotEmpty() || operator.isNotEmpty()) result.put(JSONObject().put("country",country).put("operator",operator))
        }
        try {
            val manager=app.getSystemService(TelephonyManager::class.java)
            val subscriptions=try { app.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList }
                catch (error: SecurityException) { failure("subscriptions", error); null }
            if (subscriptions.isNullOrEmpty()) add(manager)
            else subscriptions.take(2).forEach { add(manager.createForSubscriptionId(it.subscriptionId)) }
        } catch (error: Exception) { failure("sim", error) /* no new permission or inferred location */ }
        return result
    }
    private fun send(state: JSONObject) {
        if (!allowed()) return
        val action = pendingActivity(state)
        if (action > 0 && sendActivity(action)) lastSentAction = action
        if (!dirty && lastSentDay == state.getString("day") && !state.optBoolean(CONNECTED_CALL_PENDING)) return
        val day=state.getString("day")
        val region=prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, "").orEmpty().uppercase(Locale.ROOT).takeIf { it in Locale.getISOCountries() }.orEmpty()
        fun safe(text: String, maximum: Int) = text.replace(Regex("[\\p{Cntrl}@]"), "").take(maximum).ifBlank { "Unknown" }
        val counts = JSONObject(state.getJSONObject("counts").toString())
        if (!counts.has(Event.ConnectedCall.key)) counts.put(Event.ConnectedCall.key, 0)
        val body=JSONObject().put("package",app.packageName).put("day",day).put("daily_id",dailyId(state.getString("seed"),day))
            .put("version",BuildConfig.VERSION_NAME).put("android",Build.VERSION.SDK_INT)
            .put("manufacturer",safe(Build.MANUFACTURER,40)).put("model",safe(Build.MODEL,80))
            .put("language",app.resources.configuration.locales[0].toLanguageTag()).put("sim",sims()).put("region",region).put("counts",counts)
            .put("appearance",JSONObject()
                .put("accent",prefs.getString(PreferenceManager.KEY_ACCENT_COLOR,"jade").takeIf {it in setOf("jade","blue","violet","coral")} ?: "jade")
                .put("plan",dev.goodwy.rphone.controller.license.PlanAccessManager.get(app).effective.name.lowercase(Locale.ROOT)))
        var connection: HttpURLConnection? = null
        try {
            if (!allowed()) return
            transport?.let { if (it(body)) acknowledge(state, day); return }
            connection=URL("https://dialer.dotdesign.ro/api/v1/statistics").openConnection() as HttpURLConnection
            connection.requestMethod="POST"; connection.connectTimeout=10_000; connection.readTimeout=10_000
            connection.instanceFollowRedirects=false; connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json")
            // Re-check just before transport; an already dispatched request cannot be withdrawn.
            if (!allowed()) return
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode == 200) acknowledge(state, day)
        } catch (error: Exception) { failure("send", error) /* retry at next Home visit/event */ }
        finally { connection?.disconnect() }
    }
    private fun acknowledge(state: JSONObject, day: String) {
        if (state.remove(CONNECTED_CALL_PENDING) != null) {
            try { write(state) }
            catch (error: Exception) { state.put(CONNECTED_CALL_PENDING, true); throw error }
        }
        lastSentDay = day
        dirty = false
    }
    @android.annotation.SuppressLint("HardwareIds")
    private fun sendActivity(action: Long): Boolean {
        if (!activityReportingEnabled() || !allowed()) return false
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrBlank()) return false
        var connection: HttpURLConnection? = null
        return try {
            val body = JSONObject().put("installation", scopedLicenseInstallationId(androidId, app.packageName)).put("acted_at", action)
            connection = URL("https://dialer.dotdesign.ro/api/v1/installation-activity").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (!allowed()) return false
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            connection.responseCode == 200
        } catch (error: Exception) {
            failure("activity", error)
            false
        } finally { connection?.disconnect() }
    }
    companion object {
        private const val CONNECTED_CALL_PENDING = "connected_call_pending"
        @Volatile private var automaticGate = true
        internal fun setReportingGate(mode: ReportingMode) { automaticGate = mode == ReportingMode.Automatic }
        @Volatile private var instance: ProductStatistics? = null
        @Synchronized fun get(context: Context,prefs: PreferenceManager): ProductStatistics = instance ?: ProductStatistics(context,prefs).also { instance=it }
        internal fun dailyId(seed: String,day: String): String = MessageDigest.getInstance("SHA-256").digest("$seed|$day".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(Locale.ROOT,it) }
    }
}
