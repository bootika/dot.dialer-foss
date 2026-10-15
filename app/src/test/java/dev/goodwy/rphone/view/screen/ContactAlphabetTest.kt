package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContactAlphabetTest {
    @Test
    fun `contact rail always exposes the complete alphabet`() {
        assertEquals(27, CONTACT_ALPHABET.size)
        assertEquals("A", CONTACT_ALPHABET.first())
        assertEquals("#", CONTACT_ALPHABET.last())
    }

    @Test
    fun `alphabet targets retain the first row for every populated letter`() {
        val targets = contactAlphabetTargets(
            listOf("Ana", "Andrei", "Bogdan", "Đặng Thị Thúy", "Ștefan", "0722 000 000"),
        )

        assertEquals(0, targets["A"])
        assertEquals(2, targets["B"])
        assertEquals(3, targets["D"])
        assertEquals(4, targets["S"])
        assertEquals(5, targets["#"])
        assertFalse(targets.containsKey("C"))
    }

    @Test
    fun `empty rail letters resolve to the nearest populated section`() {
        val resolved = resolvedContactAlphabetTargets(
            mapOf("A" to 1, "D" to 9, "#" to 20),
        )

        assertEquals(27, resolved.size)
        assertEquals(1, resolved["A"])
        assertEquals(9, resolved["B"])
        assertEquals(9, resolved["C"])
        assertEquals(9, resolved["Z"])
        assertEquals(20, resolved["#"])
    }
}
