package dev.goodwy.rphone.controller

import android.Manifest
import android.app.Activity
import android.app.Application
import android.telecom.TelecomManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.makeCall
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,36],application=DirectTapProtectionTest.ProductionApplication::class)
class DirectTapProtectionTest {
    // Exercise normal production routing; test-distribution licence checks have their own suite.
    class ProductionApplication : Application() { override fun getPackageName() = "dot.dialer" }
    class ProductionActivity : Activity() { override fun getPackageName() = "dot.dialer" }
    private val app get()=RuntimeEnvironment.getApplication()
    // Robolectric reuses the app sandbox across suites; never inherit another fixture's
    // test-distribution licence singleton in this production-routing test.
    @Before fun isolate() {
        org.robolectric.util.ReflectionHelpers.setStaticField(
            dev.goodwy.rphone.controller.license.TestAccessManager::class.java, "instance", null)
        DirectCallWindow.cancel(); CallUiHandoff.controller.contentReady()
    }
    @After fun clear() { isolate() }
    @Test fun restoredOldOffCannotBypassDirectTapProtectionAndCancelPlacesNoCall() {
        val prefs=PreferenceManager(app)
        runBlocking { prefs.restoreAllPreferences(mapOf("direct_call_cancel_window" to false),replacePortable=true) }
        assertFalse(PreferenceManager(app).getBoolean("direct_call_cancel_window",true))
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val host=Robolectric.buildActivity(ProductionActivity::class.java).setup()
        try {
            assertTrue(dev.goodwy.rphone.controller.license.TestAccessManager.get(host.get()).allowed)
            val telecom=shadowOf(host.get().getSystemService(TelecomManager::class.java))
            makeCall(host.get(),"0700000011",directTap=true)
            assertNotNull(DirectCallWindow.state.value)
            assertEquals(0,telecom.allOutgoingCalls.size)
            DirectCallWindow.cancel()
            assertEquals(0,telecom.allOutgoingCalls.size)
        } finally { host.pause().stop().destroy() }
    }
    @Test fun explicitCallButtonRemainsImmediateWithoutCancellationWindow() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val host=Robolectric.buildActivity(ProductionActivity::class.java).setup()
        try {
            val telecom=shadowOf(host.get().getSystemService(TelecomManager::class.java))
            makeCall(host.get(),"0700000011",directTap=false)
            assertNull(DirectCallWindow.state.value)
            assertEquals(1,telecom.allOutgoingCalls.size)
        } finally { host.pause().stop().destroy() }
    }
}
