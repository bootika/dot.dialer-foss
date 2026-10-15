package dev.goodwy.rphone.controller.util

import org.junit.Assert.*
import org.junit.Test

class ContactPhotoSelectionTest {
    private fun photo(id: String, size: Int, previous: Int? = 96, at: Long = 1,
        contact: Long = 10, account: String = "first@example.test") = WhatsAppAvatarReviewItem(
        id,contact,contact+10,null,"Test Alexandru Butica",null,account,"$id-candidate.png",
        previous?.let { "$id-previous.png" },size,size,previous,previous,"fixture",
        WhatsAppAvatarMatchStrength.ExactPhone,at,source="Google")
    private fun choose(items: List<WhatsAppAvatarReviewItem>, preset: ContactPhotoSelectionPreset) =
        contactPhotoPresetSelection(items,preset) { true }
    @Test fun optimalChoosesLargestImprovementForSmallPhotos() {
        assertEquals(setOf("full"),choose(listOf(photo("thumb",96),photo("medium",200),photo("full",512)),ContactPhotoSelectionPreset.Optimal))
    }
    @Test fun optimalLeavesUsefulPhotosAloneButLargestCanChooseBigger() {
        val items=listOf(photo("full",512,512),photo("large",1024,512))
        assertTrue(choose(items,ContactPhotoSelectionPreset.Optimal).isEmpty())
        assertEquals(setOf("large"),choose(items,ContactPhotoSelectionPreset.Largest))
    }
    @Test fun smallUpgradeStillHelpsWhenNo512VersionExists() {
        assertEquals(setOf("medium"),choose(listOf(photo("medium",200)),ContactPhotoSelectionPreset.Optimal))
    }
    @Test fun optimalCanFillAMissingPhotoButNeverShrinksOneAxis() {
        val fill=photo("fill",512,null,contact=20)
        val narrow=photo("narrow",1024).copy(candidateHeight=48)
        assertEquals(setOf("fill"),choose(listOf(narrow,fill),ContactPhotoSelectionPreset.Optimal))
    }
    @Test fun lastFoundMeansQueueTimeRatherThanResolution() {
        val items=listOf(photo("large",1024,at=1),photo("recent",512,at=2))
        assertEquals(setOf("recent"),choose(items,ContactPhotoSelectionPreset.LastFound))
        assertEquals(setOf("large"),choose(items,ContactPhotoSelectionPreset.Largest))
    }
    @Test fun ineligibleCandidatesAreExcludedFromEveryPreset() {
        val items=listOf(photo("blocked",1024),photo("allowed",512))
        for(preset in ContactPhotoSelectionPreset.entries) assertEquals(setOf("allowed"),
            contactPhotoPresetSelection(items,preset) { it.id=="allowed" })
    }
    @Test fun allPresetsRespectTheExistingNoSmallerChoiceRule() {
        for(preset in ContactPhotoSelectionPreset.entries) assertTrue(choose(listOf(photo("small",96,200)),preset).isEmpty())
    }
    @Test fun unknownCurrentDimensionsAreNotAnOptimalUpgrade() {
        assertTrue(choose(listOf(photo("unknown",512).copy(previousWidth=null)),ContactPhotoSelectionPreset.Optimal).isEmpty())
    }
    @Test fun contactsAndAccountsAreGroupedSeparatelyAndNumberRowsIgnored() {
        val items=listOf(photo("first",512),photo("second",1024,contact=11),photo("other",512,account="other@example.test"),
            photo("number",2048).copy(kind="number"))
        assertEquals(setOf("first","second","other"),choose(items,ContactPhotoSelectionPreset.Optimal))
    }
    @Test fun equalityAt200Or96DoesNotCountAsAnImprovement() {
        assertTrue(choose(listOf(photo("same",200,200)),ContactPhotoSelectionPreset.Optimal).isEmpty())
    }
    @Test fun allKeepsExplicitSourceAndFillsOtherContactsWithoutSmallerOrNumberChoices() {
        val items=listOf(photo("manual",200),photo("largest",512),photo("other",512,contact=11),
            photo("smaller",192,512,contact=12),photo("blocked",512,contact=13),
            photo("number",2048).copy(kind="number"))
        assertEquals(setOf("manual","other"),contactPhotoSelectAll(items,setOf("manual","number")) { it.id!="blocked" })
        assertEquals(setOf("largest","other"),contactPhotoSelectAll(items,setOf("blocked")) { it.id!="blocked" })
    }

}
