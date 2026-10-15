package dev.goodwy.rphone.view.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernPastelAvatarPaletteTest {
    @Test fun `unknown caller uses stable canonical number rather than empty grey seed`() {
        assertEquals(avatarIdentityColorSeed("", "+40 721 234 567"), avatarIdentityColorSeed("", "\u202A+40721234567\u202C"))
        val seeds = (0..99).map { avatarIdentityColorSeed("", "07000000${it.toString().padStart(2, '0')}") }
        assertEquals(100, seeds.toSet().size)
        assertTrue(seeds.map { modernPastelAvatarColor(it).first }.toSet().size >= 12)
        assertEquals(canonicalAvatarColorSeed("Loredana"), avatarIdentityColorSeed("Loredana", "0700000099"))
        assertEquals(canonicalAvatarColorSeed(""), avatarIdentityColorSeed("", ""))
    }
    @Test
    fun `same contact always receives the same pastel pair`() {
        assertEquals(
            modernPastelAvatarColor("Alexandru Butica"),
            modernPastelAvatarColor("Alexandru Butica"),
        )
    }

    @Test
    fun `palette distributes a representative contact list`() {
        val colors = listOf(
            "Loredana",
            "Andrei Branea",
            "Raluca Staicu",
            "Mihai Pop",
            "Tata",
            "Patrix",
            "Test Alexandru Butica",
        ).map { modernPastelAvatarColor(it).first }.toSet()

        assertTrue(colors.size >= 4)
    }

    @Test
    fun `modern pastel initials stay white in light and dark themes`() {
        val names = listOf("Loredana", "Andrei Branea", "Raluca Staicu", "Mihai Pop", "Tata")

        names.forEach { name ->
            assertEquals(Color.White, modernPastelAvatarColor(name).second)
        }
    }

    @Test
    fun `reference palette keeps the supplied white initials`() {
        modernPastelAvatarColors.forEach { (_, foreground) ->
            assertEquals(Color.White, foreground)
        }
    }

    @Test
    fun `multicolor palette exactly matches Thunderbird light fallback colors in upstream order`() {
        val thunderbirdColors = listOf(
            Color(0xFFE57373),
            Color(0xFF9575CD),
            Color(0xFF4FC3F7),
            Color(0xFF81C784),
            Color(0xFFFF8A65),
            Color(0xFF90A4AE),
            Color(0xFFF06292),
            Color(0xFF7986CB),
            Color(0xFF4DD0E1),
            Color(0xFFFFCA28),
            Color(0xFFA1887F),
            Color(0xFFBA68C8),
            Color(0xFF64B5F6),
            Color(0xFF4DB6AC),
            Color(0xFFFFA726),
        )

        assertEquals(thunderbirdColors, modernPastelAvatarColors.map { it.first })
    }

    @Test
    fun `formatted versions of the same phone number keep the same color`() {
        assertEquals(
            modernPastelAvatarColor("+40 721 234 567"),
            modernPastelAvatarColor("\u202A+40721234567\u202C"),
        )
    }

    @Test
    fun `contact color stays stable regardless of list position and neighbours`() {
        val firstList = distinctAdjacentAvatarColorIndices(listOf("Alpha", "Same contact", "Beta"))
        val secondList = distinctAdjacentAvatarColorIndices(listOf("Beta", "Alpha", "Same contact"))

        assertEquals(modernPastelAvatarIndex("Same contact"), firstList[1])
        assertEquals(modernPastelAvatarIndex("Same contact"), secondList[2])
    }

    @Test
    fun `color index uses Thunderbird positive hash selection`() {
        assertEquals(9, modernPastelAvatarIndex("abc"))
    }

    @Test
    fun `legacy multicolor schemes migrate to the curated multicolor palette`() {
        assertTrue(usesMulticolorAvatarPalette(modernPastel = true, legacyVibrant = false, legacyGoogle = false))
        assertTrue(usesMulticolorAvatarPalette(modernPastel = false, legacyVibrant = true, legacyGoogle = false))
        assertTrue(usesMulticolorAvatarPalette(modernPastel = false, legacyVibrant = false, legacyGoogle = true))
        assertFalse(usesMulticolorAvatarPalette(modernPastel = false, legacyVibrant = false, legacyGoogle = false))
    }
}
