package io.github.bootika.dotdialer.core.search

import kotlin.math.ln

enum class SearchDocumentKind {
    CONTACT,
    RECENT_CALL,
    UNSAVED_CALL,
    NOTE,
}

data class SearchDocument<T>(
    val key: String,
    val kind: SearchDocumentKind,
    val payload: T,
    val primaryText: String,
    val secondaryTexts: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val interactionCount: Int = 0,
    val lastInteractionMillis: Long = 0L,
)

data class SearchOptions(
    val foldDiacritics: Boolean = true,
    val includeT9: Boolean = false,
    val enabledKinds: Set<SearchDocumentKind> = setOf(
        SearchDocumentKind.CONTACT,
        SearchDocumentKind.UNSAVED_CALL,
        SearchDocumentKind.NOTE,
    ),
    val limit: Int = 100,
    val nowMillis: Long = System.currentTimeMillis(),
)

data class SearchHit<T>(
    val document: SearchDocument<T>,
    val score: Double,
)

/**
 * Immutable local index shared by regular search and T9 search.
 *
 * Both literal and accent-folded forms are precomputed so changing the preference does not
 * require rereading contacts from Android's provider or rebuilding the index.
 */
class LocalSearchIndex<T> private constructor(
    private val entries: List<IndexedDocument<T>>,
) {
    /**
     * Returns the immutable documents already held by this index without rerunning matching.
     *
     * This is used by browse-style surfaces (for example the unified dialer home screen) so
     * their empty-query state and their filtered state share the exact same source of truth.
     */
    fun documents(
        enabledKinds: Set<SearchDocumentKind> = SearchDocumentKind.entries.toSet(),
    ): List<SearchDocument<T>> = entries.asSequence()
        .map(IndexedDocument<T>::document)
        .filter { it.kind in enabledKinds }
        .toList()

    fun search(query: String, options: SearchOptions = SearchOptions()): List<SearchHit<T>> {
        if (query.isBlank() || options.limit <= 0) return emptyList()

        val normalizedQuery = SearchTextNormalizer.normalize(query.trim(), options.foldDiacritics)
        val phoneQuery = SearchTextNormalizer.normalizePhone(query)
        val isNumericQuery = phoneQuery.isNotEmpty() && query.all {
            it.isDigit() || it.isWhitespace() || it in PHONE_QUERY_PUNCTUATION
        }

        return entries.asSequence()
            .filter { it.document.kind in options.enabledKinds }
            .mapNotNull { entry ->
                val matchScore = entry.matchScore(
                    normalizedQuery = normalizedQuery,
                    phoneQuery = phoneQuery,
                    isNumericQuery = isNumericQuery,
                    foldDiacritics = options.foldDiacritics,
                    includeT9 = options.includeT9,
                ) ?: return@mapNotNull null

                SearchHit(
                    document = entry.document,
                    score = matchScore + usageScore(entry.document, options.nowMillis),
                )
            }
            .sortedWith(
                compareByDescending<SearchHit<T>> { it.score }
                    .thenByDescending { it.document.lastInteractionMillis }
                    .thenBy { SearchTextNormalizer.normalize(it.document.primaryText, true) }
                    .thenBy { it.document.key }
            )
            .take(options.limit)
            .toList()
    }

    companion object {
        fun <T> build(documents: List<SearchDocument<T>>): LocalSearchIndex<T> =
            LocalSearchIndex(documents.map(::IndexedDocument))

        private val PHONE_QUERY_PUNCTUATION = setOf('+', '-', '(', ')', '.', '/')

        private fun usageScore(document: SearchDocument<*>, nowMillis: Long): Double {
            val frequency = (ln(1.0 + document.interactionCount.coerceAtLeast(0)) * 42.0)
                .coerceAtMost(MAX_FREQUENCY_SCORE)
            if (document.lastInteractionMillis <= 0L) return frequency

            val ageMillis = (nowMillis - document.lastInteractionMillis).coerceAtLeast(0L)
            val ageDays = ageMillis.toDouble() / MILLIS_PER_DAY
            val recency = 160.0 / (1.0 + ageDays / 30.0)
            return frequency + recency
        }

        private const val MILLIS_PER_DAY = 86_400_000.0
        private const val MAX_FREQUENCY_SCORE = 120.0
    }
}

private class IndexedDocument<T>(
    val document: SearchDocument<T>,
) {
    private val primaryLiteral = SearchTextNormalizer.normalize(document.primaryText, false)
    private val primaryFolded = SearchTextNormalizer.normalize(document.primaryText, true)
    private val secondaryLiteral = document.secondaryTexts.map { SearchTextNormalizer.normalize(it, false) }
    private val secondaryFolded = document.secondaryTexts.map { SearchTextNormalizer.normalize(it, true) }
    private val phones = document.phoneNumbers.map(SearchTextNormalizer::normalizePhone).filter(String::isNotEmpty)
    private val t9Literal = SearchTextNormalizer.toT9(document.primaryText, false)
    private val t9Folded = SearchTextNormalizer.toT9(document.primaryText, true)
    private val secondaryT9Literal = document.secondaryTexts.map { SearchTextNormalizer.toT9(it, false) }
    private val secondaryT9Folded = document.secondaryTexts.map { SearchTextNormalizer.toT9(it, true) }

    fun matchScore(
        normalizedQuery: String,
        phoneQuery: String,
        isNumericQuery: Boolean,
        foldDiacritics: Boolean,
        includeT9: Boolean,
    ): Double? {
        val primary = if (foldDiacritics) primaryFolded else primaryLiteral
        val secondary = if (foldDiacritics) secondaryFolded else secondaryLiteral

        val textScore = if (isNumericQuery) null else bestTextScore(primary, secondary, normalizedQuery)
        val phoneScore = if (phoneQuery.isEmpty()) null else bestPhoneScore(phoneQuery)
        val t9Score = if (includeT9 && isNumericQuery) {
            val primaryT9 = if (foldDiacritics) t9Folded else t9Literal
            val secondaryT9 = if (foldDiacritics) secondaryT9Folded else secondaryT9Literal
            bestT9Score(primaryT9, secondaryT9, phoneQuery)
        } else {
            null
        }

        return listOfNotNull(textScore, phoneScore, t9Score).maxOrNull()
    }

    private fun bestTextScore(primary: String, secondary: List<String>, query: String): Double? {
        if (query.isEmpty()) return null
        if (primary == query) return 1_200.0
        if (primary.startsWith(query)) return 1_050.0
        if (primary.hasWordStartingWith(query)) return 980.0
        if (primary.contains(query)) return 900.0

        secondary.forEach { field ->
            if (field == query) return 860.0
            if (field.startsWith(query) || field.hasWordStartingWith(query)) return 800.0
            if (field.contains(query)) return 720.0
        }
        return null
    }

    private fun bestPhoneScore(query: String): Double? {
        phones.forEach { phone ->
            if (phone == query) return 1_150.0
            if (phone.startsWith(query)) return 1_000.0
            if (phone.contains(query)) return 880.0
        }
        return null
    }

    private fun bestT9Score(primary: String, secondary: List<String>, query: String): Double? {
        if (primary.t9WordStartsWith(query)) return 940.0
        if (secondary.any { it.t9WordStartsWith(query) }) return 760.0
        return null
    }
}

private fun String.hasWordStartingWith(query: String): Boolean =
    split(Regex("[^\\p{L}\\p{N}]+"))
        .any { it.startsWith(query) }

private fun String.t9WordStartsWith(query: String): Boolean =
    split(Regex("\\s+"))
        .any { it.startsWith(query) }
