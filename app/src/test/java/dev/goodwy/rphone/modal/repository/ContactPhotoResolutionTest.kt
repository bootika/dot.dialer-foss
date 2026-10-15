package dev.goodwy.rphone.modal.repository

import dev.goodwy.rphone.modal.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPhotoResolutionTest {
    @Test
    fun `adds a photo discovered after the contact was created`() {
        val contact = Contact(id = "4007")

        val updated = contact.withPhotoIfMissing("content://contacts/4007/photo")

        assertEquals("content://contacts/4007/photo", updated.photoUri)
    }

    @Test
    fun `does not replace an existing photo`() {
        val contact = Contact(id = "4007", photoUri = "content://contacts/4007/original")

        val updated = contact.withPhotoIfMissing("content://contacts/4007/later")

        assertEquals("content://contacts/4007/original", updated.photoUri)
    }

    @Test
    fun `ignores a blank photo candidate`() {
        val contact = Contact(id = "4007")

        val updated = contact.withPhotoIfMissing(" ")

        assertEquals(null, updated.photoUri)
    }
}
