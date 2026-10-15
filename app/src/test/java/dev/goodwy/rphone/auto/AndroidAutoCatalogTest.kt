package dev.goodwy.rphone.auto

import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoCatalogTest {
    @Test
    fun `private and hidden contacts never reach the car catalog`() {
        val private = contact(id = "private", number = "0711111111", isPrivate = true)
        val hidden = contact(id = "hidden", number = "0722222222", isHidden = true)
        val public = contact(id = "public", number = "0733333333")

        val result = buildCatalog(
            contacts = listOf(private, hidden, public),
            callLogs = listOf(
                call(id = 1, number = private.phoneNumbers.single(), name = "Private Name"),
                call(id = 2, number = hidden.phoneNumbers.single(), name = "Hidden Name"),
                call(id = 3, number = public.phoneNumbers.single(), name = "Public Name"),
            ),
        )

        assertEquals(listOf("public"), result.contacts.map(AndroidAutoContact::id))
        assertEquals(listOf("0733333333"), result.recents.map(AndroidAutoRecent::number))
    }

    @Test
    fun `contact source visibility applies to contacts favorites and cached recent names`() {
        val visible = contact(
            id = "visible",
            number = "0711111111",
            accountName = "visible@example.com",
            accountType = "com.google",
            isFavorite = true,
        )
        val filtered = contact(
            id = "filtered",
            number = "0722222222",
            accountName = "hidden@example.com",
            accountType = "com.google",
            isFavorite = true,
        )

        val result = buildCatalog(
            contacts = listOf(visible, filtered),
            callLogs = listOf(
                call(id = 1, number = filtered.phoneNumbers.single(), name = "Filtered", contactId = filtered.id),
            ),
            visibleAccounts = setOf("com.google|visible@example.com"),
        )

        assertEquals(listOf("visible"), result.contacts.map(AndroidAutoContact::id))
        assertEquals(listOf("visible"), result.favorites.map(AndroidAutoContact::id))
        assertEquals("0722222222", result.recents.single().displayName)
    }

    @Test
    fun `favorite ordering and selected favorite number stay consistent with handset`() {
        val first = contact(id = "first", number = "0711111111", isFavorite = true)
        val second = contact(
            id = "second",
            number = "0722222222",
            extraNumber = "+40 733 333 333",
            isFavorite = true,
        )

        val result = buildCatalog(
            contacts = listOf(first, second),
            favoriteOrder = listOf("second", "first"),
            favoriteNumbers = mapOf("second" to "0733333333"),
        )

        assertEquals(listOf("second", "first"), result.favorites.map(AndroidAutoContact::id))
        assertEquals("+40 733 333 333", result.favorites.first().number)
    }

    @Test
    fun `unknown caller id labels remain available without exposing filtered contacts`() {
        val result = buildCatalog(
            callLogs = listOf(call(id = 1, number = "*222", name = "Carrier service")),
        )

        assertEquals("Carrier service", result.recents.single().displayName)
        assertTrue(result.contacts.isEmpty())
        assertFalse(result.recents.isEmpty())
    }

    private fun buildCatalog(
        contacts: List<Contact> = emptyList(),
        callLogs: List<CallLogEntry> = emptyList(),
        visibleAccounts: Set<String>? = null,
        favoriteOrder: List<String> = emptyList(),
        favoriteNumbers: Map<String, String?> = emptyMap(),
    ): AndroidAutoCatalog = buildAndroidAutoCatalog(
        contacts = contacts,
        callLogs = callLogs,
        visibleAccounts = visibleAccounts,
        sortOrder = 0,
        displayOrder = 0,
        favoriteOrder = favoriteOrder,
        favoriteNumbers = favoriteNumbers,
    )

    private fun contact(
        id: String,
        number: String,
        extraNumber: String? = null,
        accountName: String? = null,
        accountType: String? = null,
        isFavorite: Boolean = false,
        isPrivate: Boolean = false,
        isHidden: Boolean = false,
    ): Contact {
        val numbers = listOfNotNull(number, extraNumber)
        return Contact(
            id = id,
            givenName = id.replaceFirstChar(Char::uppercaseChar),
            phoneNumbers = numbers,
            phoneDetails = numbers.mapIndexed { index, value ->
                ContactPhoneDetail(number = value, isPrimary = index == 0)
            },
            accountName = accountName,
            accountType = accountType,
            isFavorite = isFavorite,
            isPrivate = isPrivate,
            isHidden = isHidden,
        )
    }

    private fun call(
        id: Long,
        number: String,
        name: String?,
        contactId: String? = null,
    ): CallLogEntry = CallLogEntry(
        id = id,
        number = number,
        name = name,
        type = 1,
        date = id,
        duration = 0,
        photoUri = null,
        contactId = contactId,
    )
}
