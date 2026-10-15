package dev.goodwy.rphone.controller.lock

import android.app.Application
import dev.goodwy.rphone.controller.util.PreferenceManager
import javax.crypto.spec.SecretKeySpec
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AuthenticationSecretsTest {
    // Test-only AES key; production always uses AndroidKeyStore.
    private val key = SecretKeySpec(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, "AES")
    private val context = RuntimeEnvironment.getApplication()
    private fun store() = AuthenticationSecrets(context) { key }

    @Before fun resetFixture() = runBlocking {
        val prefs = PreferenceManager(context, store())
        prefs.disableAuthentication()
        prefs.setAppLockTimeout(0)
        kotlinx.coroutines.withTimeout(5000) { prefs.settingsChanged.first { prefs.getAppLockTimeout() == 0 } }
        Unit
    }
    @After fun clearFixture() = runBlocking { PreferenceManager(context, store()).disableAuthentication() }

    @Test fun `salted verifiers differ and accept only the correct secret`() {
        val first = AuthenticationRecord.create("pin", "6482")
        val second = AuthenticationRecord.create("pin", "6482")
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.verifier.contentEquals(second.verifier))
        assertTrue(first.matches("6482"))
        assertFalse(first.matches("6483"))
        assertFalse(first.matches(""))
    }

    @Test fun `encrypted verifier persists without plaintext and decrypts after recreation`() = runBlocking {
        val secret = "Test Alexandru Butica password"
        store().configure("password", secret)
        val bytes = File(context.noBackupFilesDir, "authentication-password-v1.bin").readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains(secret))
        assertTrue(store().verify("password", secret))
        assertFalse(store().verify("password", "wrong password"))
    }

    @Test fun `fifth failure imposes a persistent cooldown including correct credentials`() = runBlocking {
        store().configure("pin", "6482")
        repeat(5) { assertFalse(store().verify("pin", "1234")) }
        val failure = runCatching { store().verify("pin", "6482") }.exceptionOrNull()
        assertTrue(failure is AuthenticationRetryException)
        assertTrue((failure as AuthenticationRetryException).seconds in 1..30)
    }

    @Test fun `success resets failure count before the next attempt`() = runBlocking {
        store().configure("pin", "6482")
        repeat(4) { assertFalse(store().verify("pin", "1234")) }
        assertTrue(store().verify("pin", "6482"))
        repeat(4) { assertFalse(store().verify("pin", "1234")) }
        assertTrue(store().verify("pin", "6482"))
    }

    @Test fun `corrupt ciphertext never accepts any secret`() = runBlocking {
        store().configure("pin", "6482")
        val file = File(context.noBackupFilesDir, "authentication-pin-v1.bin")
        val bytes = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(bytes)
        assertTrue(runCatching { store().verify("pin", "6482") }.isFailure)
    }

    @Test fun `missing key or missing record never unlocks`() = runBlocking {
        assertTrue(runCatching { store().verify("pin", "6482") }.isFailure)
        store().configure("pin", "6482")
        val unavailable = AuthenticationSecrets(context) { error("Unavailable test key") }
        assertTrue(runCatching { unavailable.verify("pin", "6482") }.isFailure)
    }

    @Test fun `failed preferences commit restores the previous verifier even for the same method`() = runBlocking {
        store().configure("pin", "6482")
        assertTrue(runCatching { store().configureAndCommit("pin", "5831") { error("Test persistence failure") } }.isFailure)
        assertTrue(store().verify("pin", "6482"))
        assertFalse(store().verify("pin", "5831"))
        assertTrue(runCatching { store().configureAndCommit("password", "Test Alexandru Butica password") { error("Test persistence failure") } }.isFailure)
        assertFalse(File(context.noBackupFilesDir, "authentication-password-v1.bin").exists())
    }

    @Test fun `precommit method change leaves previous method operational`() = runBlocking {
        store().configure("pin", "6482")
        store().configure("password", "Test Alexandru Butica password")
        assertTrue(store().verify("pin", "6482"))
        assertTrue(store().verify("password", "Test Alexandru Butica password"))
        store().clear(except = "password")
        assertTrue(runCatching { store().verify("pin", "6482") }.isFailure)
    }

    @Test fun `legacy migration can retry after encrypted commit but rejects mismatched migration`() = runBlocking {
        store().migrate("pin", "6482")
        store().migrate("pin", "6482")
        assertTrue(store().verify("pin", "6482"))
        assertTrue(runCatching { store().migrate("pin", "0000") }.isFailure)
    }

    @Test fun `retry delay is bounded and binary records validate their shape`() {
        val first = AuthenticationRecord.create("pin", "6482")
        var record = first
        repeat(50) { record = record.failed(1000) }
        assertEquals(30, record.failures)
        assertEquals(1_801_000L, record.retryAfter)
        assertTrue(AuthenticationRecord.decode(first.encode()).matches("6482"))
        assertTrue(runCatching { AuthenticationRecord.decode(first.encode() + byteArrayOf(1)) }.isFailure)
    }

    @Test fun `fresh settings are off and configuration commits before activation`() = runBlocking {
        val prefs = PreferenceManager(context, store())
        assertFalse(prefs.isAuthenticationEnabled())
        assertFalse(prefs.isAppLockEnabled())
        prefs.configureAuthentication("pin", "6482")
        assertTrue(prefs.isAuthenticationEnabled())
        assertTrue(prefs.isAppLockEnabled())
        assertTrue(prefs.verifyAuthentication("pin", "6482"))
        assertFalse(prefs.getAllPreferences().containsKey("biometrics_pin"))
        assertFalse(prefs.getAllPreferences().containsKey("biometrics_password"))
        prefs.disableAuthentication()
        assertFalse(prefs.isAuthenticationEnabled())
        assertFalse(prefs.isAppLockEnabled())
    }

    @Test fun `invalid or failed configuration does not enable authentication`() = runBlocking {
        val prefs = PreferenceManager(context, store())
        assertTrue(runCatching { prefs.configureAuthentication("password", "short") }.isFailure)
        assertFalse(prefs.isAuthenticationEnabled())
        val unavailable = PreferenceManager(context, AuthenticationSecrets(context) { error("Test key unavailable") })
        assertTrue(runCatching { unavailable.configureAuthentication("pin", "6482") }.isFailure)
        assertFalse(unavailable.isAuthenticationEnabled())
    }

    @Test fun `editable backup cannot enable disable or replace authentication`() = runBlocking {
        val prefs = PreferenceManager(context, store())
        prefs.configureAuthentication("pin", "6482")
        prefs.restoreAllPreferences(mapOf("authentication_enabled" to false,
            "biometrics_type" to "system", "biometrics_pin" to "0000", "biometrics_app_lock" to false,
            "app_lock_timeout" to 30, "theme_mode" to "dark"))
        assertTrue(prefs.isAppLockEnabled())
        assertEquals("pin", prefs.getString("biometrics_type", ""))
        assertEquals(0, prefs.getAppLockTimeout())
        assertEquals("dark", prefs.getString("theme_mode", ""))
        assertTrue(prefs.verifyAuthentication("pin", "6482"))
    }

    @Test fun `plaintext preferences migrate durably and preserve an existing lock`() = runBlocking {
        val initial = PreferenceManager(context, store())
        val field = PreferenceManager::class.java.getDeclaredField("dataStore").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val data = field.get(initial) as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
        data.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("biometrics_type")] = "pin"
            it[androidx.datastore.preferences.core.stringPreferencesKey("biometrics_pin")] = "6482"
            it[androidx.datastore.preferences.core.booleanPreferencesKey("biometrics_app_lock")] = true
            it.remove(androidx.datastore.preferences.core.booleanPreferencesKey("authentication_enabled"))
        }
        val prefs = PreferenceManager(context, store())
        assertTrue(prefs.isAppLockEnabled())
        assertTrue(prefs.verifyAuthentication("pin", "6482"))
        assertFalse(prefs.getAllPreferences().containsKey("biometrics_pin"))
        val restored = PreferenceManager(context, store())
        assertTrue(restored.verifyAuthentication("pin", "6482"))
    }

    @Test fun `call whitelist requires full identity and does not match number suffixes`() = runBlocking {
        val prefs = PreferenceManager(context, store())
        prefs.configureAuthentication("system")
        prefs.setBoolean("biometrics_call_lock", true)
        prefs.setString("biometrics_call_lock_mode", "skip_specified")
        prefs.setString("biometrics_call_lock_numbers", "+40722123456")
        prefs.setString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, "RO")
        kotlinx.coroutines.withTimeout(5000) {
            prefs.settingsChanged.first { prefs.getBoolean("biometrics_call_lock", false) &&
                prefs.getString("biometrics_call_lock_mode", "") == "skip_specified" &&
                prefs.getString("biometrics_call_lock_numbers", "") == "+40722123456" &&
                prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, "") == "RO" }
        }
        assertFalse(prefs.shouldGateCallWithBiometric("0722123456"))
        assertTrue(prefs.shouldGateCallWithBiometric("+44722123456"))
        assertTrue(prefs.shouldGateCallWithBiometric("123456"))
        assertTrue(prefs.shouldGateCallWithBiometric(null))
    }
}
