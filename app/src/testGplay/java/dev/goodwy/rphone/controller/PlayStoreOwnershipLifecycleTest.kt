package dev.goodwy.rphone.controller

import android.app.Application
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.license.AppPlan
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlayStoreOwnershipLifecycleTest {
    @Test
    fun constructorRestoresEachPaidTierBeforeOpeningPlansOrConnectingBilling() {
        for ((product, tier) in listOf("dot_plus_lifetime" to AppPlan.PLUS, "dot_premium_lifetime" to AppPlan.PREMIUM)) {
            var record: JSONObject? = null
            val cache = PlayStoreOwnershipCache({ record }, { record = JSONObject(it.toString()) }, { record = null })
            cache.save(PlayStoreOwnership(setOf(product), emptySet()))
            val app = RuntimeEnvironment.getApplication()
            val helper = PlayStoreViewModel(app, PreferenceManager(app), ownershipCache = cache)
            assertEquals(tier, helper.storePlan.value)
            assertEquals(tier == AppPlan.PREMIUM, helper.isPro.value)
            assertTrue(helper.isIapPurchased(product))
            val client = PlayStoreViewModel::class.java.getDeclaredField("billingClient").apply { isAccessible = true }
            assertNull(client.get(helper))
        }
    }

    @Test
    fun authoritativeEmptyQueryPersistsRevocationAcrossRestart() {
        var record: JSONObject? = null
        val cache = PlayStoreOwnershipCache({ record }, { record = JSONObject(it.toString()) }, { record = null })
        cache.save(PlayStoreOwnership(setOf("dot_premium_lifetime"), emptySet()))
        val app = RuntimeEnvironment.getApplication()
        val helper = PlayStoreViewModel(app, PreferenceManager(app), ownershipCache = cache)
        for (name in listOf("iapPurchased", "subPurchased")) {
            val field = PlayStoreViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val owned = field.get(helper) as MutableStateFlow<Set<String>>
            owned.value = emptySet()
        }
        PlayStoreViewModel::class.java.getDeclaredMethod("updateProStatus").apply { isAccessible = true }.invoke(helper)
        assertEquals(AppPlan.FREE, helper.storePlan.value)
        val restarted = PlayStoreViewModel(app, PreferenceManager(app), ownershipCache = cache)
        assertEquals(AppPlan.FREE, restarted.storePlan.value)
        assertFalse(restarted.isPro.value)
    }
    @Test
    fun confirmedRevocationWithWriteFailureCannotRestoreStalePaidCache() {
        var record: JSONObject? = null
        var failWrite = false
        val cache = PlayStoreOwnershipCache({ record }, {
            if (failWrite) throw java.io.IOException("storage unavailable")
            record = JSONObject(it.toString())
        }, { record = null })
        cache.save(PlayStoreOwnership(setOf("dot_premium_lifetime"), emptySet()))
        val app = RuntimeEnvironment.getApplication()
        val helper = PlayStoreViewModel(app, PreferenceManager(app), ownershipCache = cache)
        val field = PlayStoreViewModel::class.java.getDeclaredField("iapPurchased").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val owned = field.get(helper) as MutableStateFlow<Set<String>>
        owned.value = emptySet()
        failWrite = true
        PlayStoreViewModel::class.java.getDeclaredMethod("updateProStatus").apply { isAccessible = true }.invoke(helper)
        assertNull(record)
        val restarted = PlayStoreViewModel(app, PreferenceManager(app), ownershipCache = cache)
        assertEquals(AppPlan.FREE, restarted.storePlan.value)
        assertFalse(restarted.isPro.value)
    }

    @Test
    fun cancelledPurchaseDoesNotAcknowledgeOrChangeCachedRights() {
        var record: JSONObject? = null
        val cache = PlayStoreOwnershipCache({ record }, { record = it }, { record = null })
        cache.save(PlayStoreOwnership(setOf("dot_plus_lifetime"), emptySet()))
        val app = RuntimeEnvironment.getApplication()
        val helper = PlayStoreViewModel(app, PreferenceManager(app), ownershipCache = cache)
        val listener = PlayStoreViewModel::class.java.getDeclaredField("purchasesUpdatedListener")
            .apply { isAccessible = true }.get(helper) as com.android.billingclient.api.PurchasesUpdatedListener
        listener.onPurchasesUpdated(com.android.billingclient.api.BillingResult.newBuilder()
            .setResponseCode(com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED).build(), null)
        assertEquals(AppPlan.PLUS, helper.storePlan.value)
        assertEquals(AppPlan.PLUS, cache.restore()!!.plan)
        assertFalse(helper.purchaseSuccess.value)
        assertNull(PlayStoreViewModel::class.java.getDeclaredField("billingClient").apply { isAccessible = true }.get(helper))
    }
}
