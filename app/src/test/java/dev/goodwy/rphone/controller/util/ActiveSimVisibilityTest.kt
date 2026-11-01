package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.telephony.SubscriptionManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.content.ComponentName
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class ActiveSimVisibilityTest {
    @Test fun `visibility follows active subscriptions and hides when permission is unavailable`() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val manager = shadowOf(context.getSystemService(SubscriptionManager::class.java))
        val first = SubscriptionInfoBuilder.newBuilder().setId(1).setSimSlotIndex(0).buildSubscriptionInfo()
        val second = SubscriptionInfoBuilder.newBuilder().setId(2).setSimSlotIndex(1).buildSubscriptionInfo()
        val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), "A")
        context.getSystemService(TelecomManager::class.java).registerPhoneAccount(
            PhoneAccount.builder(handle, "Network A").setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION).build())
        manager.setActiveSubscriptionInfoList(listOf(first))
        assertFalse(hasDualSim(context))
        assertNull(callingAccountLabelForDisplay(context, handle))
        manager.setActiveSubscriptionInfoList(listOf(first, second))
        assertTrue(hasDualSim(context))
        assertEquals("Network A", callingAccountLabelForDisplay(context, handle))
        manager.setActiveSubscriptionInfoList(listOf(first))
        assertFalse(hasDualSim(context))
        assertNull(callingAccountLabelForDisplay(context, handle))
        shadowOf(context).denyPermissions(Manifest.permission.READ_PHONE_STATE)
        manager.setActiveSubscriptionInfoList(listOf(first, second))
        assertFalse(hasDualSim(context))
    }
}
