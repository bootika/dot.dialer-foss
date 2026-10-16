package io.github.bootika.dotdialer.core.license

import org.junit.Assert.*
import org.junit.Test

class PlanEntitlementsTest {
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
}
