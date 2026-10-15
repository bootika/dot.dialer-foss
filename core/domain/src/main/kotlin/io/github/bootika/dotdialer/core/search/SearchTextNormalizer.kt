package io.github.bootika.dotdialer.core.search

import java.text.Normalizer
import java.util.Locale

/**
 * Canonical text preparation used by every local-search surface.
 *
 * Case folding is always enabled. Diacritic folding is a user preference: when disabled,
 * "Ștefan" and "Stefan" remain distinct. The explicit replacements cover common Latin
 * letters which Unicode NFD does not decompose into a base letter plus combining mark.
 */
object SearchTextNormalizer {
    data class MatchRange(val start: Int, val endExclusive: Int)

    fun normalize(text: String, foldDiacritics: Boolean): String {
        val lowercase = text.lowercase(Locale.ROOT)
        if (!foldDiacritics) return lowercase

        return buildString(lowercase.length) {
            lowercase.forEach { character ->
                val replacement = SPECIAL_LATIN_FOLD[character]
                if (replacement != null) {
                    append(replacement)
                } else {
                    val decomposed = Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
                    decomposed.forEach { decomposedCharacter ->
                        if (!decomposedCharacter.isCombiningMark()) append(decomposedCharacter)
                    }
                }
            }
        }
    }

    fun normalizePhone(value: String): String = value.filter(Char::isDigit)

    /**
     * Stable identity used when the same phone number is written in local or international form.
     * Keeping this rule here ensures that loading, caching, and rendering Recents group numbers
     * in exactly the same way.
     */
    fun phoneIdentityKey(value: String): String? =
        normalizePhone(value)
            .takeLast(PHONE_NUMBER_MATCH_DIGITS)
            .ifBlank { null }

    fun toT9(value: String, foldDiacritics: Boolean): String = buildString {
        normalize(value, foldDiacritics).forEach { character ->
            when (character) {
                in 'a'..'c' -> append('2')
                in 'd'..'f' -> append('3')
                in 'g'..'i' -> append('4')
                in 'j'..'l' -> append('5')
                in 'm'..'o' -> append('6')
                in 'p'..'s' -> append('7')
                in 't'..'v' -> append('8')
                in 'w'..'z' -> append('9')
                in '0'..'9' -> append(character)
                else -> append(WORD_SEPARATOR)
            }
        }
    }

    fun contains(text: String, query: String, foldDiacritics: Boolean): Boolean {
        if (query.isBlank()) return false
        return normalize(text, foldDiacritics).contains(normalize(query, foldDiacritics))
    }

    /** Returns source-text offsets even when the normalized query has fewer accents. */
    fun findMatchRanges(
        text: String,
        query: String,
        foldDiacritics: Boolean,
    ): List<MatchRange> {
        if (query.isBlank() || text.isEmpty()) return emptyList()
        val normalizedQuery = normalize(query, foldDiacritics)
        if (normalizedQuery.isEmpty()) return emptyList()

        val normalizedText = StringBuilder(text.length)
        val sourceOffsets = mutableListOf<Int>()
        text.forEachIndexed { sourceIndex, character ->
            normalize(character.toString(), foldDiacritics).forEach { normalizedCharacter ->
                normalizedText.append(normalizedCharacter)
                sourceOffsets.add(sourceIndex)
            }
        }

        val matches = mutableListOf<MatchRange>()
        var searchFrom = 0
        while (searchFrom <= normalizedText.length - normalizedQuery.length) {
            val matchIndex = normalizedText.indexOf(normalizedQuery, searchFrom)
            if (matchIndex < 0) break
            val endIndex = matchIndex + normalizedQuery.length - 1
            matches += MatchRange(
                start = sourceOffsets[matchIndex],
                endExclusive = sourceOffsets[endIndex] + 1,
            )
            searchFrom = matchIndex + normalizedQuery.length
        }
        return matches.distinct()
    }

    private fun Char.isCombiningMark(): Boolean = when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true

        else -> false
    }

    private const val WORD_SEPARATOR = ' '
    private const val PHONE_NUMBER_MATCH_DIGITS = 9

    private val SPECIAL_LATIN_FOLD = mapOf(
        'ð' to "d",
        'đ' to "d",
        'ħ' to "h",
        'ı' to "i",
        'ł' to "l",
        'ø' to "o",
        'þ' to "th",
        'ß' to "ss",
        'æ' to "ae",
        'œ' to "oe",
    )
}
