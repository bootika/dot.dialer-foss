package io.github.bootika.dotdialer.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchEngineTest {
    private val now = 2_000_000_000_000L

    @Test
    fun `finds a contact without typing its diacritics`() {
        val index = LocalSearchIndex.build(
            listOf(contact("1", "Ștefan Țurcanu")),
        )

        assertEquals(
            listOf("1"),
            index.search("stefan tur", SearchOptions(foldDiacritics = true, nowMillis = now))
                .map { it.document.key },
        )
        assertTrue(
            index.search("stefan", SearchOptions(foldDiacritics = false, nowMillis = now)).isEmpty(),
        )
    }

    @Test
    fun `uses the same folded index for T9`() {
        val index = LocalSearchIndex.build(
            listOf(contact("1", "Ștefan"), contact("2", "Alexandru")),
        )

        val results = index.search(
            "783",
            SearchOptions(foldDiacritics = true, includeT9 = true, nowMillis = now),
        )

        assertEquals(listOf("1"), results.map { it.document.key })
    }

    @Test
    fun `ranks frequency and recency for equally good matches`() {
        val recent = contact(
            key = "recent",
            name = "Alex Recent",
            count = 3,
            lastInteraction = now - 86_400_000L,
        )
        val frequent = contact(
            key = "frequent",
            name = "Alex Frequent",
            count = 20,
            lastInteraction = now - 120L * 86_400_000L,
        )
        val unused = contact(key = "unused", name = "Alex Unused")
        val index = LocalSearchIndex.build(listOf(unused, frequent, recent))

        val results = index.search("alex", SearchOptions(nowMillis = now))

        assertEquals("recent", results[0].document.key)
        assertEquals("frequent", results[1].document.key)
        assertEquals("unused", results[2].document.key)
    }

    @Test
    fun `keeps exact relevance ahead of usage boost`() {
        val exact = contact(key = "exact", name = "Ana")
        val frequentSubstring = contact(
            key = "frequent",
            name = "Ioana Popescu",
            count = 10_000,
            lastInteraction = now,
        )
        val index = LocalSearchIndex.build(listOf(frequentSubstring, exact))

        assertEquals(
            "exact",
            index.search("ana", SearchOptions(nowMillis = now)).first().document.key,
        )
    }

    @Test
    fun `returns contacts calls and notes in one ranked result list`() {
        val documents = listOf(
            contact("contact", "Maria Ionescu", count = 6, lastInteraction = now),
            SearchDocument(
                key = "call",
                kind = SearchDocumentKind.UNSAVED_CALL,
                payload = "call",
                primaryText = "Maria delivery",
                phoneNumbers = listOf("0712 000 000"),
            ),
            SearchDocument(
                key = "note",
                kind = SearchDocumentKind.NOTE,
                payload = "note",
                primaryText = "Maria meeting",
                secondaryTexts = listOf("Discuss contract"),
            ),
        )
        val index = LocalSearchIndex.build(documents)

        val results = index.search("maria", SearchOptions(nowMillis = now))

        assertEquals(3, results.size)
        assertEquals(SearchDocumentKind.CONTACT, results.first().document.kind)
        assertEquals(
            setOf(SearchDocumentKind.CONTACT, SearchDocumentKind.UNSAVED_CALL, SearchDocumentKind.NOTE),
            results.map { it.document.kind }.toSet(),
        )
    }

    @Test
    fun `respects enabled result kinds and result limit`() {
        val index = LocalSearchIndex.build(
            listOf(
                contact("1", "Ana One"),
                contact("2", "Ana Two"),
                SearchDocument("call", SearchDocumentKind.UNSAVED_CALL, "call", "Ana Caller"),
            ),
        )

        val results = index.search(
            "ana",
            SearchOptions(
                enabledKinds = setOf(SearchDocumentKind.CONTACT),
                limit = 1,
                nowMillis = now,
            ),
        )

        assertEquals(1, results.size)
        assertEquals(SearchDocumentKind.CONTACT, results.single().document.kind)
    }

    private fun contact(
        key: String,
        name: String,
        count: Int = 0,
        lastInteraction: Long = 0L,
    ) = SearchDocument(
        key = key,
        kind = SearchDocumentKind.CONTACT,
        payload = key,
        primaryText = name,
        interactionCount = count,
        lastInteractionMillis = lastInteraction,
    )
}
