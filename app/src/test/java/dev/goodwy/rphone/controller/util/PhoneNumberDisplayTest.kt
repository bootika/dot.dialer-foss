package dev.goodwy.rphone.controller.util

import org.junit.Assert.*
import org.junit.Test

class PhoneNumberDisplayTest {
    @Test fun nationalAndInternationalFormatsRespectCountryAndPrefix() {
        val cases = listOf(
            Triple("0723123456", "RO", "0723 123 456"),
            Triple("+40723123456", "DE", "+40 723 123 456"),
            Triple("0211234567", "RO", "021 123 4567"),
            Triple("+493614283555", "RO", "+49 361 4283555"),
            Triple("03614283555", "DE", "0361 4283555"),
            Triple("+4930266424242", "RO", "+49 30 266424242"),
            Triple("+33612345678", "RO", "+33 6 12 34 56 78"),
            Triple("+442079460018", "RO", "+44 20 7946 0018"),
            Triple("+12025550123", "DE", "+1 202 555 0123"),
            Triple("+390212345678", "RO", "+39 02 1234 5678"),
            Triple("+819012345678", "RO", "+81 90 1234 5678"),
        )
        for ((raw, region, expected) in cases) assertEquals(raw, expected, PhoneNumberDisplay.format(raw, region))
    }
    @Test fun specialPartialUnknownAndAmbiguousValuesAreUntouched() {
        for (raw in listOf("112", "*222", "*#06#", "0723", "+40", "+999123456789", "unknown", "Office", "sip:12345678@example.org", "0723123456,12", "0723123456;12", "0723123456 ext 12", "+4903614283555")) {
            assertEquals(raw, raw, PhoneNumberDisplay.format(raw, "RO"))
        }
        assertEquals("0723123456", PhoneNumberDisplay.format("0723123456", null))
        assertEquals("03614283555", PhoneNumberDisplay.format("03614283555", "RO"))
        assertEquals("0040723123456", PhoneNumberDisplay.format("0040723123456", "DE"))
    }
    @Test fun everySupportedRegionPreservesAllDiallableCharacters() {
        val util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance()
        for (region in util.supportedRegions) {
            val number = util.getExampleNumber(region) ?: continue
            for (mode in listOf(com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.NATIONAL,
                com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)) {
                val raw = util.format(number, mode)
                val displayed = PhoneNumberDisplay.format(raw, region)
                fun dial(value: String) = value.filter { it.isDigit() || it == '+' }
                assertEquals(region, dial(raw), dial(displayed))
                assertEquals(region, displayed, PhoneNumberDisplay.format(displayed, region))
            }
        }
    }
    @Test fun groupingOffsetsReferToRawDigitsNeverInsertedSpaces() {
        assertEquals(setOf(4,7), PhoneNumberDisplay.boundaries("0723123456", "RO"))
        assertEquals(setOf(3,6,9), PhoneNumberDisplay.boundaries("+40723123456", "RO"))
        assertEquals(emptySet<Int>(), PhoneNumberDisplay.boundaries("*222", "RO"))
        assertEquals(emptySet<Int>(), PhoneNumberDisplay.boundaries("0723 123 456", "RO"))
        assertEquals("\u200e+40 723 123 456", PhoneNumberDisplay.format("\u200e+40723123456", "DE"))
        assertEquals(1f, PhoneNumberDisplay.GROUP_SPACE_SCALE)
    }
}
