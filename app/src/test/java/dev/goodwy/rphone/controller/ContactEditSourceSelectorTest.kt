package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.repository.ContactsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactEditSourceSelectorTest {
    @Test
    fun readOnlyLinkedAppIsSkippedForWritablePhoneContact() {
        val result = selectPreferredEditableRawContact(
            sources = listOf(
                source("whatsapp", "WhatsApp", "com.whatsapp", isReadOnly = true),
                source("phone", null, null),
            ),
            preferredAccountName = "WhatsApp",
            preferredAccountType = "com.whatsapp",
        )

        assertEquals("phone", result?.rawContactId)
    }

    @Test
    fun matchingWritableAccountWinsOverOtherWritableSources() {
        val result = selectPreferredEditableRawContact(
            sources = listOf(
                source("phone", null, null),
                source("google", "person@example.test", "com.google"),
            ),
            preferredAccountName = "person@example.test",
            preferredAccountType = "com.google",
        )

        assertEquals("google", result?.rawContactId)
    }

    @Test
    fun localPhoneContactWinsWhenPreferredAccountIsUnavailable() {
        val result = selectPreferredEditableRawContact(
            sources = listOf(
                source("other", "person@example.test", "com.google"),
                source("phone", null, null),
            ),
            preferredAccountName = "missing@example.test",
            preferredAccountType = "com.google",
        )

        assertEquals("phone", result?.rawContactId)
    }

    @Test
    fun noReadOnlySourceIsReturned() {
        val result = selectPreferredEditableRawContact(
            sources = listOf(source("whatsapp", "WhatsApp", "com.whatsapp", isReadOnly = true)),
            preferredAccountName = null,
            preferredAccountType = null,
        )

        assertNull(result)
    }

    private fun source(
        id: String,
        accountName: String?,
        accountType: String?,
        isReadOnly: Boolean = false,
    ) = ContactsRepository.RawContactInfo(
        rawContactId = id,
        accountName = accountName,
        accountType = accountType,
        contactId = "aggregate",
        isReadOnly = isReadOnly,
    )
}
