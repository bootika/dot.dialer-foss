package dev.goodwy.rphone.controller.util

import android.telephony.PhoneNumberUtils
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@kotlinx.serialization.Serializable
internal data class PhoneExtension(val digits: String = "", val label: String = "") {
    val valid: Boolean get() = digits.isEmpty() || digits.matches(Regex("[0-9*#]{1,24}"))
}

/** Local number metadata only. Portable preferences preserve it in Dot backups. */
internal object PhoneExtensions {
    private val writes = Mutex()
    const val KEY = "phone_extensions_v1"
    fun key(number: String): String = PhoneNumberUtils.normalizeNumber(number)
    fun read(prefs: PreferenceManager): Map<String, PhoneExtension> = decode(prefs.getString(KEY, null))
    internal fun decode(value: String?): Map<String, PhoneExtension> = try {
        val root = JSONObject(value ?: "{}")
        root.keys().asSequence().mapNotNull { number ->
            val item = root.optJSONObject(number) ?: return@mapNotNull null
            val extension = PhoneExtension(item.optString("digits"), item.optString("label").take(40))
            if (number.isBlank() || !extension.valid || extension.digits.isEmpty()) null else number to extension
        }.toMap()
    } catch (error: org.json.JSONException) { android.util.Log.w("PhoneExtensions", "Invalid local extension metadata"); emptyMap() }

    fun find(prefs: PreferenceManager, number: String, region: String? = null): PhoneExtension? {
        val entries = read(prefs)
        entries[key(number)]?.let { return it }
        val international = region?.let { PhoneNumberUtils.formatNumberToE164(number, it) } ?: return null
        // Never guess from a suffix. Ambiguous aliases deliberately have no shortcut.
        val matches = entries.filterKeys { PhoneNumberUtils.formatNumberToE164(it, region) == international }.values
        return matches.singleOrNull()
    }

    suspend fun replace(prefs: PreferenceManager, originalNumbers: List<String>, entries: Map<String, PhoneExtension>) = writes.withLock {
        check(prefs.allowsFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY))
        require(entries.values.all { it.valid && it.label.length <= 40 })
        val persisted = prefs.getPersistedPreferences()
        val updated = decode(persisted[KEY] as? String).toMutableMap()
        originalNumbers.forEach { updated.remove(key(it)) }
        entries.forEach { (number, value) ->
            if (value.digits.isNotEmpty() && key(number).isNotBlank()) updated[key(number)] = value
        }
        val root = JSONObject()
        updated.forEach { (number, value) -> root.put(number, JSONObject().put("digits", value.digits).put("label", value.label)) }
        prefs.setString(KEY, root.toString())
        check(prefs.getPersistedPreferences()[KEY] == root.toString())
    }
}
