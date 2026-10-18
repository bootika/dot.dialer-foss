package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGestureActionsTest {
    @Test fun `all selectors share the approved stable ordered action IDs`() {
        assertEquals(listOf(0, 1, 2, 4, 7, 5), HOME_ROW_ACTIONS)
        HOME_ROW_ACTIONS.forEach { assertEquals(it, supportedHomeSwipeAction(it)) }
    }

    @Test fun `removed video and unsupported saved actions become no action`() {
        listOf(3, 6, -1, 999).forEach { assertEquals(0, supportedHomeSwipeAction(it)) }
    }

    @Test fun `legacy tap settings retain call or contact until explicitly changed`() {
        assertEquals(1, resolvedRecentTapAction(-1, true))
        assertEquals(7, resolvedRecentTapAction(-1, false))
        HOME_ROW_ACTIONS.forEach { action ->
            assertEquals(action, resolvedRecentTapAction(action, true))
            assertEquals(action, resolvedRecentTapAction(action, false))
        }
    }
    @Test fun `new delete ID is swipe only and never reactivates legacy delete`() {
        assertEquals(listOf(0, 1, 2, 4, 7, 5, 8), HOME_SWIPE_ACTIONS)
        assertEquals(8, supportedHomeSwipeAction(8))
        assertEquals(0, supportedHomeSwipeAction(6))
        assertEquals(7, resolvedRecentTapAction(8, false))
        assertEquals(1, resolvedRecentTapAction(8, true))
        assertEquals(8, contactSwipeAction(8, true))
        assertEquals(8, contactSwipeAction(8, false))
        HOME_ROW_ACTIONS.forEach { assertEquals(it, contactSwipeAction(it, true)); assertEquals(0, contactSwipeAction(it, false)) }
    }
}
