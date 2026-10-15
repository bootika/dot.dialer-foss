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
        val acknowledgement = source
            .substringAfter("client.acknowledgePurchase(params)")
            .substringBefore("private fun completePurchase()")
        val completion = source
            .substringAfter("private fun completePurchase()")
            .substringBefore("private fun queryPurchases(")

        assertTrue(acknowledgement.contains("BillingResponseCode.OK"))
        assertTrue(acknowledgement.contains("completePurchase()"))
        assertTrue(completion.contains("_purchaseSuccess.value = true"))
        assertEquals(1, source.split("_purchaseSuccess.value = true").size - 1)
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
        val launch = source
            .substringAfter("private fun launchBillingFlow(")
            .substringBefore("override fun getPriceDonation")
        val ownership = source
            .substringAfter("private fun queryPurchases(onComplete")
            .substringBefore("override fun refreshAllData()")

        assertTrue(launch.contains("result.responseCode != BillingClient.BillingResponseCode.OK"))
        assertTrue(ownership.contains("allSuccessful"))
        assertTrue(ownership.contains("if (allSuccessful) updateProStatus()"))
        assertTrue(ownership.contains("remaining == 0"))
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
