package dev.goodwy.rphone.controller

import dev.goodwy.rphone.controller.license.PlanProducts
import io.github.bootika.dotdialer.core.license.AppPlan
import org.junit.Assert.*
import org.junit.Test

class PlayPlanCheckoutTest {
    private fun product(id:String)=PlanProducts.all.single {it.id==id}
    private fun subscription(id:String,plan:AppPlan?=AppPlan.PLUS,token:String="private-test-token")=
        ActivePlaySubscription(setOf(id),token,plan)
    @Test fun independentGrantArrivingDuringOwnershipReadPreventsDuplicateCheckout() {
        val target=product("dot_premium_yearly")
        assertTrue(PlayPlanCheckout.decide(target,emptySet(),emptyList(),false) is PlayCheckoutDecision.Ready)
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,AppPlan.PREMIUM))
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,gift=true))
        assertTrue(PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,AppPlan.PLUS) is PlayCheckoutDecision.Ready)
    }
    @Test fun uninitializedLocalSourcesCannotBeTreatedAsFreeBeforeEncryptedGrantRestore() {
        val target=product("dot_premium_yearly")
        assertEquals(PlayCheckoutDecision.Unavailable,PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,independentReady=false))
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,AppPlan.PREMIUM,independentReady=true))
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,gift=true,independentReady=true))
        assertTrue(PlayPlanCheckout.decide(target,emptySet(),emptyList(),false,independentReady=true) is PlayCheckoutDecision.Ready)
    }
    @Test fun higherTierSubscriptionReplacesFreshOwnedTokenWithExplicitMode() {
        val decision=PlayPlanCheckout.decide(product("dot_premium_yearly"),emptySet(),listOf(subscription("dot_plus_monthly")),false)
        assertTrue(decision is PlayCheckoutDecision.Ready)
        val replacement=(decision as PlayCheckoutDecision.Ready).replacement!!
        assertEquals("private-test-token",replacement.purchaseToken())
        assertEquals(PlayReplacementMode.CHARGE_FULL_PRICE,replacement.mode)
        assertFalse(replacement.toString().contains("private-test-token"))
    }
    @Test fun sameTierCadenceUsesWithoutProrationButSameSkuCannotBeBoughtTwice() {
        val old=listOf(subscription("dot_plus_monthly"))
        val next=PlayPlanCheckout.decide(product("dot_plus_yearly"),emptySet(),old,false) as PlayCheckoutDecision.Ready
        assertEquals(PlayReplacementMode.WITHOUT_PRORATION,next.replacement!!.mode)
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(product("dot_plus_monthly"),emptySet(),old,false))
    }
    @Test fun lifetimeCannotBeBoughtWhileAnyActiveSubscriptionIsPresent() {
        for(old in listOf(subscription("dot_plus_monthly"),subscription("legacy",null),subscription("dot_premium_monthly",AppPlan.PREMIUM))){
            assertEquals(PlayCheckoutDecision.ManageSubscription,PlayPlanCheckout.decide(product("dot_premium_lifetime"),emptySet(),listOf(old),false))
        }
        assertTrue(PlayPlanCheckout.decide(product("dot_premium_lifetime"),emptySet(),emptyList(),false) is PlayCheckoutDecision.Ready)
    }
    @Test fun pendingUnknownOrMultipleOwnershipAndDowngradesNeverGuessAToken() {
        val target=product("dot_premium_monthly")
        assertEquals(PlayCheckoutDecision.Pending,PlayPlanCheckout.decide(target,emptySet(),emptyList(),true))
        assertEquals(PlayCheckoutDecision.ManageSubscription,PlayPlanCheckout.decide(target,emptySet(),listOf(subscription("legacy",null)),false))
        assertEquals(PlayCheckoutDecision.ManageSubscription,PlayPlanCheckout.decide(target,emptySet(),listOf(subscription("dot_plus_monthly"),subscription("dot_plus_yearly",token="second-token")),false))
        assertEquals(PlayCheckoutDecision.ManageSubscription,PlayPlanCheckout.decide(product("dot_plus_yearly"),emptySet(),listOf(subscription("dot_premium_monthly",AppPlan.PREMIUM)),false))
    }
    @Test fun lifetimeAndHigherPermanentOwnershipCannotBeSoldAgain() {
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(product("dot_premium_yearly"),setOf("dot_premium_lifetime"),emptyList(),false))
        assertEquals(PlayCheckoutDecision.AlreadyOwned,PlayPlanCheckout.decide(product("dot_plus_lifetime"),setOf("dot_premium_lifetime"),emptyList(),false))
    }
    @Test fun tokenObjectsNeverExposeSecretsInStringRepresentations() {
        assertFalse(subscription("dot_plus_monthly").toString().contains("private-test-token"))
    }
}
