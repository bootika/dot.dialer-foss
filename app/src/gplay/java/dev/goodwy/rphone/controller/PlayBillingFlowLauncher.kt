package dev.goodwy.rphone.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.CancellationException

/** Re-evaluate external access after network preparation, immediately before any charge UI. */
internal class PlayBillingFlowLauncher(
    private val principal: suspend () -> String?,
    private val current: () -> Boolean,
    private val decide: () -> PlayCheckoutDecision,
    private val finish: () -> Unit,
    private val launch: (BillingFlowParams) -> BillingResult,
    private val blocked: (PlayCheckoutDecision) -> Unit,
    private val failure: () -> Unit,
) {
    suspend fun start(product: BillingFlowParams.ProductDetailsParams) {
        val account = try { principal() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { if(current())failure();return }
        if (!current()) return
        when (val decision = decide()) {
            is PlayCheckoutDecision.Ready -> {
                val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product))
                account?.let(params::setObfuscatedAccountId)
                decision.replacement?.let { replacement ->
                    val mode = when (replacement.mode) {
                        PlayReplacementMode.CHARGE_FULL_PRICE -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE
                        PlayReplacementMode.WITHOUT_PRORATION -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION
                    }
                    params.setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                        .setOldPurchaseToken(replacement.purchaseToken()).setSubscriptionReplacementMode(mode).build())
                }
                finish()
                try {
                    val result = launch(params.build())
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) failure()
                } catch (_: RuntimeException) { failure() }
            }
            else -> blocked(decision)
        }
    }
}
