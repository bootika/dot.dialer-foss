package io.github.bootika.dotdialer.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSessionReducerTest {
    @Test
    fun `clears query and hides keyboard after a call is initiated`() {
        val result = SearchSessionReducer.reduce(
            SearchSessionState(query = "Ștefan", keyboardVisible = true),
            SearchSessionAction.CallInitiated,
        )

        assertEquals(SearchSessionState(query = "", keyboardVisible = false), result)
    }

    @Test
    fun `query changes preserve keyboard state`() {
        val result = SearchSessionReducer.reduce(
            SearchSessionState(query = "old", keyboardVisible = true),
            SearchSessionAction.QueryChanged("new"),
        )

        assertEquals(SearchSessionState(query = "new", keyboardVisible = true), result)
    }
}
