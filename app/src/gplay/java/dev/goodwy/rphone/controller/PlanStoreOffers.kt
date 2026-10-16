package dev.goodwy.rphone.controller

import com.android.billingclient.api.ProductDetails
import dev.goodwy.rphone.controller.license.PlanProducts

/** New tiers use the regular base plan; the app-managed trial is never a store billing offer. */
internal fun ProductDetails.planOffer(planId: String?): ProductDetails.SubscriptionOfferDetails? {
    val offers=subscriptionOfferDetails.orEmpty()
    if(PlanProducts.all.any {it.id==productId})return offers.firstOrNull {it.offerId==null && (planId==null || it.basePlanId==planId)}
    return offers.firstOrNull {planId==null || it.basePlanId==planId}
}
