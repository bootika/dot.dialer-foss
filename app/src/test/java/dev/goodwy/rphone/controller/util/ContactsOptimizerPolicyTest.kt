package dev.goodwy.rphone.controller.util

import org.junit.Assert.*
import org.junit.Test

class ContactsOptimizerPolicyTest {
    private fun photo(w: Int, h: Int = w) = ContactPhotoImageInfo(10, w, h, 0)
    @Test fun manualNeverWritesAndFillOnlyNeedsAbsence() {
        assertFalse(optimizerAccepts(ContactPhotoAutomaticPolicy.Manual, null, photo(512), 1024))
        assertTrue(optimizerAccepts(ContactPhotoAutomaticPolicy.FillMissing, null, photo(192), null))
        assertFalse(optimizerAccepts(ContactPhotoAutomaticPolicy.FillMissing, photo(96), photo(512), 1024))
    }
    @Test fun newestWithoutLossRejectsEitherShrinkingAxisAndActualProviderScaling() {
        val policy = ContactPhotoAutomaticPolicy.Newest
        assertFalse(optimizerAccepts(policy, photo(512), photo(192), 1024))
        assertFalse(optimizerAccepts(policy, photo(512), photo(1024, 400), 1024))
        assertFalse(optimizerAccepts(policy, photo(640), photo(1024), 512))
        assertFalse(optimizerAccepts(policy, photo(96), photo(512), null))
        assertTrue(optimizerAccepts(policy, photo(512), photo(512), 720))
        assertTrue(optimizerAccepts(policy, photo(96), photo(512), 720))
    }
    @Test fun qualityImprovesBelowThresholdAndPreservesAdequatePhotos() {
        val policy = ContactPhotoAutomaticPolicy.Optimal
        assertTrue(optimizerAccepts(policy, photo(96), photo(512), 720))
        assertFalse(optimizerAccepts(policy, photo(512), photo(1024), 1024))
        assertFalse(optimizerAccepts(policy, photo(192), photo(192), 1024))
        assertFalse(optimizerAccepts(policy, photo(512, 400), photo(1024, 600), 512))
    }
    @Test fun onlyExplicitLatestAllowsAutomaticDownsizingAndDefaultRemainsProtected() {
        assertTrue(optimizerAccepts(ContactPhotoAutomaticPolicy.Latest, photo(512), photo(192), 720))
        assertEquals(ContactPhotoAutomaticPolicy.Newest, ContactOptimizationSettings().effectivePhotoPolicy)
        assertEquals(ContactPhotoAutomaticPolicy.Newest, ContactPhotoAutomaticPolicy.fromStored("obsolete"))
        assertEquals(ContactPhotoAutomaticPolicy.FillMissing, ContactOptimizationSettings(autoUpdateExisting=false).effectivePhotoPolicy)
    }
}
