package dev.goodwy.rphone.view.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterStateTest {

    @Test
    fun defaultRequiresEveryImplementedSearchCategory() {
        assertTrue(SearchFilterState().isDefault)
        assertFalse(SearchFilterState(contacts = false).isDefault)
        assertFalse(SearchFilterState(nonContacts = false).isDefault)
        assertFalse(SearchFilterState(contactNotes = false).isDefault)
    }
}
