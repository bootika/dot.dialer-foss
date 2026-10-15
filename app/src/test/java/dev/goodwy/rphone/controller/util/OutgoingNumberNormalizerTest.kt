package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OutgoingNumberNormalizerTest {
    private val formatter: (String, String) -> String? = { number, region ->
        if (region == "RO" && number.filter(Char::isDigit) == "0722123456") "+40722123456" else null
    }

    @Test
    fun `romanian national number receives country prefix`() {
        assertEquals(
            "+40722123456",
            OutgoingNumberNormalizer.normalize("0722 123 456", true, "ro", formatter),
        )
    }

    @Test
    fun `international numbers are never rewritten`() {
        assertEquals(
            "+40722123456",
            OutgoingNumberNormalizer.normalize("+40722123456", true, "RO", formatter),
        )
        assertEquals(
            "0040722123456",
            OutgoingNumberNormalizer.normalize("0040722123456", true, "RO", formatter),
        )
    }

    @Test
    fun `emergency short and service codes are never rewritten`() {
        listOf("112", "911", "*100#", "*#06#").forEach { number ->
            assertEquals(number, OutgoingNumberNormalizer.normalize(number, true, "RO", formatter))
        }
    }

    @Test
    fun `disabled or unknown region preserves original number`() {
        assertEquals(
            "0722 123 456",
            OutgoingNumberNormalizer.normalize("0722 123 456", false, "RO", formatter),
        )
        assertEquals(
            "0722 123 456",
            OutgoingNumberNormalizer.normalize("0722 123 456", true, null, formatter),
        )
    }

    @Test
    fun `formatter failure preserves original number`() {
        assertEquals(
            "0744 000 000",
            OutgoingNumberNormalizer.normalize("0744 000 000", true, "RO", formatter),
        )
    }
}
