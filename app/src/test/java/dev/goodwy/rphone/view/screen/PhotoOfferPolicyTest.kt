package dev.goodwy.rphone.view.screen

import org.junit.Assert.*
import org.junit.Test

class PhotoOfferPolicyTest {
    @Test fun requiresBothTimeAndUsage() {
        val start = 100L
        assertFalse(PhotoOfferPolicy.eligible(start + PhotoOfferPolicy.WAIT_MILLIS - 1, start, 3, false))
        assertFalse(PhotoOfferPolicy.eligible(start + PhotoOfferPolicy.WAIT_MILLIS, start, 2, false))
        assertTrue(PhotoOfferPolicy.eligible(start + PhotoOfferPolicy.WAIT_MILLIS, start, 3, false))
    }
    @Test fun dismissalAndClockRollbackCannotTriggerAnOffer() {
        assertFalse(PhotoOfferPolicy.eligible(Long.MAX_VALUE, 100, 10, true))
        assertFalse(PhotoOfferPolicy.eligible(99, 100, 10, false))
        assertFalse(PhotoOfferPolicy.eligible(Long.MAX_VALUE, 0, 10, false))
    }
}
