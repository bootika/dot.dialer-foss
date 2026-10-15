package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactFilteringTest {
    private val local = Contact(id = "local", givenName = "Mara")
    private val privateContact = Contact(id = "private", givenName = "Pia", isPrivate = true)
    private val cloud = Contact(
        id = "cloud",
        givenName = "Alexandru",
        familyName = "Butica",
        accountName = "people@example.test",
        accountType = "example.contacts",
    )

    @Test
    fun visibleAccountsKeepOnlySelectedSources() {
        val result = filterAndSortContacts(
            inputs = inputs(visibleAccounts = setOf("local|local", "private|private")),
            sortOrder = 0,
        )

        assertEquals(listOf("local"), result.map(Contact::id))
    }

    @Test
    fun selectedAccountTakesPriorityOverVisibleAccountFilter() {
        val result = filterAndSortContacts(
            inputs = inputs(
                accountName = "people@example.test",
                accountType = "example.contacts",
                visibleAccounts = emptySet(),
            ),
            sortOrder = 0,
        )

        assertEquals(listOf("cloud"), result.map(Contact::id))
    }

    @Test
    fun familyNameOrderFallsBackToDisplayName() {
        val result = filterAndSortContacts(inputs(), sortOrder = 1)

        assertEquals(listOf("cloud", "local"), result.map(Contact::id))
    }

    @Test
    fun groupedContactsAreAlphabeticalWithNonLettersLast() {
        val numeric = Contact(id = "numeric", givenName = "7 Service")

        val result = groupContactsByInitial(
            contacts = listOf(privateContact, numeric, local, cloud),
            sortOrder = 0,
        )

        assertEquals(listOf('A', 'M', 'P', '#'), result.keys.toList())
        assertEquals(listOf("numeric"), result.getValue('#').map(Contact::id))
    }

    private fun inputs(
        accountName: String? = null,
        accountType: String? = null,
        visibleAccounts: Set<String>? = null,
    ) = ContactFilterInputs(
        contacts = listOf(local, privateContact, cloud),
        accountName = accountName,
        accountType = accountType,
        privateOnly = false,
        localOnly = false,
        visibleAccounts = visibleAccounts,
    )
}
