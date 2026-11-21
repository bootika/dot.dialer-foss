package dev.goodwy.rphone.controller

import dev.goodwy.rphone.controller.license.PlanProduct
import dev.goodwy.rphone.controller.license.PlanProducts
import io.github.bootika.dotdialer.core.license.AppPlan

/** Tokens are short-lived private checkout capabilities, never serializable ownership/cache state. */
internal class ActivePlaySubscription(val products:Set<String>, private val token:String, val plan:AppPlan?) {
    fun purchaseToken()=token
    override fun toString()="ActivePlaySubscription(products=$products, token=[private])"
}
internal enum class PlayReplacementMode { CHARGE_FULL_PRICE, WITHOUT_PRORATION }
internal class PlaySubscriptionReplacement(private val token:String,val mode:PlayReplacementMode) {
    fun purchaseToken()=token
    override fun toString()="PlaySubscriptionReplacement(mode=$mode, token=[private])"
}
internal sealed interface PlayCheckoutDecision {
    class Ready(val replacement:PlaySubscriptionReplacement?=null):PlayCheckoutDecision
    data object AlreadyOwned:PlayCheckoutDecision
    data object ManageSubscription:PlayCheckoutDecision
    data object Pending:PlayCheckoutDecision
    data object Unavailable:PlayCheckoutDecision
}
internal object PlayPlanCheckout {
    fun decide(target:PlanProduct,inApps:Set<String>,subscriptions:List<ActivePlaySubscription>,pending:Boolean,
        independentPlan:AppPlan=AppPlan.FREE,gift:Boolean=false,independentReady:Boolean=true):PlayCheckoutDecision {
        if(!independentReady)return PlayCheckoutDecision.Unavailable
        if(independentPlan>=target.tier || gift)return PlayCheckoutDecision.AlreadyOwned
        if(pending)return PlayCheckoutDecision.Pending
        val permanent=PlayStoreOwnership(inApps,emptySet())
        if(maxOf(permanent.plan,if(permanent.legacyInApp)AppPlan.PREMIUM else AppPlan.FREE)>=target.tier)return PlayCheckoutDecision.AlreadyOwned
        if(subscriptions.any {target.id in it.products})return PlayCheckoutDecision.AlreadyOwned
        if(!target.subscription)return if(subscriptions.isEmpty())PlayCheckoutDecision.Ready() else PlayCheckoutDecision.ManageSubscription
        if(subscriptions.isEmpty())return PlayCheckoutDecision.Ready()
        val old=subscriptions.singleOrNull()?:return PlayCheckoutDecision.ManageSubscription
        val oldPlan=old.plan?:return PlayCheckoutDecision.ManageSubscription
        if(oldPlan>target.tier || old.products.size!=1 || old.purchaseToken().isBlank())return PlayCheckoutDecision.ManageSubscription
        return PlayCheckoutDecision.Ready(PlaySubscriptionReplacement(old.purchaseToken(),
            if(target.tier>oldPlan)PlayReplacementMode.CHARGE_FULL_PRICE else PlayReplacementMode.WITHOUT_PRORATION))
    }
}
