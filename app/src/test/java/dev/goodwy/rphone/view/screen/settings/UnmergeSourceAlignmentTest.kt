package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UnmergeSourceAlignmentTest {
    private val sources=listOf(ContactSource("first@example.com","com.google",rawContactId="11"),
        ContactSource("second@example.com","com.google",rawContactId="21"))
    @Test fun `same provider different raw sources keep identity and order`()=runTest {
        val contacts=loadMergedSourceContacts(sources) {Contact(id=it,givenName="Test Alexandru Butica")}
        assertEquals(listOf("11","21"),contacts.map {it.id})
    }
    @Test fun `missing first source cannot relabel second source as first`()=runTest {
        val result=runCatching {loadMergedSourceContacts(sources) {if(it=="11") null else Contact(id=it)}}
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun `provider failure and cancellation are not silently skipped`()=runTest {
        for(error in listOf(SecurityException("Test permission revoked"),CancellationException("Screen closed"))) {
            val result=runCatching {loadMergedSourceContacts(sources) {throw error}}
            assertSame(error,result.exceptionOrNull())
        }
    }
}
