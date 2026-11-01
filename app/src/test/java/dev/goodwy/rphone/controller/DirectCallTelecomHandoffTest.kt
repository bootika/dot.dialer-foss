package dev.goodwy.rphone.controller

import android.app.Application
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class DirectCallTelecomHandoffTest {
    @Test fun originalIntentMetadataIsRecognizedWithoutNumberMatching() {
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        val call = Shadow.newInstanceOf(Call::class.java)
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(details, "mIntentExtras", Bundle().apply {
            putString(DirectCallWindow.REQUEST_EXTRA, "test-request")
        })
        assertEquals("test-request", directCallRequestId(call))
        ReflectionHelpers.setField(details, "mIntentExtras", Bundle().apply {
            putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply {
                putString(DirectCallWindow.REQUEST_EXTRA, "test-request-nested")
            })
        })
        assertEquals("test-request-nested", directCallRequestId(call))
        ReflectionHelpers.setField(details, "mIntentExtras", Bundle())
        assertNull(directCallRequestId(call))
    }
}
