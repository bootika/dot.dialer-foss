package dev.goodwy.rphone.domain.usecase

import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCallerNameUseCaseTest {
    @Test
    fun `address-less conference call does not query contacts`() = runTest {
        var lookupCount = 0
        val useCase = GetCallerNameUseCase(repository { lookupCount += 1; null })

        val result = useCase(incomingNumber = "", cnamName = "Conference")

        assertEquals(0, lookupCount)
        assertEquals("Conference", result.name)
        assertEquals("", result.number)
        assertFalse(result.isLocalContact)
    }

    @Test
    fun `provider rejection falls back to network caller name`() = runTest {
        val useCase = GetCallerNameUseCase(repository {
            throw IllegalArgumentException("Provider rejected lookup")
        })

        val result = useCase(incomingNumber = "0700000101", cnamName = "Test Alexandru Butica")

        assertEquals("Test Alexandru Butica", result.name)
        assertEquals("0700000101", result.number)
        assertFalse(result.isLocalContact)
    }

    @Test
    fun `local contact still takes precedence`() = runTest {
        val useCase = GetCallerNameUseCase(repository {
            Contact(
                id = "42",
                givenName = "Alexandru",
                familyName = "Butica",
                phoneNumbers = listOf("0700000101"),
                photoUri = "content://contacts/42/photo",
            )
        })

        val result = useCase(incomingNumber = "0700000101", cnamName = "Network name")

        assertEquals("Alexandru Butica", result.name)
        assertEquals("content://contacts/42/photo", result.photoUri)
        assertTrue(result.isLocalContact)
    }

    private fun repository(block: suspend (String) -> Contact?): ICallerRepository =
        object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = block(number)
        }
}
