package dev.goodwy.rphone.controller.util

import android.telephony.PhoneNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutgoingNumberAndroidFormatterTest {
    @Test
    fun `android formatter converts a romanian national mobile number to e164`() {
        assertEquals(
            "+40722123456",
            PhoneNumberUtils.formatNumberToE164("0722 123 456", "RO"),
        )
    }
}
