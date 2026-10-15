package dev.goodwy.rphone.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class CallIdentityResolverTest {
    @Test
    fun `conference identity never falls through to unknown`() {
        assertEquals(
            "Conference",
            baseCallIdentityName(
                isConference = true,
                conferenceLabel = "Conference",
                callerDisplayName = null,
                number = "",
                unknownLabel = "Unknown",
            ),
        )
    }

    @Test
    fun `regular identity preserves caller name then number fallback`() {
        assertEquals(
            "Mara Ionescu",
            baseCallIdentityName(false, "Conference", "Mara Ionescu", "0700000102", "Unknown"),
        )
        assertEquals(
            "0700000102",
            baseCallIdentityName(false, "Conference", null, "0700000102", "Unknown"),
        )
    }
}
