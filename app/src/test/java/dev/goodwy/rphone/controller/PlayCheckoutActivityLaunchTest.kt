package dev.goodwy.rphone.controller

import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import dev.goodwy.rphone.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class PlayCheckoutActivityLaunchTest {
    private val activity = Robolectric.buildActivity(MainActivity::class.java).get()

    @Test
    fun nullPurchaseIntentIsRejectedBeforeAndroidStartsCheckout() {
        val checkout = proxyIntent().putExtra("BUY_INTENT", null as PendingIntent?)

        assertThrows(ActivityNotFoundException::class.java) { activity.startActivity(checkout) }
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun validPurchaseIntentReachesAndroid() {
        val purchase = PendingIntent.getActivity(activity, 0, Intent("test.checkout"), PendingIntent.FLAG_IMMUTABLE)
        val checkout = proxyIntent().putExtra("BUY_INTENT", purchase)

        activity.startActivity(checkout)

        assertEquals(checkout.component, shadowOf(activity).nextStartedActivity.component)
    }

    @Test
    fun otherActivityAndNonPurchaseBillingFlowStillReachAndroid() {
        val unrelated = Intent("test.navigation").putExtra("BUY_INTENT", null as PendingIntent?)
        val nonPurchase = proxyIntent().putExtra("IN_APP_MESSAGE_INTENT", "fixture")

        activity.startActivity(unrelated)
        assertEquals(unrelated.action, shadowOf(activity).nextStartedActivity.action)
        activity.startActivity(nonPurchase)
        assertEquals(nonPurchase.component, shadowOf(activity).nextStartedActivity.component)
    }

    private fun proxyIntent() = Intent().setClassName(
        activity.packageName,
        "com.android.billingclient.api.ProxyBillingActivity",
    )
}
