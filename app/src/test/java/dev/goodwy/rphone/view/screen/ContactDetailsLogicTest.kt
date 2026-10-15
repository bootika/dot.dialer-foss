package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDetailsLogicTest {

    @Test
    fun `company and job label handles every available combination`() {
        assertEquals("", null.companyAndJobLabel())
        assertEquals("Engineer • Example", Contact(id = "1", company = "Example", jobTitle = "Engineer").companyAndJobLabel())
        assertEquals("Example", Contact(id = "1", company = "Example").companyAndJobLabel())
        assertEquals("Engineer", Contact(id = "1", jobTitle = "Engineer").companyAndJobLabel())
    }

    @Test
    fun `setting a primary phone clears the previous primary`() {
        val contact = Contact(
            id = "1",
            phoneDetails = listOf(
                ContactPhoneDetail(number = "111", isPrimary = true),
                ContactPhoneDetail(number = "222"),
            ),
        )

        val updated = contact.withPrimaryPhone("222", isPrimary = true)

        assertFalse(updated.phoneDetails.first().isPrimary)
        assertTrue(updated.phoneDetails.last().isPrimary)
    }

    @Test
    fun `phone rows merge formatting variants and collect their sources`() {
        val details = listOf(
            ContactPhoneDetail(number = "+40 700 000 001", isPrimary = true),
            ContactPhoneDetail(number = "+40700000001"),
            ContactPhoneDetail(number = "0700 000 002"),
        )
        val sources = listOf(
            ContactPhoneSourceData(
                accountName = "person@example.com",
                accountType = "com.google",
                phoneDetails = listOf(ContactPhoneDetail(number = "+40700000001")),
            ),
            ContactPhoneSourceData(
                accountName = "WhatsApp",
                accountType = "com.whatsapp",
                phoneDetails = listOf(ContactPhoneDetail(number = "+40 700 000 001")),
            ),
        )

        val rows = buildContactPhoneRows(details, sources)

        assertEquals(2, rows.size)
        assertEquals("+40 700 000 001", rows.first().detail.number)
        assertTrue(rows.first().detail.isPrimary)
        assertEquals(listOf("com.google", "com.whatsapp"), rows.first().sources.map { it.accountType })
    }

    @Test
    fun `contact logs match by id contact number or requested number`() {
        val logs = listOf(
            log(id = 1, number = "000", contactId = "contact"),
            log(id = 2, number = "+40 123", contactId = null),
            log(id = 3, number = "555 000", contactId = null),
            log(id = 4, number = "999", contactId = null),
        )
        val contact = Contact(id = "contact", phoneNumbers = listOf("+40123"))

        val matches = logs.matchingContactDetailsLogs(contact, phoneNumber = "555000")

        assertEquals(listOf(1L, 2L, 3L), matches.map { it.id })
    }

    private fun log(id: Long, number: String, contactId: String?) = CallLogEntry(
        id = id,
        number = number,
        name = null,
        type = 0,
        date = 0,
        duration = 0,
        photoUri = null,
        contactId = contactId,
    )
}
