package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteSuggestionsTest {
    @Test
    fun `suggests at most five non-favorites ordered by call count`() {
        val contacts = (1..7).map { index -> contact("$index", "Person $index", "070000000$index") } +
            contact("favorite", "Favorite", "0711111111", isFavorite = true)
        val logs = contacts.flatMapIndexed { index, contact ->
            listOf(call(index.toLong(), contact, NOW - index, count = index + 1))
        }

        val result = suggestedFavoriteContacts(contacts, logs, NOW, displayOrder = 0)

        assertEquals(listOf("7", "6", "5", "4", "3"), result.map(Contact::id))
    }

    @Test
    fun `uses only the last thirty days and ignores blocked calls`() {
        val recent = contact("recent", "Recent", "0711111111")
        val old = contact("old", "Old", "0722222222")
        val blocked = contact("blocked", "Blocked", "0733333333")
        val logs = listOf(
            call(1, recent, NOW - FAVORITE_SUGGESTION_WINDOW_MILLIS, count = 2),
            call(2, old, NOW - FAVORITE_SUGGESTION_WINDOW_MILLIS - 1, count = 20),
            call(3, blocked, NOW - 1, count = 30, isBlocked = true),
        )

        val result = suggestedFavoriteContacts(listOf(recent, old, blocked), logs, NOW, 0)

        assertEquals(listOf("recent"), result.map(Contact::id))
    }

    @Test
    fun `matches a known contact by normalized number when call id is unavailable`() {
        val contact = contact("person", "Person", "+40 721 234 567")
        val log = call(1, contact, NOW - 1).copy(contactId = null, number = "0721-234-567")

        val result = suggestedFavoriteContacts(listOf(contact), listOf(log), NOW, 0)

        assertEquals(listOf("person"), result.map(Contact::id))
    }

    @Test
    fun `does not guess when the same number belongs to multiple contacts`() {
        val first = contact("first", "First", "0721234567")
        val second = contact("second", "Second", "+40 721 234 567")
        val log = call(1, first, NOW - 1).copy(contactId = null)

        val result = suggestedFavoriteContacts(listOf(first, second), listOf(log), NOW, 0)

        assertEquals(emptyList<Contact>(), result)
    }

    private fun contact(id: String, name: String, number: String, isFavorite: Boolean = false) =
        Contact(id = id, givenName = name, phoneNumbers = listOf(number), isFavorite = isFavorite)

    private fun call(
        id: Long,
        contact: Contact,
        date: Long,
        count: Int = 1,
        isBlocked: Boolean = false,
    ) = CallLogEntry(
        id = id,
        number = contact.phoneNumbers.first(),
        name = contact.displayName,
        type = 1,
        date = date,
        duration = 0,
        photoUri = null,
        contactId = contact.id,
        isBlocked = isBlocked,
        types = List(count) { 1 },
    )

    private companion object {
        const val NOW = 4_000_000_000_000L
    }
}
