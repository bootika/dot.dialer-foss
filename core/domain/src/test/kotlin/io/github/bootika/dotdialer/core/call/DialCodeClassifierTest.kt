package io.github.bootika.dotdialer.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialCodeClassifierTest {
    @Test
    fun `device information codes stay local`() {
        assertEquals(DialCodeAction.OpenDeviceInfo, DialCodeClassifier.classify("*#06#"))
        assertEquals(DialCodeAction.OpenSarInfo, DialCodeClassifier.classify(" *#07# "))
    }

    @Test
    fun `android secret code extracts only its digits`() {
        assertEquals(
            DialCodeAction.DispatchSecretCode("4636"),
            DialCodeClassifier.classify("*#*#4636#*#*"),
        )
    }

    @Test
    fun `carrier codes preserve their exact normalized text`() {
        listOf("*124#", "##002#", "*21*1234#", "*#62#").forEach { code ->
            assertEquals(DialCodeAction.PlaceCarrierCode(code), DialCodeClassifier.classify(code))
        }
    }

    @Test
    fun `ordinary numbers and incomplete codes are ignored`() {
        listOf("", "12", "0722123456", "*124", "4636#").forEach { input ->
            assertEquals(DialCodeAction.None, DialCodeClassifier.classify(input))
        }
    }
    @Test fun `carrier and local codes never create a phone call UI`() {
        listOf("*124#", "##002#", "*21*1234#", "*#62#", "*#06#", "*#07#", "*#*#4636#*#*")
            .forEach { assertFalse(it, DialCodeClassifier.shouldShowCallUi(it)) }
    }
    @Test fun `regular calls respect explicit UI suppression`() {
        assertTrue(DialCodeClassifier.shouldShowCallUi("+40720033833"))
        assertFalse(DialCodeClassifier.shouldShowCallUi("+40720033833", requested = false))
    }
    @Test fun `UI eligibility follows the final number rather than previous picker state`() {
        assertFalse(DialCodeClassifier.shouldShowCallUi("*124#"))
        assertTrue(DialCodeClassifier.shouldShowCallUi("+40720033833"))
        assertFalse(DialCodeClassifier.shouldShowCallUi("##002#"))
    }
}
