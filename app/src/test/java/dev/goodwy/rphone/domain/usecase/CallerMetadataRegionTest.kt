package dev.goodwy.rphone.domain.usecase

import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CallerMetadataRegionTest {
    @Test fun sameNationalDigitsNeverShareEnrichmentAcrossCountriesOrUnknownCountry() = runTest {
        val useCase = GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = error("Scoped lookup required")
            override suspend fun getContactByNumberInRegion(number: String, regionIso: String?): Contact =
                Contact("101", givenName = "Test Alexandru Butica", photoUri = "content://example.test/photo/1")
        })
        val metadata = useCase("0722123456", null, "RO")
        assertEquals("0722123456", metadata.number)
        assertTrue(metadata.isLocalContact)
        assertEquals(metadata, metadata.forLookupRegion("RO"))
        assertNull(metadata.forLookupRegion("GB"))
        assertNull(metadata.forLookupRegion(null))
    }
    @Test fun originalUnknownRegionMetadataRemainsAvailableInItsOwnContext() {
        val original = CallerMetadata("0722123456", "Test Alexandru Butica", false)
        assertEquals(original, original.forLookupRegion(null))
        assertNull(original.forLookupRegion("RO"))
    }
}
