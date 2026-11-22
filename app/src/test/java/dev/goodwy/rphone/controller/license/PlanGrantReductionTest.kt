package dev.goodwy.rphone.controller.license

import io.github.bootika.dotdialer.core.license.*
import org.junit.Assert.*
import org.junit.Test

class PlanGrantReductionTest {
    private fun grant(terms:Map<AppPlan,Long?>,trialEnd:Long=0)=PlanGrant(
        PlanEntitlements(terms.keys.maxOrNull()?:AppPlan.FREE,if(trialEnd>0)1 else 0,trialEnd),10000,terms)
    @Test fun reductionsInspectAllFutureRightsNotJustTheTierToday() {
        val before=grant(mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 5000L))
        assertTrue(grant(mapOf(AppPlan.PREMIUM to 5000L)).reduces(before))
        assertTrue(grant(mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 4000L)).reduces(before))
        assertFalse(grant(mapOf(AppPlan.PREMIUM to null)).reduces(before))
        assertFalse(grant(mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 6000L)).reduces(before))
        assertFalse(grant(mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 5000L)).reduces(before))
    }
    @Test fun lifetimeAndTrialReductionsAreDistinguishedFromOutageAndUpgrades() {
        val lifetime=grant(mapOf(AppPlan.PREMIUM to null))
        assertTrue(grant(emptyMap()).reduces(lifetime))
        assertTrue(grant(mapOf(AppPlan.PREMIUM to 5000L)).reduces(lifetime))
        assertFalse(lifetime.reduces(grant(mapOf(AppPlan.PREMIUM to 5000L))))
        assertTrue(grant(mapOf(AppPlan.PLUS to null),4000L).reduces(grant(mapOf(AppPlan.PLUS to null),5000L)))
        assertFalse(lifetime.reduces(grant(emptyMap(),5000L)))
    }
}
