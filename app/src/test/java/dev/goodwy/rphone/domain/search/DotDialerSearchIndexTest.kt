package dev.goodwy.rphone.domain.search

import dev.goodwy.rphone.controller.util.NoteEntry
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import io.github.bootika.dotdialer.core.search.SearchDocumentKind
import io.github.bootika.dotdialer.core.search.SearchOptions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotDialerSearchIndexTest {
    private val now = 2_000_000_000_000L

    @Test fun `same name and formatted number on distinct provider contacts remain separate`() {
        val contacts = listOf(
            Contact(id = "101", givenName = "Test Alexandru Butica", phoneNumbers = listOf("+40722123456")),
            Contact(id = "202", givenName = "Test Alexandru Butica", phoneNumbers = listOf("+40 722 123 456")),
        )
        for (repeat in listOf(false, true)) {
            val result = DotDialerHomeDirectory.sections(
                DotDialerSearchIndex.build(contacts, emptyList()), "alexandru", true,
                repeatContactsInSearchResults = repeat,
            )
            assertEquals(listOf("contact:101", "contact:202"), result.contacts.map { it.key })
            assertEquals(contacts.map { it.phoneNumbers.single() }, result.contacts.map {
                (it.payload as DotDialerSearchPayload.ContactResult).contact.phoneNumbers.single()
            })
        }
    }

    @Test fun `one provider contact with two number formats yields one search row`() {
        val contact = Contact(id = "101", givenName = "Test Alexandru Butica",
            phoneNumbers = listOf("+40722123456", "+40 722 123 456"))
        val result = DotDialerHomeDirectory.sections(
            DotDialerSearchIndex.build(listOf(contact), emptyList()), "alexandru", true,
        )
        assertEquals(listOf("contact:101"), result.contacts.map { it.key })
        assertEquals(contact, (result.contacts.single().payload as DotDialerSearchPayload.ContactResult).contact)
    }

    @Test fun `all calls keeps chronological events and single unsaved identity while default merges`() {
        val logs=listOf(
            call(1,"0700000001",null,now).copy(type=2, types=listOf(2),ids=listOf(1L)),
            call(2,"0700000002",null,now-1000).copy(ids=listOf(2L)),
            call(3,"+40 700 000 001",null,now-2000).copy(type=3,types=listOf(3),ids=listOf(3L)),
        )
        val individual=DotDialerSearchIndex.build(emptyList(),logs,showAllCalls=true)
        val sections=DotDialerHomeDirectory.sections(individual,"",true)
        assertEquals(listOf(1L,2L,3L),sections.recents.map { (it.payload as DotDialerSearchPayload.CallResult).call.id })
        assertEquals(3,sections.recents.map { it.key }.distinct().size)
        assertEquals(1,sections.recents.count { (it.payload as DotDialerSearchPayload.CallResult).call.type==3 })
        assertEquals(2,individual.documents(setOf(SearchDocumentKind.UNSAVED_CALL)).size)
        val searched=DotDialerHomeDirectory.sections(individual,"0700000001",true)
        assertEquals(2,searched.recents.size)
        val merged=DotDialerHomeDirectory.sections(DotDialerSearchIndex.build(emptyList(),logs),"",true)
        assertEquals(2,merged.recents.size)
        assertEquals(listOf(1L,3L),(merged.recents.first().payload as DotDialerSearchPayload.CallResult).call.ids)
    }

    @Test
    fun `attaches call usage to a saved contact and ranks it`() {
        val used = Contact(id = "1", givenName = "Alex Used", phoneNumbers = listOf("+40 712 111 111"))
        val unused = Contact(id = "2", givenName = "Alex Unused", phoneNumbers = listOf("+40 712 222 222"))
        val logs = listOf(
            call(id = 1, number = "+40 712 111 111", contactId = "1", date = now),
            call(id = 2, number = "+40 712 111 111", contactId = "1", date = now - 1_000),
        )

        val results = DotDialerSearchIndex.build(listOf(unused, used), logs)
            .search("alex", SearchOptions(nowMillis = now))

        assertEquals("1", (results.first().document.payload as DotDialerSearchPayload.ContactResult).contact.id)
    }

    @Test
    fun `resolves a call to a contact by normalized phone when provider contact id is missing`() {
        val contact = Contact(id = "1", givenName = "Maria", phoneNumbers = listOf("+40 721 234 567"))
        val call = call(id = 1, number = "0721-234-567", contactId = null, date = now)

        val results = DotDialerSearchIndex.build(listOf(contact), listOf(call))
            .search("0721", SearchOptions(nowMillis = now))

        assertEquals(1, results.size)
        assertEquals(SearchDocumentKind.CONTACT, results.single().document.kind)
    }

    @Test
    fun `replaces a stale call contact id and avatar from the current normalized phone match`() {
        val contact = Contact(
            id = "current-id",
            givenName = "Maria",
            phoneNumbers = listOf("+40 721 234 567"),
            photoUri = "content://contacts/current/photo",
        )
        val staleCall = call(
            id = 1,
            number = "0721-234-567",
            contactId = "deleted-id",
            date = now,
        )

        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(listOf(contact), listOf(staleCall)),
            query = "maria",
            foldDiacritics = true,
            repeatContactsInSearchResults = true,
        )
        val resolved = (sections.recents.single().payload as DotDialerSearchPayload.CallResult).call

        assertEquals("current-id", resolved.contactId)
        assertEquals("content://contacts/current/photo", resolved.photoUri)
    }

    @Test
    fun `prefers the matching contact that has a photo when sources repeat a number`() {
        val withoutPhoto = Contact(
            id = "without-photo",
            givenName = "Maria",
            phoneNumbers = listOf("0721 234 567"),
        )
        val withPhoto = Contact(
            id = "with-photo",
            givenName = "Maria",
            phoneNumbers = listOf("+40 721 234 567"),
            photoUri = "content://contacts/photo",
        )

        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(
                listOf(withoutPhoto, withPhoto),
                listOf(call(id = 1, number = "0721234567", contactId = null, date = now)),
            ),
            query = "maria",
            foldDiacritics = true,
            repeatContactsInSearchResults = true,
        )
        val resolved = (sections.recents.single().payload as DotDialerSearchPayload.CallResult).call

        assertEquals("with-photo", resolved.contactId)
        assertEquals("content://contacts/photo", resolved.photoUri)
    }

    @Test
    fun `current contact without a photo clears a stale call log avatar`() {
        val contact = Contact(
            id = "current-id",
            givenName = "Amalia",
            phoneNumbers = listOf("0727 648 757"),
            photoUri = null,
        )
        val staleCall = call(
            id = 1,
            number = "0727648757",
            contactId = "current-id",
            date = now,
        ).copy(photoUri = "content://call-log/stale-photo")

        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(listOf(contact), listOf(staleCall)),
            query = "amalia",
            foldDiacritics = true,
            repeatContactsInSearchResults = true,
        )
        val resolved = (sections.recents.single().payload as DotDialerSearchPayload.CallResult).call

        assertEquals("current-id", resolved.contactId)
        assertEquals(null, resolved.photoUri)
    }

    @Test
    fun `deduplicates unsaved calls and keeps the newest payload`() {
        val older = call(id = 1, number = "0712 000 000", contactId = null, date = now - 10_000)
        val newest = call(id = 2, number = "0712-000-000", contactId = null, date = now)

        val results = DotDialerSearchIndex.build(emptyList(), listOf(older, newest))
            .search("0712", SearchOptions(nowMillis = now))

        assertEquals(1, results.size)
        assertEquals(2L, (results.single().document.payload as DotDialerSearchPayload.CallResult).call.id)
    }

    @Test
    fun `indexes notes in the same result set`() {
        val note = NoteEntry(
            file = File("meeting-note.txt"),
            contactName = "Ștefan",
            phoneNumber = "0712000000",
            content = "Discuție contract",
            lastModified = now,
        )

        val results = DotDialerSearchIndex.build(emptyList(), emptyList(), listOf(note))
            .search("discutie", SearchOptions(foldDiacritics = true, nowMillis = now))

        assertTrue(results.single().document.payload is DotDialerSearchPayload.NoteResult)
    }

    @Test
    fun `home recents aggregate a number and stay ordered by latest call`() {
        val olderFrequent = (1L..5L).map { id ->
            call(
                id = id,
                number = "0712 000 001",
                contactId = null,
                date = now - (id * 1_000),
            )
        }
        val newest = call(
            id = 10,
            number = "0712 000 002",
            contactId = null,
            date = now,
        )

        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(emptyList(), olderFrequent + newest),
            query = "",
            foldDiacritics = true,
        )

        assertEquals(2, sections.recents.size)
        assertEquals(
            "0712 000 002",
            (sections.recents.first().payload as DotDialerSearchPayload.CallResult).call.number,
        )
        assertEquals(5, sections.recents.last().interactionCount)
    }

    @Test
    fun `home search keeps recents before an alphabetical contact directory`() {
        val stefan = Contact(id = "2", givenName = "Ștefan", phoneNumbers = listOf("0722"))
        val sorin = Contact(id = "1", givenName = "Sorin", phoneNumbers = listOf("0711"))
        val index = DotDialerSearchIndex.build(
            contacts = listOf(stefan, sorin),
            callLogs = listOf(call(id = 1, number = "0722", contactId = "2", date = now)),
        )

        val sections = DotDialerHomeDirectory.sections(
            index = index,
            query = "stef",
            foldDiacritics = true,
            repeatContactsInSearchResults = true,
        )

        assertEquals(1, sections.recents.size)
        assertEquals(
            "2",
            (sections.contacts.single().payload as DotDialerSearchPayload.ContactResult).contact.id,
        )

        val allContacts = DotDialerHomeDirectory.sections(index, "", true).contacts
        assertEquals(
            listOf("Sorin", "Ștefan"),
            allContacts.map { it.primaryText },
        )
    }

    @Test
    fun `home search returns every matching contact when repeated results are enabled`() {
        val contacts = listOf(
            Contact(id = "1", givenName = "Mihaela Andrei", phoneNumbers = listOf("0711")),
            Contact(id = "2", givenName = "Mihaela Barbu", phoneNumbers = listOf("0722")),
            Contact(id = "3", givenName = "Mihai Ciobanu", phoneNumbers = listOf("0733")),
            Contact(id = "4", givenName = "Mihaela Dinu", phoneNumbers = listOf("0744")),
            Contact(id = "5", givenName = "Mihnea Ene", phoneNumbers = listOf("0755")),
        )
        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(
                contacts = contacts,
                callLogs = listOf(call(id = 1, number = "0711", contactId = null, date = now)),
            ),
            query = "mih",
            foldDiacritics = true,
            repeatContactsInSearchResults = true,
            visibleContactIds = contacts.mapTo(mutableSetOf(), Contact::id),
        )

        assertEquals(1, sections.recents.size)
        assertEquals(
            listOf("Mihaela Andrei", "Mihaela Barbu", "Mihaela Dinu", "Mihai Ciobanu", "Mihnea Ene"),
            sections.contacts.map { it.primaryText },
        )
    }

    @Test
    fun `home search hides contacts already represented in recents by default`() {
        val contacts = listOf(
            Contact(id = "1", givenName = "Mihaela Andrei", phoneNumbers = listOf("0711")),
            Contact(id = "2", givenName = "Mihaela Barbu", phoneNumbers = listOf("0722")),
            Contact(id = "3", givenName = "Mihai Ciobanu", phoneNumbers = listOf("0733")),
        )
        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(
                contacts = contacts,
                callLogs = listOf(call(id = 1, number = "0711", contactId = null, date = now)),
            ),
            query = "mih",
            foldDiacritics = true,
            visibleContactIds = contacts.mapTo(mutableSetOf(), Contact::id),
        )

        assertEquals(1, sections.recents.size)
        assertEquals(
            listOf("Mihaela Barbu", "Mihai Ciobanu"),
            sections.contacts.map { it.primaryText },
        )
    }

    @Test
    fun `home directory keeps all contacts when no search is active`() {
        val contact = Contact(id = "1", givenName = "Mihaela Andrei", phoneNumbers = listOf("0711"))
        val sections = DotDialerHomeDirectory.sections(
            index = DotDialerSearchIndex.build(
                contacts = listOf(contact),
                callLogs = listOf(call(id = 1, number = "0711", contactId = "1", date = now)),
            ),
            query = "",
            foldDiacritics = true,
        )

        assertEquals(1, sections.recents.size)
        assertEquals(listOf("Mihaela Andrei"), sections.contacts.map { it.primaryText })
    }

    @Test
    fun `home directory respects visible contacts and configured alphabetical order`() {
        val ana = Contact(id = "ana", givenName = "Ana")
        val bogdan = Contact(id = "bogdan", givenName = "Bogdan")
        val hidden = Contact(id = "hidden", givenName = "Hidden")
        val index = DotDialerSearchIndex.build(listOf(ana, bogdan, hidden), emptyList())

        val sections = DotDialerHomeDirectory.sections(
            index = index,
            query = "",
            foldDiacritics = true,
            contactOrder = mapOf("bogdan" to 0, "ana" to 1),
            visibleContactIds = setOf("ana", "bogdan"),
        )

        assertEquals(listOf("Bogdan", "Ana"), sections.contacts.map { it.primaryText })
    }

    @Test
    fun `dialpad preloads the complete address book alphabetically`() {
        val index = DotDialerSearchIndex.build(
            contacts = listOf(
                Contact(id = "3", givenName = "Ștefan", phoneNumbers = listOf("0733")),
                Contact(id = "1", givenName = "Ana", phoneNumbers = listOf("0711")),
                Contact(id = "2", givenName = "Bogdan", phoneNumbers = listOf("0722")),
            ),
            callLogs = emptyList(),
        )

        val contacts = DotDialerDialpadDirectory.contacts(index, "", foldDiacritics = true)

        assertEquals(listOf("Ana", "Bogdan", "Ștefan"), contacts.map { it.primaryText })
    }

    @Test
    fun `dialpad T9 filters by name but preserves alphabetical presentation`() {
        val index = DotDialerSearchIndex.build(
            contacts = listOf(
                Contact(id = "3", givenName = "Mihaela Zaha", phoneNumbers = listOf("0733")),
                Contact(id = "1", givenName = "Mihai Andrei", phoneNumbers = listOf("0711")),
                Contact(id = "2", givenName = "Mihaela Barbu", phoneNumbers = listOf("0722")),
                Contact(id = "4", givenName = "Radu", phoneNumbers = listOf("0744")),
            ),
            callLogs = emptyList(),
        )

        val contacts = DotDialerDialpadDirectory.contacts(index, "644", foldDiacritics = true)

        assertEquals(
            listOf("Mihaela Barbu", "Mihaela Zaha", "Mihai Andrei"),
            contacts.map { it.primaryText },
        )
    }

    private fun call(
        id: Long,
        number: String,
        contactId: String?,
        date: Long,
    ) = CallLogEntry(
        id = id,
        number = number,
        name = null,
        type = 1,
        date = date,
        duration = 0,
        photoUri = null,
        contactId = contactId,
    )
}
