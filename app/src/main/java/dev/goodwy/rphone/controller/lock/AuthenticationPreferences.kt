package dev.goodwy.rphone.controller.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_TYPE
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_AUTHENTICATION_ENABLED
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_PIN
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_PASSWORD
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_APP_LOCK_PIN
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_APP_LOCK
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_CALL_LOCK
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_CALL_LOCK_MODE
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_BIOMETRICS_CALL_LOCK_NUMBERS
import dev.goodwy.rphone.controller.util.canonicalMergeNumber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Durable credential configuration; UI only delegates after identity verification. */
internal class AuthenticationPreferences(
    private val dataStore: DataStore<Preferences>,
    private val authenticationSecrets: AuthenticationSecrets,
    private val snapshot: () -> Preferences,
    private val publish: (Preferences) -> Unit,
) {
    fun isEnabled(): Boolean {
        val method = snapshot()[stringPreferencesKey(KEY_BIOMETRICS_TYPE)].orEmpty()
        return (snapshot()[booleanPreferencesKey(KEY_AUTHENTICATION_ENABLED)] ?: method.isNotEmpty()) &&
            method.isNotEmpty()
    }
    fun shouldGateCall(phoneNumber: String?, region: () -> String?): Boolean {
        val prefs = snapshot()
        if (!isEnabled() || prefs[booleanPreferencesKey(KEY_BIOMETRICS_CALL_LOCK)] != true) return false
        val mode = prefs[stringPreferencesKey(KEY_BIOMETRICS_CALL_LOCK_MODE)] ?: "all"
        if (mode == "all") return true
        if (phoneNumber.isNullOrBlank()) return mode == "skip_specified"
        val stored = prefs[stringPreferencesKey(KEY_BIOMETRICS_CALL_LOCK_NUMBERS)].orEmpty()
        if (stored.isBlank()) return mode == "skip_specified"
        val country = region()
        val incoming = canonicalMergeNumber(phoneNumber, country)
        val match = stored.split(",").any { raw ->
            incoming != null && canonicalMergeNumber(raw, country) == incoming
        }
        return if (mode == "specified") match else !match
    }

    suspend fun migrateLegacy() {
        // Commit the encrypted verifier before removing either legacy plaintext secret.
        if (listOf(KEY_BIOMETRICS_PIN, KEY_BIOMETRICS_PASSWORD, KEY_APP_LOCK_PIN)
                .none { snapshot()[stringPreferencesKey(it)] != null }) return
        withContext(Dispatchers.IO) {
            val initial = snapshot()
            val method = initial[stringPreferencesKey(KEY_BIOMETRICS_TYPE)] ?: ""
            val legacyKey = when (method) {
                "pin" -> KEY_BIOMETRICS_PIN
                "password" -> KEY_BIOMETRICS_PASSWORD
                else -> null
            }
            val legacy = legacyKey?.let { initial[stringPreferencesKey(it)] }.orEmpty()
            try {
                if (legacy.isNotEmpty()) authenticationSecrets.migrate(method, legacy)
                dataStore.edit {
                    it.remove(stringPreferencesKey(KEY_BIOMETRICS_PIN))
                    it.remove(stringPreferencesKey(KEY_BIOMETRICS_PASSWORD))
                    it.remove(stringPreferencesKey(KEY_APP_LOCK_PIN))
                }
                publish(dataStore.data.first())
            } catch (error: Exception) {
                android.util.Log.e("Authentication", "Credential migration failed: ${error.javaClass.simpleName}")
                // Consumers never compare legacy plaintext or silently turn off an existing lock.
            }
        }

    }
    suspend fun configure(method: String, secret: String = "") = withContext(NonCancellable + Dispatchers.IO) {
        require(method in setOf("system", "pin", "password"))
        if (method == "pin") require(secret.length == 4 && secret.all(Char::isDigit))
        if (method == "password") require(secret.length >= 8)
        val previouslyEnabled = isEnabled()
        val commit: suspend () -> Unit = {
            dataStore.edit {
                it[stringPreferencesKey(KEY_BIOMETRICS_TYPE)] = method
                it[booleanPreferencesKey(KEY_AUTHENTICATION_ENABLED)] = true
                if (!previouslyEnabled) it[booleanPreferencesKey(KEY_BIOMETRICS_APP_LOCK)] = true
                it.remove(stringPreferencesKey(KEY_BIOMETRICS_PIN))
                it.remove(stringPreferencesKey(KEY_BIOMETRICS_PASSWORD))
                it.remove(stringPreferencesKey(KEY_APP_LOCK_PIN))
            }
            publish(dataStore.data.first())
        }
        if (method == "system") commit() else authenticationSecrets.configureAndCommit(method, secret, commit)
        authenticationSecrets.clear(except = if (method == "system") null else method)
        AppLockManager.unlock()
    }

    suspend fun disable() = withContext(NonCancellable + Dispatchers.IO) {
        dataStore.edit {
            it[booleanPreferencesKey(KEY_AUTHENTICATION_ENABLED)] = false
            it[stringPreferencesKey(KEY_BIOMETRICS_TYPE)] = ""
            it[booleanPreferencesKey(KEY_BIOMETRICS_APP_LOCK)] = false
            it[booleanPreferencesKey(KEY_BIOMETRICS_CALL_LOCK)] = false
            it.remove(stringPreferencesKey(KEY_BIOMETRICS_PIN))
            it.remove(stringPreferencesKey(KEY_BIOMETRICS_PASSWORD))
            it.remove(stringPreferencesKey(KEY_APP_LOCK_PIN))
        }
        publish(dataStore.data.first())
        authenticationSecrets.clear()
        AppLockManager.lock()
    }


}

/** Reuse the existing cold-start read; never publish a pre-migration credential cache. */
internal suspend fun loadAuthenticationPreferences(
    dataStore: DataStore<Preferences>, secrets: AuthenticationSecrets,
): Preferences {
    var current = dataStore.data.first()
    AuthenticationPreferences(dataStore, secrets, { current }, { current = it }).migrateLegacy()
    return current
}
