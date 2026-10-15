package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMaintenanceTest {

    @Test
    fun standardizePreviewCountsChangesAndCapsExamples() {
        val contacts = listOf(
            Contact(id = "one", givenName = "Ana", phoneNumbers = listOf("0712 345 678", "123")),
            Contact(id = "two", givenName = "Matei", phoneNumbers = listOf("01 2", "03 4", "05 6")),
        )

        val result = buildStandardizePreview(contacts)

        assertEquals(2, result.totalContacts)
        assertEquals(2, result.contactsWithChanges)
        assertEquals(4, result.totalNumbersChanged)
        assertEquals(3, result.examples.size)
        assertEquals("0712345678", result.examples.first().newNumber)
    }

    @Test
    fun requestedFavoriteStateWinsWhileMissingRequestToggles() {
        assertTrue(resolveFavoriteState(current = false, requested = true))
        assertFalse(resolveFavoriteState(current = true, requested = false))
        assertTrue(resolveFavoriteState(current = false, requested = null))
        assertFalse(resolveFavoriteState(current = true, requested = null))
    }
}
