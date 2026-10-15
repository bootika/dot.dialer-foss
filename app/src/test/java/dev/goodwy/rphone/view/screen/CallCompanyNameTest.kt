package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallCompanyNameTest {
    @Test fun nullableProviderAndPendingIdentityOmitTheCompanyRow() {
        listOf(null, "", " ", "\n\t").forEach { assertNull(displayedCallCompanyName(it, "Test Alexandru Butica")) }
    }
    @Test fun duplicateContactNameIsOmittedIgnoringCase() {
        assertNull(displayedCallCompanyName("TEST ALEXANDRU BUTICA", "Test Alexandru Butica"))
    }
    @Test fun distinctCompanyRetainsTheOriginalText() {
        assertEquals("Atelier local", displayedCallCompanyName("Atelier local", "Test Alexandru Butica"))
    }
    @Test fun companyCanBecomeUnavailableAndReturnAcrossMetadataRefreshes() {
        assertEquals(listOf("Atelier local", null, "Atelier local"),
            listOf("Atelier local", null, "Atelier local").map { displayedCallCompanyName(it, "Test Alexandru Butica") })
    }
}
