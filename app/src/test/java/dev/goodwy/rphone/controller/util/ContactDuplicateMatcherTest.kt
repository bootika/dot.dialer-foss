package dev.goodwy.rphone.controller.util

import android.app.Application
import dev.goodwy.rphone.modal.data.Contact
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class)
class ContactDuplicateMatcherTest {
    @Test fun `national and international forms share full E164 identity`() {
        for ((region,national,international) in listOf(
            Triple("RO","0722 123 456","+40722123456"),
            Triple("GB","020 7946 0018","+442079460018"),
            Triple("US","202-555-0123","+12025550123"),
            Triple("IT","02 36618 300","+390236618300"),
            Triple("DE","030 123456","+4930123456"))) {
            assertEquals(region,international,canonicalMergeNumber(national,region))
            assertEquals(region,international,canonicalMergeNumber(international,region))
        }
    }
    @Test fun `prefix handling is regional and Italy keeps significant zero`() {
        assertEquals("+442079460018",canonicalMergeNumber("00442079460018","RO"))
        assertEquals("+390236618300",canonicalMergeNumber("02 36618 300","IT"))
        assertNull(canonicalMergeNumber("00442079460018","US"))
    }
    @Test fun `ambiguous national short service extension and invalid strings are excluded`() {
        for (number in listOf("0722123456","112","1234567","","*123#","+40722123456;12",
            "+40722123456 ext 12","++40722123456","407+22123456")) {
            assertNull(number,canonicalMergeNumber(number,null))
        }
        assertNull(canonicalMergeNumber("112","RO"))
        assertEquals("+40722123456",canonicalMergeNumber("+40 (722) 123-456",null))
    }
    private fun contact(id:String,name:String,vararg numbers:String) =
        Contact(id=id,givenName=name,phoneNumbers=numbers.toList())
    @Test fun `name equality and seven to nine digit tails never cause merge`() {
        val contacts=listOf(contact("1","Test Alexandru Butica","+40722123456"),
            contact("2","Test Alexandru Butica","+44722123456"),
            contact("3","Mara","+40723123456"))
        assertTrue(duplicatePhoneGroups(contacts,"RO").isEmpty())
    }
    @Test fun `overlap and repeated numbers produce one distinct candidate component`() {
        val a=contact("1","Mara","0722123456","0723123456","0722123456")
        val b=contact("2","Elena","+40722123456")
        val c=contact("3","Test Alexandru Butica","+40723123456")
        val ignored=b.copy(id="p1",isPrivate=true)
        assertEquals(listOf(listOf("1","2","3")),
            duplicatePhoneGroups(listOf(a,b,c,ignored,a),"RO").map { group -> group.map(Contact::id) })
    }
    @Test fun `international candidates work without a known national region`() {
        val contacts=listOf(contact("1","Mara","0722123456"),
            contact("2","Elena","+40722123456"),contact("3","Test Alexandru Butica","+40722123456"))
        assertEquals(listOf(listOf("2","3")),duplicatePhoneGroups(contacts,null).map { it.map(Contact::id) })
    }
}
