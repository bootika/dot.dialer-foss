package dev.goodwy.rphone.view.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsListTypographyTest {

    @Test
    fun contactNamesStayAtTheCompactRecentsScale() {
        assertEquals(16, contactsListNameSizeSp(16))
        assertEquals(16, contactsListNameSizeSp(18))
        assertEquals(16, contactsListNameSizeSp(22))
    }
}
