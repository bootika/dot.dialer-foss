package io.github.bootika.dotdialer.core.license

import org.junit.Assert.*
import org.junit.Test

class PlanEntitlementsTest {
    @Test fun `display names change without renaming persisted tiers or ordinals`() {
        assertEquals(listOf("Free","Power Up","Complete"),AppPlan.entries.map {it.displayName})
        assertEquals(listOf("FREE","PLUS","PREMIUM"),AppPlan.entries.map {it.name})
        assertEquals(listOf(0,1,2),AppPlan.entries.map {it.ordinal})
    }
    @Test fun `free keeps no paid work plus manual premium all`() {
        for(plan in AppPlan.entries) for(feature in PlanFeature.entries) {
            val expected=if(feature==PlanFeature.MANUAL_OPTIMIZER) plan>=AppPlan.PLUS else plan==AppPlan.PREMIUM
            assertEquals("$plan $feature",expected,PlanEntitlements(plan).allows(feature,1000))
        }
    }
    @Test fun `trial boundaries are exact and preserve purchased plus`() {
        val rights=PlanEntitlements(AppPlan.PLUS,100,200)
        assertEquals(AppPlan.PLUS,rights.effective(99))
        assertEquals(AppPlan.PREMIUM,rights.effective(100))
        assertEquals(AppPlan.PREMIUM,rights.effective(199))
        assertEquals(AppPlan.PLUS,rights.effective(200))
        assertEquals(AppPlan.FREE,PlanEntitlements(AppPlan.FREE,100,200).effective(200))
    }
    @Test fun `guides show only newly unlocked tiers in order`() {
        assertEquals(emptyList<AppPlan>(),PlanGuidePolicy.pending(AppPlan.FREE,AppPlan.FREE))
        assertEquals(listOf(AppPlan.PLUS),PlanGuidePolicy.pending(AppPlan.PLUS,AppPlan.FREE))
        assertEquals(listOf(AppPlan.PLUS,AppPlan.PREMIUM),PlanGuidePolicy.pending(AppPlan.PREMIUM,AppPlan.FREE))
        assertEquals(listOf(AppPlan.PREMIUM),PlanGuidePolicy.pending(AppPlan.PREMIUM,AppPlan.PLUS))
        assertTrue(PlanGuidePolicy.pending(AppPlan.PLUS,AppPlan.PREMIUM).isEmpty())
        assertTrue(PlanGuidePolicy.pending(AppPlan.PREMIUM,AppPlan.PREMIUM).isEmpty())
    }
    @Test fun `WhatsApp channel is Complete only with the same trial cutoff`() {
        for (plan in AppPlan.entries) {
            val rights = PlanEntitlements(plan, 100, 200)
            assertEquals(plan == AppPlan.PREMIUM, rights.allows(PlanFeature.CALL_MESSAGE_CHANNEL, 99))
            assertTrue(rights.allows(PlanFeature.CALL_MESSAGE_CHANNEL, 100))
            assertTrue(rights.allows(PlanFeature.CALL_MESSAGE_CHANNEL, 199))
            assertEquals(plan == AppPlan.PREMIUM, rights.allows(PlanFeature.CALL_MESSAGE_CHANNEL, 200))
        }
    }

}
