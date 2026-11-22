package dev.goodwy.rphone.controller

import org.junit.Assert.*
import org.junit.Test

class PlayMigrationCacheTest {
    private val none=PlayStoreOwnership(emptySet(),emptySet())
    @Test fun finiteNewPurchaseNeverEntersUnboundedLegacyCache() {
        assertNull(PlayMigrationCache.after(null,PlayStoreOwnership(emptySet(),setOf("dot_plus_monthly")),false))
        assertNull(PlayMigrationCache.after(none,PlayStoreOwnership(emptySet(),setOf("dot_plus_monthly")),false))
    }
    @Test fun failedUpgradePersistencePreservesOnlyPriorAcquiredRights() {
        val previous=PlayStoreOwnership(setOf("dot_plus_lifetime"),emptySet())
        assertNull(PlayMigrationCache.after(previous,PlayStoreOwnership(previous.inApps,setOf("dot_premium_monthly")),false))
    }
    @Test fun signedReductionWithFailedPersistenceInvalidatesOnlyRemovedLegacyProducts() {
        val previous=PlayStoreOwnership(setOf("dot_plus_lifetime"),setOf("dot_premium_monthly"))
        assertEquals(PlayStoreOwnership(previous.inApps,emptySet()),
            PlayMigrationCache.after(previous,PlayStoreOwnership(previous.inApps,emptySet()),false))
        assertEquals(none,PlayMigrationCache.after(previous,none,false))
    }
    @Test fun durableSignedSourceRemovesLegacyFallbackInsteadOfDuplicatingGrant() {
        val previous=PlayStoreOwnership(setOf("dot_plus_lifetime"),emptySet())
        assertEquals(none,PlayMigrationCache.after(previous,previous,true))
    }
}
