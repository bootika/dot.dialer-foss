package dev.goodwy.rphone.controller

import org.junit.Assert.*
import org.junit.Test

class PlayCheckoutRequestTest {
    @Test fun leavingOrReplacingThePurchaseScreenInvalidatesOnlyPreparedCheckout() {
        val requests=PlayCheckoutRequest()
        val old=requests.begin()
        assertTrue(requests.isCurrent(old))
        assertTrue(requests.cancel())
        assertFalse(requests.isCurrent(old))
        val current=requests.begin()
        assertFalse(requests.isCurrent(old));assertTrue(requests.isCurrent(current))
        requests.finish(current)
        assertFalse(requests.cancel())
        assertFalse(requests.isCurrent(current))
    }
}
