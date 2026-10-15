package dev.goodwy.rphone.controller.lock

import android.app.Application
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class AppLockSessionTest {
    @Before fun resetFixture() = runBlocking {
        val prefs = prefs()
        prefs.disableAuthentication()
        prefs.setAppLockTimeout(0)
        withTimeout(5000) { prefs.settingsChanged.first { prefs.getAppLockTimeout() == 0 } }
        Unit
    }
    @After fun reset() { AppLockManager.lock() }
    private fun prefs() = PreferenceManager(RuntimeEnvironment.getApplication())
    @Test fun `disabled authentication never locks`() {
        AppLockManager.lock()
        assertFalse(AppLockManager.isLocked(prefs()))
    }
    @Test fun `immediate timeout relocks on return but not while foregrounded`() = runBlocking {
        val prefs = prefs()
        prefs.configureAuthentication("system")
        assertFalse(AppLockManager.isLocked(prefs))
        ShadowSystemClock.advanceBy(Duration.ofMillis(10))
        AppLockManager.onAppBackgrounded()
        AppLockManager.onAppForegrounded(prefs)
        assertTrue(AppLockManager.isLocked(prefs))
        AppLockManager.unlock()
        assertFalse(AppLockManager.isLocked(prefs))
    }
    @Test fun `timeout expires at the exact boundary and successful return clears background time`() = runBlocking {
        val prefs = prefs()
        prefs.configureAuthentication("system")
        prefs.setAppLockTimeout(1)
        withTimeout(5000) { prefs.settingsChanged.first { prefs.getAppLockTimeout() == 1 } }
        ShadowSystemClock.advanceBy(Duration.ofMillis(10))
        AppLockManager.onAppBackgrounded()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(59))
        AppLockManager.onAppForegrounded(prefs)
        assertFalse(AppLockManager.isLocked(prefs))
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        assertFalse(AppLockManager.isLocked(prefs))
        AppLockManager.onAppBackgrounded()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
        AppLockManager.onAppForegrounded(prefs)
        assertTrue(AppLockManager.isLocked(prefs))
    }
    @Test fun `Android 10 accepts the same authenticator mask used for availability`() {
        // The old STRONG | DEVICE_CREDENTIAL mask throws on API 29.
        androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("Test Alexandru Butica")
            .setAllowedAuthenticators(AppLockManager.getAuthenticators()).build()
    }
}
