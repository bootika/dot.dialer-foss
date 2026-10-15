package io.github.bootika.dotdialer.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextNormalizerTest {
    @Test
    fun `folds Romanian diacritics and case`() {
        assertEquals(
            "stefan taran in bacau",
            SearchTextNormalizer.normalize("Ștefan Țăran în Bacău", foldDiacritics = true),
        )
    }

    @Test
    fun `folds composed combining and special Latin letters`() {
        assertEquals(
            "lodz francois strasse smorrebrod",
            SearchTextNormalizer.normalize("Łódź François Straße Smørrebrød", foldDiacritics = true),
        )
    }

    @Test
    fun `folds Vietnamese diacritics and stroke d into the Latin base alphabet`() {
        assertEquals(
            "dang thi thuy",
            SearchTextNormalizer.normalize("Đặng Thị Thúy", foldDiacritics = true),
        )
    }

    @Test
    fun `preserves diacritic distinction when folding is disabled`() {
        assertFalse(SearchTextNormalizer.contains("Ștefan", "Stefan", foldDiacritics = false))
        assertTrue(SearchTextNormalizer.contains("Ștefan", "ște", foldDiacritics = false))
    }

    @Test
    fun `does not damage non Latin writing`() {
        assertEquals(
            "иван 東京",
            SearchTextNormalizer.normalize("ИВАН 東京", foldDiacritics = true),
        )
    }

    @Test
    fun `maps folded Romanian names to T9`() {
        assertEquals("783326", SearchTextNormalizer.toT9("Ștefan", foldDiacritics = true))
        assertEquals(" 83326", SearchTextNormalizer.toT9("Ștefan", foldDiacritics = false))
    }

    @Test
    fun `maps folded matches back to source offsets`() {
        assertEquals(
            listOf(SearchTextNormalizer.MatchRange(start = 6, endExclusive = 12)),
            SearchTextNormalizer.findMatchRanges(
                text = "Salut Ștefan!",
                query = "stefan",
                foldDiacritics = true,
            ),
        )
    }

    @Test
    fun `uses the same phone identity for local and international formatting`() {
        assertEquals(
            SearchTextNormalizer.phoneIdentityKey("+40 775 523 984"),
            SearchTextNormalizer.phoneIdentityKey("0775 523 984"),
        )
    }
}
