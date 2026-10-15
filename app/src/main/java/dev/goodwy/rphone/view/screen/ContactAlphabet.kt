package dev.goodwy.rphone.view.screen

import io.github.bootika.dotdialer.core.search.SearchTextNormalizer

internal val CONTACT_ALPHABET: List<String> = ('A'..'Z').map(Char::toString) + "#"

internal fun contactSectionLetter(name: String): String {
    val normalized = SearchTextNormalizer.normalize(name.trim(), foldDiacritics = true)
    return normalized.firstOrNull()?.takeIf(Char::isLetter)?.uppercase() ?: "#"
}

internal fun contactAlphabetTargets(displayNames: List<String>): Map<String, Int> =
    displayNames.mapIndexed { index, name -> contactSectionLetter(name) to index }
        .distinctBy { it.first }
        .toMap()

/**
 * Resolves every position on the fixed A-Z/# rail to a real list target.
 * Empty letters move to the next populated section (or the previous one when
 * there is no later section), so a continuous finger scrub never becomes inert.
 */
internal fun resolvedContactAlphabetTargets(
    populatedTargets: Map<String, Int>,
): Map<String, Int?> {
    val populatedLetters = CONTACT_ALPHABET.filter(populatedTargets::containsKey)
    return CONTACT_ALPHABET.associateWith { requested ->
        populatedTargets[requested]
            ?: if (requested == "#") {
                populatedLetters.lastOrNull { it != "#" }?.let(populatedTargets::get)
            } else {
                null
            }
            ?: populatedLetters.firstOrNull { it != "#" && it > requested }
                ?.let(populatedTargets::get)
            ?: populatedLetters.lastOrNull { it != "#" && it < requested }
                ?.let(populatedTargets::get)
            ?: populatedTargets["#"]
    }
}
