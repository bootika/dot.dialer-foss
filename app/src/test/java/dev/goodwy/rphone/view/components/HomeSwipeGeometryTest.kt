package dev.goodwy.rphone.view.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSwipeGeometryTest {

    @Test
    fun `action rail is twenty five percent deeper and has no overdraw gap`() {
        assertEquals(85, HOME_SWIPE_ACTION_WIDTH_DP)
        assertEquals(58, HOME_SWIPE_TRIGGER_DP)
        assertTrue(HOME_SWIPE_TRIGGER_DP < HOME_SWIPE_ACTION_WIDTH_DP)
    }
}
