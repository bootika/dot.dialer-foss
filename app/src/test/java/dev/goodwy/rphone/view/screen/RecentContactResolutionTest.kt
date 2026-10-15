package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentContactResolutionTest {
    @Test
    fun `stale contact id falls back to the normalized current phone match`() {
        val current = Contact(
            id = "current-id",
            givenName = "Maria",
            phoneNumbers = listOf("+40 721 234 567"),
            photoUri = "content://contacts/photo",
        )
        val call = CallLogEntry(
            id = 1,
            number = "0721-234-567",
            name = "Maria",
            type = 1,
            date = 1_000L,
            duration = 0L,
            photoUri = null,
            contactId = "deleted-id",
        )

        val resolved = resolveCurrentContact(
            call = call,
            contactsById = mapOf(current.id to current),
            contactsByPhone = buildContactsByPhone(listOf(current)),
        )

        assertEquals("current-id", resolved?.id)
        assertEquals("content://contacts/photo", resolved?.photoUri)
    }

    @Test
    fun `home search and recents resolve from the visible contact snapshot`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()

        assertTrue(source.contains("val contactsById = remember(contacts)"))
        assertTrue(source.contains("val contactsByPhone = remember(contacts)"))
        assertTrue(source.contains("DotDialerSearchIndex.build(contacts, callLogs, showAllCalls = showAllRecentCalls)"))
        assertFalse(source.contains("DotDialerSearchIndex.build(allContacts, callLogs)"))
    }

    @Test
    fun `public address book identity wins over a private duplicate for the same number`() {
        val privateCopy = Contact(
            id = "private-id",
            givenName = "Maria",
            phoneNumbers = listOf("0721 234 567"),
            photoUri = "content://private/old-photo",
            isPrivate = true,
        )
        val addressBookContact = Contact(
            id = "system-id",
            givenName = "Maria",
            phoneNumbers = listOf("+40 721 234 567"),
            photoUri = "content://contacts/current-photo",
        )

        val contactsByPhone = buildContactsByPhone(listOf(privateCopy, addressBookContact))

        assertEquals("system-id", contactsByPhone.getValue("721234567").id)
        assertEquals(
            "content://contacts/current-photo",
            canonicalContactPhotoUri(privateCopy, contactsByPhone),
        )
    }

    @Test
    fun `shared number does not borrow another contacts photo`() {
        val first = Contact(
            id = "first",
            givenName = "Maria",
            phoneNumbers = listOf("0721 234 567"),
        )
        val second = Contact(
            id = "second",
            givenName = "Service",
            phoneNumbers = listOf("+40 721 234 567"),
            photoUri = "content://contacts/service-photo",
        )

        val contactsByPhone = buildContactsByPhone(listOf(first, second))

        assertEquals(null, canonicalContactPhotoUri(first, contactsByPhone))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile)
            ?: error("Unable to find repository file: $relativePath")
    }
}
