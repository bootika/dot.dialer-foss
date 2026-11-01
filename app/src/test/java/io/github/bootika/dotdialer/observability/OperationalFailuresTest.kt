package io.github.bootika.dotdialer.observability

import org.junit.Assert.*
import org.junit.Test

class OperationalFailuresTest {
    @Test fun `rejects unbounded values deduplicates and preserves safe grouping after scrub`() {
        assertNull(OperationalFailures.event("+40720000001", "internal", 0))
        assertNull(OperationalFailures.event("capture", "private transcript", 0))
        val event = OperationalFailures.event("bridge_sync", "invalid_response", 1000)!!
        val scrubbed = SentryPrivacyFilter.scrub(event)
        assertEquals("bridge_sync", scrubbed.getTag("stage"))
        assertEquals(listOf("dot_dialer_operational_failure", "bridge_sync", "invalid_response"), scrubbed.fingerprints)
        assertNull(scrubbed.throwable)
        assertNull(OperationalFailures.event("bridge_sync", "invalid_response", 1001))
        assertNotNull(OperationalFailures.event("bridge_sync", "invalid_response", 301000))
        repeat(18) { OperationalFailures.event("capture", "internal", 1_000_000L + it * 300_000L) }
        assertNull(OperationalFailures.event("capture_finalize", "storage", 10_000_000))
    }
}
