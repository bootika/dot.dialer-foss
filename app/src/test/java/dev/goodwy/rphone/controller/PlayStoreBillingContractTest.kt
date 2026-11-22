package dev.goodwy.rphone.controller

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreBillingContractTest {

    @Test
    fun `purchase success is emitted only by acknowledged completion`() {
        val source = billingSource()
        val acknowledgement = repoFile("app/src/gplay/java/dev/goodwy/rphone/controller/PlayLegacyPurchaseUpdates.kt").readText()
            .substringAfter("client.acknowledgePurchase(params)")
        val completion = source
            .substringAfter("private fun completePurchase()")
            .substringBefore("private fun queryPurchases(")

        assertTrue(acknowledgement.contains("BillingResponseCode.OK"))
        assertTrue(acknowledgement.contains("complete()"))
        assertTrue(completion.contains("_purchaseSuccess.value = true"))
        assertEquals(1, source.split("_purchaseSuccess.value = true").size - 1)
        assertTrue(source.contains("queryPurchases(restore = false) { verified -> _purchaseSuccess.value = verified &&"))
        assertTrue(repoFile("app/src/gplay/java/dev/goodwy/rphone/AppModule.kt").readText().contains("serverAuthorityRequired = true"))
    }

    @Test
    fun `restore and pro checks wait for real purchase queries`() {
        val source = billingSource()
        val restore = source
            .substringAfter("override fun restorePurchases()")
            .substringBefore("override fun checkProStatus()")
        val proCheck = source
            .substringAfter("override fun checkProStatus()")
            .substringBefore("private fun updateProStatus()")

        assertTrue(restore.contains("runWhenBillingReady { queryPurchases() }"))
        assertTrue(proCheck.contains("runWhenBillingReady { queryPurchases() }"))
        assertFalse(source.contains("delay("))
        assertFalse(source.contains("queryPurchaseHistoryAsync"))
    }

    @Test
    fun `billing launch and ownership queries handle unsuccessful results`() {
        val source = billingSource()
        val launch = repoFile("app/src/gplay/java/dev/goodwy/rphone/controller/PlayBillingFlowLauncher.kt").readText()
        val ownership = source
            .substringAfter("private fun queryPurchases(restore:")
            .substringBefore("override fun refreshAllData()")

        assertTrue(launch.contains("result.responseCode != BillingClient.BillingResponseCode.OK"))
        assertTrue(ownership.contains("PlayStoreOwnershipRefresh("))
        assertTrue(ownership.contains("if (accepted != null)"))
        assertTrue(ownership.contains("PlayServerSnapshot.reconcile("))
        val reader=repoFile("app/src/gplay/java/dev/goodwy/rphone/controller/PlayStoreOwnershipRefresh.kt").readText()
        assertTrue(reader.contains("PlayStoreOwnershipQuery("))
        assertTrue(ownership.contains("ownershipQueryGeneration.get() != generation"))
    }

    private fun billingSource(): String = repoFile(
        "app/src/gplay/java/dev/goodwy/rphone/controller/PlayStoreViewModel.kt",
    ).readText()

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $relativePath from $start")
    }
}
