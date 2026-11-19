package dev.goodwy.rphone.controller

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase

/** Restore may discover purchases completed while the process was absent. */
internal class PlayStoreRestoredPurchases(
    private val acknowledge: (Purchase, (Boolean) -> Unit) -> Unit,
    private val isCurrent: () -> Boolean,
) {
    constructor(client: BillingClient, isCurrent: () -> Boolean) : this({ purchase, done ->
        try {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { done(it.responseCode == BillingClient.BillingResponseCode.OK) }
        } catch (error: RuntimeException) {
            android.util.Log.w("PlayOwnership", "acknowledge: ${error.javaClass.simpleName}")
            done(false)
        }
    }, isCurrent)

    fun collect(purchases: List<Purchase>, complete: (Set<String>?) -> Unit) {
        if (!isCurrent()) return
        val owned = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = owned.filterNot(Purchase::isAcknowledged)
        if (pending.isEmpty()) {
            complete(owned.flatMap(Purchase::getProducts).toSet())
            return
        }
        val replies = mutableMapOf<Int, Boolean>()
        pending.forEachIndexed { index, purchase ->
            if (isCurrent()) acknowledge(purchase) { success ->
                synchronized(replies) {
                    if (!isCurrent() || replies.containsKey(index)) return@synchronized
                    replies[index] = success
                    if (replies.size == pending.size) {
                        complete(if (replies.values.all { it }) owned.flatMap(Purchase::getProducts).toSet() else null)
                    }
                }
            }
        }
    }
}
