package dev.goodwy.rphone.controller.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

internal class NumberSimPreferences(
    private val dataStore: DataStore<Preferences>,
    private val current: () -> Preferences,
    private val region: () -> String?,
    private val revision: () -> Int,
) {
    private var regionSnapshot: Pair<Int, String?>? = null
    @Synchronized private fun currentRegion(): String? {
        val version = revision()
        regionSnapshot?.takeIf { it.first == version }?.let { return it.second }
        return region().also { regionSnapshot = version to it }
    }
    @Synchronized fun refreshRegion() { regionSnapshot = null; currentRegion() }
    fun get(number: String): String? {
        val identity = NumberSimIdentities.resolve(number, currentRegion()) ?: return null
        val snapshot = current()
        snapshot[stringPreferencesKey(identity.key)]?.let { return it.takeUnless { value -> value == NumberSimIdentities.USE_DEFAULT } }
        val saved = identity.legacyKeys.mapNotNull { snapshot[stringPreferencesKey(it)] }.distinct()
        return when (saved.size) {
            0 -> if (identity.unsafeLegacyKeys.any { snapshot[stringPreferencesKey(it)] != null }) NumberSimIdentities.CONFLICT else null
            1 -> saved.single()
            else -> NumberSimIdentities.CONFLICT
        }
    }
    fun set(number: String, account: String?) {
        val identity = NumberSimIdentities.resolve(number, currentRegion()) ?: return
        PreferenceWriteBarrier.enqueue {
            dataStore.edit { prefs ->
                identity.legacyKeys.forEach { prefs.remove(stringPreferencesKey(it)) }
                val key = stringPreferencesKey(identity.key)
                prefs[key] = account ?: NumberSimIdentities.USE_DEFAULT
            }
        }
    }
}
