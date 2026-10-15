package dev.goodwy.rphone.controller.util

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OutgoingDialCodePolicyTest {
    @Test fun encodedCarrierCodesDoNotOpenThePhoneCallScreen() {
        for (code in listOf("*124#", "%2A124%23", "%23%23002%23", "%2A21%2A1234%23", "*#*#4636#*#*")) {
            assertFalse(code, outgoingCallUiAllowed(code))
        }
    }
    @Test fun finalNumberAndExplicitRequestDetermineRegularCallUi() {
        assertTrue(outgoingCallUiAllowed("+40720033833"))
        assertTrue(outgoingCallUiAllowed("%2B40720033833"))
        assertFalse(outgoingCallUiAllowed("+40720033833", requested = false))
        assertFalse(outgoingCallUiAllowed("*124#"))
        assertTrue(outgoingCallUiAllowed("+40720033833"))
    }
}
