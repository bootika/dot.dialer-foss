package dev.goodwy.rphone.controller

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase

/** Compatibility path only. Operational server mode acknowledges through verified Publisher state. */
internal object PlayLegacyPurchaseUpdates {
    fun accept(client: BillingClient?, purchase: Purchase, complete: () -> Unit, failure: () -> Unit) {
        if (purchase.isAcknowledged) { complete(); return }
        if (client == null) { failure(); return }
        try {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) complete() else failure()
            }
        } catch (_: RuntimeException) { failure() }
    }
}
