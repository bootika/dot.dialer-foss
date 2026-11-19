package dev.goodwy.rphone.controller

import android.app.Application
import dev.goodwy.rphone.controller.license.PlanProducts
import io.github.bootika.dotdialer.core.license.AppPlan
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlayStoreOwnershipCacheTest {
    @Test
    fun lifetimePlansSurviveCacheRecreationWithoutAStoreRequest() {
        for ((product, tier) in listOf("dot_plus_lifetime" to AppPlan.PLUS, "dot_premium_lifetime" to AppPlan.PREMIUM)) {
            var record: JSONObject? = null
            PlayStoreOwnershipCache({ record }, { record = JSONObject(it.toString()) }, { record = null })
                .save(PlayStoreOwnership(setOf(product), emptySet()))
            val restored = PlayStoreOwnershipCache({ record }, { error("Restore must not write") }).restore()!!
            assertEquals(setOf(product), restored.inApps)
            assertEquals(tier, PlanProducts.purchased(restored.inApps + restored.subscriptions))
            assertEquals(setOf("version", "inApps", "subscriptions"), record!!.keys().asSequence().toSet())
        }
    }

    @Test
    fun authoritativeEmptyOwnershipRemovesPreviouslyOwnedSubscription() {
        var record: JSONObject? = null
        val cache = PlayStoreOwnershipCache({ record }, { record = JSONObject(it.toString()) }, { record = null })
        cache.save(PlayStoreOwnership(emptySet(), setOf("dot_premium_yearly")))
        assertEquals(AppPlan.PREMIUM, PlanProducts.purchased(cache.restore()!!.subscriptions))
        cache.save(PlayStoreOwnership(emptySet(), emptySet()))
        val restarted = PlayStoreOwnershipCache({ record }, { error("Restore must not write") }).restore()!!
        assertEquals(AppPlan.FREE, PlanProducts.purchased(restarted.inApps + restarted.subscriptions))
    }

    @Test
    fun unknownProductsAndInvalidRecordCannotGrantAccess() {
        val invalid = JSONObject().put("version", 1).put("inApps", JSONArray(listOf("unknown_lifetime")))
            .put("subscriptions", JSONArray())
        assertNull(PlayStoreOwnershipCache({ invalid }, {}).restore())
        invalid.put("inApps", JSONArray()).put("purchaseToken", "private-token-placeholder")
        assertNull(PlayStoreOwnershipCache({ invalid }, {}).restore())
        assertNull(PlayStoreOwnershipCache({ throw java.io.IOException() }, {}).restore())
    }

    @Test
    fun savingNeverPersistsUnknownProductsOrTokens() {
        var record: JSONObject? = null
        val cache = PlayStoreOwnershipCache({ record }, { record = it })
        cache.save(PlayStoreOwnership(setOf("dot_plus_lifetime", "unknown"), setOf("dot_premium_monthly", "unknown")))
        assertEquals(setOf("dot_plus_lifetime"), cache.restore()!!.inApps)
        assertEquals(setOf("dot_premium_monthly"), cache.restore()!!.subscriptions)
        assertFalse(record.toString().contains("unknown"))
        assertFalse(record.toString().contains("token", ignoreCase = true))
    }

    @Test
    fun encryptedOwnershipRoundTripsWithoutPlaintextProductIds() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val file = java.io.File(app.noBackupFilesDir, "play-ownership-regression.bin")
        val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 7 }, "AES")
        val storage = dev.goodwy.rphone.controller.license.GiftLicenseStorage(
            app, "play-ownership-regression", file.name, keyProvider = { key },
        )
        try {
            val cache = PlayStoreOwnershipCache(storage::read, storage::write)
            cache.save(PlayStoreOwnership(setOf("dot_premium_lifetime"), emptySet()))
            assertTrue(file.exists())
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("dot_premium_lifetime"))
            val restarted = PlayStoreOwnershipCache(storage::read, storage::write).restore()!!
            assertEquals(AppPlan.PREMIUM, restarted.plan)
        } finally {
            file.delete()
        }
    }
    @Test
    fun failedReplacementAfterConfirmedDowngradeCannotResurrectPriorRights() {
        var record: JSONObject? = null
        var failWrite = false
        val cache = PlayStoreOwnershipCache({ record }, {
            if (failWrite) throw java.io.IOException("storage unavailable")
            record = JSONObject(it.toString())
        }, { record = null })
        cache.save(PlayStoreOwnership(setOf("dot_premium_lifetime"), emptySet()))
        failWrite = true
        assertThrows(java.io.IOException::class.java) {
            cache.save(PlayStoreOwnership(setOf("dot_plus_lifetime"), emptySet()))
        }
        assertNull(PlayStoreOwnershipCache({ record }, {}).restore())
    }

    @Test
    fun failedUpgradeWriteKeepsPreviouslyConfirmedOfflineRights() {
        var record: JSONObject? = null
        var failWrite = false
        var invalidations = 0
        val cache = PlayStoreOwnershipCache({ record }, {
            if (failWrite) throw java.io.IOException("storage unavailable")
            record = JSONObject(it.toString())
        }, { invalidations++; record = null })
        val existing = PlayStoreOwnership(setOf("dot_plus_lifetime"), emptySet())
        cache.save(existing)
        failWrite = true
        assertThrows(java.io.IOException::class.java) {
            cache.save(PlayStoreOwnership(existing.inApps + "dot_premium_lifetime", emptySet()))
        }
        assertEquals(existing, cache.restore())
        assertEquals(0, invalidations)
    }
}