package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal enum class ContactPhotoAutomaticPolicy(val storedValue: String) {
    Newest("newest"), Optimal("optimal"), Manual("manual"), FillMissing("missing"), Latest("latest");
    companion object { fun fromStored(value: String) = entries.firstOrNull { it.storedValue == value } ?: Newest }
}

internal data class ContactOptimizationSettings(
    val enabled: Boolean = true,
    val normalize: Boolean = true,
    val gravatar: Boolean = true,
    val disclosed: Boolean = false,
    val business: Boolean = true,
    val google: Boolean = true,
    val policy: ExistingPhotoPolicy = ExistingPhotoPolicy.Ask,
    val autoUpdateExisting: Boolean = true,
    val onlySameOrLarger: Boolean = true,
    val gravatarDisclosed: Boolean = false,
    val businessDisclosed: Boolean = false,
    val googleAuthorized: Boolean = false,
    val automaticPhotoPolicy: ContactPhotoAutomaticPolicy = ContactPhotoAutomaticPolicy.Newest,
    val businessDiscoveryDisclosed: Boolean = false,
) {
    val effectivePhotoPolicy get() = if (!autoUpdateExisting && automaticPhotoPolicy != ContactPhotoAutomaticPolicy.Manual)
        ContactPhotoAutomaticPolicy.FillMissing else automaticPhotoPolicy
}
internal data class ContactOptimizationResult(
    val detectedAt: Long = 0,
    val outcome: String = "",
    val contactId: Long? = null,
    val source: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val writtenWidth: Int = 0,
    val writtenHeight: Int = 0,
    val hydrated: Boolean = false,
)

/** Android profile sandbox + account namespace; never stored in/exported with the agenda. */
internal class ContactOptimizationProfiles(context: Context) {
    private val root = File(context.noBackupFilesDir, "contact-optimization").apply { mkdirs() }
    private val lock = Any()
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()
    private val storageError = MutableStateFlow(false)
    val storageFailed = storageError.asStateFlow()
    fun reportStorageFailure() { storageError.value = true }

