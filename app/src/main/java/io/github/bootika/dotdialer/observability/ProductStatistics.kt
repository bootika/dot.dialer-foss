package io.github.bootika.dotdialer.observability

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.AtomicFile
import dev.goodwy.rphone.BuildConfig
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
    private fun failure(operation: String, error: Exception) {
        // Never log exception messages, payloads, SIM values or pseudonyms.
        android.util.Log.w("DotStatistics", "$operation: ${error.javaClass.simpleName}")
    }
    init {
        if (BuildConfig.FLAVOR == "gplay") scope.launch {
            prefs.settingsChanged.collect {
                if (!allowed()) mutex.withLock { queued?.cancel(); queued=null; file.delete(); lastSentDay="" }
            }
        }
    }
    private fun allowed() = automaticGate && BuildConfig.FLAVOR == "gplay" && ReportingMode.parse(prefs.getString(AppTelemetry.REPORTING_KEY,null)) == ReportingMode.Automatic
    fun homeVisible() = update(null)
    fun record(event: Event) = update(event)
    enum class Event(val key: String) { Favorite("favorites"), Swipe("swipes"), Dialpad("dialpad") }
    private fun update(event: Event?) {
        if (!allowed()) return
        scope.launch {
            try { mutex.withLock {
                if (!allowed()) return@withLock
                val state = read()
                if (event != null) state.getJSONObject("counts").apply { put(event.key,(optInt(event.key)+1).coerceAtMost(10000)) }
                write(state)
                if (event == null && lastSentDay != state.getString("day")) send(state)
                else if (event != null && queued?.isActive != true) queued = scope.launch {
                    delay(60_000)
                    try { mutex.withLock { if (allowed()) send(read()) } }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { failure("queued", error) }
                }
            } } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure("update", error) }
        }
    }
    private fun read(): JSONObject {
        val old = if (!file.baseFile.exists()) JSONObject() else try { JSONObject(String(file.readFully(),Charsets.UTF_8)) }
            catch (error: Exception) { failure("read", error); JSONObject() }
        val day = LocalDate.now(ZoneOffset.UTC).toString()
        if (old.optString("day") == day && old.has("counts") && old.optString("seed").matches(Regex("[a-f0-9-]{36}"))) return old
        return JSONObject().put("day",day).put("seed",old.optString("seed").takeIf { it.matches(Regex("[a-f0-9-]{36}")) } ?: UUID.randomUUID().toString())
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
        val day=state.getString("day")
        val region=prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, "").orEmpty().uppercase(Locale.ROOT).takeIf { it in Locale.getISOCountries() }.orEmpty()
        fun safe(text: String, maximum: Int) = text.replace(Regex("[\\p{Cntrl}@]"), "").take(maximum).ifBlank { "Unknown" }
        val body=JSONObject().put("package",app.packageName).put("day",day).put("daily_id",dailyId(state.getString("seed"),day))
            .put("version",BuildConfig.VERSION_NAME).put("android",Build.VERSION.SDK_INT)
            .put("manufacturer",safe(Build.MANUFACTURER,40)).put("model",safe(Build.MODEL,80))
            .put("language",app.resources.configuration.locales[0].toLanguageTag()).put("sim",sims()).put("region",region).put("counts",state.getJSONObject("counts"))
            .put("appearance",JSONObject()
                .put("accent",prefs.getString(PreferenceManager.KEY_ACCENT_COLOR,"jade").takeIf {it in setOf("jade","blue","violet","coral")} ?: "jade")
                .put("plan",dev.goodwy.rphone.controller.license.PlanAccessManager.get(app).effective.name.lowercase(Locale.ROOT)))
        var connection: HttpURLConnection? = null
        try {
            if (!allowed()) return
            transport?.let { if (it(body)) lastSentDay=day; return }
            connection=URL("https://dialer.dotdesign.ro/api/v1/statistics").openConnection() as HttpURLConnection
            connection.requestMethod="POST"; connection.connectTimeout=10_000; connection.readTimeout=10_000
            connection.instanceFollowRedirects=false; connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json")
            // Re-check just before transport; an already dispatched request cannot be withdrawn.
            if (!allowed()) return
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode == 200) lastSentDay=day
        } catch (error: Exception) { failure("send", error) /* retry at next Home visit/event */ }
        finally { connection?.disconnect() }
    }
    companion object {
        @Volatile private var automaticGate = true
        internal fun setReportingGate(mode: ReportingMode) { automaticGate = mode == ReportingMode.Automatic }
        @Volatile private var instance: ProductStatistics? = null
        @Synchronized fun get(context: Context,prefs: PreferenceManager): ProductStatistics = instance ?: ProductStatistics(context,prefs).also { instance=it }
        internal fun dailyId(seed: String,day: String): String = MessageDigest.getInstance("SHA-256").digest("$seed|$day".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(Locale.ROOT,it) }
    }
}
