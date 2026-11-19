package dev.goodwy.rphone.controller

import android.app.Application
import com.android.billingclient.api.Purchase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlayStoreRestoredPurchasesTest {
    @Test
    fun unacknowledgedRestoredPurchaseWaitsForAcknowledgementBeforeGrant() {
        var reply: ((Boolean) -> Unit)? = null
        val results = mutableListOf<Set<String>?>()
        val purchase = purchase()
        PlayStoreRestoredPurchases({ received, done ->
            assertEquals(purchase.purchaseToken, received.purchaseToken)
            reply = done
        }, { true }).collect(listOf(purchase), results::add)
        assertTrue(results.isEmpty())
        val acknowledgement = requireNotNull(reply)
        acknowledgement(true)
        acknowledgement(true)
        assertEquals(listOf(setOf("dot_premium_lifetime")), results)
    }

    @Test
    fun acknowledgementFailureCannotGrantNewRightsOrReplaceExistingRights() {
        val old = setOf("dot_plus_lifetime")
        var rights = old
        var completed = false
        PlayStoreRestoredPurchases({ _, done -> done(false) }, { true }).collect(listOf(purchase())) {
            completed = true
            if (it != null) rights = it
        }
        assertTrue(completed)
        assertEquals(old, rights)
    }

    @Test
    fun pendingPurchaseIsNotAcknowledgedAndDoesNotGrant() {
        val pending = purchase(state = 4)
        assertEquals(Purchase.PurchaseState.PENDING, pending.purchaseState)
        var result: Set<String>? = null
        PlayStoreRestoredPurchases({ _, _ -> error("Pending purchase must not be acknowledged") }, { true })
            .collect(listOf(pending)) { result = it }
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun acknowledgedPurchaseNeedsNoSecondAcknowledgement() {
        var result: Set<String>? = null
        PlayStoreRestoredPurchases({ _, _ -> error("Already acknowledged") }, { true })
            .collect(listOf(purchase(acknowledged = true))) { result = it }
        assertEquals(setOf("dot_premium_lifetime"), result)
    }

    @Test
    fun supersededAcknowledgementCannotFinishOldQuery() {
        var current = true
        var reply: ((Boolean) -> Unit)? = null
        val results = mutableListOf<Set<String>?>()
        PlayStoreRestoredPurchases({ _, done -> reply = done }, { current })
            .collect(listOf(purchase()), results::add)
        current = false
        reply!!(true)
        assertTrue(results.isEmpty())
    }

    @Test
    fun allRestoredPurchasesMustBeAcknowledgedEvenWithReversedReplies() {
        val replies = mutableListOf<(Boolean) -> Unit>()
        val results = mutableListOf<Set<String>?>()
        PlayStoreRestoredPurchases({ _, done -> replies += done }, { true }).collect(
            listOf(purchase(), purchase(product = "dot_plus_lifetime")), results::add,
        )
        replies[1](true)
        assertTrue(results.isEmpty())
        replies[0](false)
        assertEquals(listOf<Set<String>?>(null), results)
    }

    private fun purchase(state: Int = 0, acknowledged: Boolean = false, product: String = "dot_premium_lifetime") = Purchase(
        JSONObject().put("productId", product).put("purchaseToken", "test-purchase-$product")
            .put("purchaseState", state).put("acknowledged", acknowledged).toString(), "",
    )
}