    init {
        pruneExpired()
        // D104 files were created by this feature in app-private storage, not user-selected assets.
        val legacy = File(context.noBackupFilesDir, "whatsapp-avatar-sync")
        legacy.listFiles()?.filter { it.name == "review-queue.json" ||
            Regex("[a-f0-9-]{36}-(candidate|previous)\\.png").matches(it.name) }?.forEach { it.delete() }
    }
    fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - WhatsAppAvatarReviewStore.RETENTION_MILLIS
        root.listFiles()?.filter { it.isDirectory && Regex("[a-f0-9]{64}").matches(it.name) }?.forEach { dir ->
            dir.listFiles()?.filter {
                Regex("[a-f0-9-]{36}-(candidate|previous)\\.png").matches(it.name) && it.lastModified() < cutoff
            }?.forEach { it.delete() }
            File(dir, "result.json").takeIf { it.isFile && it.lastModified() < cutoff }?.delete()
            val queue = AtomicFile(File(dir, "review-queue.json"))
            runCatching {
                if (!queue.baseFile.isFile || queue.baseFile.length() > WhatsAppAvatarReviewStore.MAX_METADATA_BYTES) return@runCatching
                val old = org.json.JSONArray(queue.openRead().bufferedReader().use { it.readText() })
                val retained = org.json.JSONArray()
                for (i in 0 until old.length()) {
                    val item = old.optJSONObject(i) ?: continue
                    if (item.optLong("createdAtMillis") >= cutoff) retained.put(item)
                }
                if (retained.length() != old.length()) {
                    val output = queue.startWrite()
                    try { output.write(retained.toString().encodeToByteArray()); queue.finishWrite(output) }
                    catch (e: Exception) { queue.failWrite(output); throw e }
                }
            }
        }
    }
    fun directory(account: String): File {
        require(account.isNotBlank())
        return File(root, accountKey(account)).apply { mkdirs() }
    }
    fun preserveExistingGoogleConsent(account: String, enabled: Boolean) {
        if (!File(directory(account), "settings.json").exists() && enabled) update(account) { it.copy(google = true, googleAuthorized = true) }
    }
    fun settings(account: String?): ContactOptimizationSettings = synchronized(lock) {
        if (account.isNullOrBlank()) return@synchronized ContactOptimizationSettings()
        val json = read(account, "settings.json")
        ContactOptimizationSettings(json.optBoolean("enabled", true), json.optBoolean("normalize", true),
            json.optBoolean("gravatar", true), json.optBoolean("disclosed"), json.optBoolean("business", true), json.optBoolean("google", true),
            ExistingPhotoPolicy.fromStored(json.optString("policy")), json.optBoolean("autoUpdateExisting", true), json.optBoolean("onlySameOrLarger", true),
            json.optBoolean("gravatarDisclosed", json.optBoolean("gravatar", false)),
            json.optBoolean("businessDisclosed", json.optBoolean("business", false)),
            json.optBoolean("googleAuthorized", json.optBoolean("google", false)),
            ContactPhotoAutomaticPolicy.fromStored(json.optString("automaticPhotoPolicy")), json.optBoolean("businessDiscoveryDisclosed"))
    }
    fun update(account: String, transform: (ContactOptimizationSettings) -> ContactOptimizationSettings) = synchronized(lock) {
        val next = transform(settings(account))
        runCatching { save(account, "settings.json", JSONObject().put("enabled", next.enabled)
            .put("normalize", next.normalize).put("gravatar", next.gravatar)
            .put("disclosed", next.disclosed).put("business", next.business).put("google", next.google)
            .put("policy", next.policy.storedValue).put("autoUpdateExisting", next.autoUpdateExisting).put("onlySameOrLarger", next.onlySameOrLarger)
            .put("gravatarDisclosed", next.gravatarDisclosed).put("businessDisclosed", next.businessDisclosed)
            .put("googleAuthorized", next.googleAuthorized).put("automaticPhotoPolicy", next.automaticPhotoPolicy.storedValue)
            .put("businessDiscoveryDisclosed",next.businessDiscoveryDisclosed)) }
            .onSuccess { storageError.value = false }.onFailure { storageError.value = true }
        revision.value++
    }
    fun gravatarAllowed(account: String) = settings(account).let { it.gravatar && it.gravatarDisclosed }
    fun businessAllowed(account: String) = settings(account).let { it.business && it.businessDisclosed }
    fun googleAllowed(account: String) = settings(account).let { it.google && it.googleAuthorized }
    fun result(account: String): ContactOptimizationResult {
        val j = read(account, "result.json")
        return ContactOptimizationResult(j.optLong("detectedAt"), j.optString("outcome"),
            if (j.has("contactId")) j.optLong("contactId") else null, j.optString("source"),
            j.optInt("originalWidth"), j.optInt("originalHeight"), j.optInt("writtenWidth"),
            j.optInt("writtenHeight"), j.optBoolean("hydrated"))
    }
    fun record(account: String, result: ContactOptimizationResult) = synchronized(lock) {
        val j = JSONObject().put("detectedAt", result.detectedAt).put("outcome", result.outcome)
            .put("source", result.source).put("originalWidth", result.originalWidth)
            .put("originalHeight", result.originalHeight).put("writtenWidth", result.writtenWidth)
            .put("writtenHeight", result.writtenHeight).put("hydrated", result.hydrated)
        result.contactId?.let { j.put("contactId", it) }
        runCatching { save(account, "result.json", j) }.onSuccess { storageError.value = false }.onFailure { storageError.value = true }
        revision.value++
    }
    private fun read(account: String, name: String): JSONObject = runCatching {
        val file = AtomicFile(File(directory(account), name))
        if (file.baseFile.length() > 32 * 1024) return@runCatching JSONObject()
        JSONObject(file.openRead().bufferedReader().use { it.readText() })
    }.getOrDefault(JSONObject())
    private fun save(account: String, name: String, json: JSONObject) {
        val file = AtomicFile(File(directory(account), name)); val output = file.startWrite()
        try { output.write(json.toString().encodeToByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    companion object {
        fun accountKey(account: String): String = MessageDigest.getInstance("SHA-256")
            .digest(account.trim().lowercase(Locale.ROOT).encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
