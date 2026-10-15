package io.github.bootika.dotdialer.observability

import org.junit.Assert.*
import org.junit.Test

class ReportingModeTest {
    @Test fun automaticIsDefaultAndValuesRoundTrip() {
        assertEquals(ReportingMode.Automatic, ReportingMode.parse(null))
        for (mode in ReportingMode.entries) assertEquals(mode, ReportingMode.parse(mode.value))
    }
    @Test fun optOutBlocksExistingAutomaticTransportAndAutomaticEvents() {
        assertFalse(ReportingMode.maySend(ReportingMode.OnDemand, ReportingMode.Automatic, false))
        assertFalse(ReportingMode.maySend(ReportingMode.OnDemand, ReportingMode.Automatic, true))
        assertFalse(ReportingMode.maySend(ReportingMode.OnDemand, ReportingMode.OnDemand, false))
        assertTrue(ReportingMode.maySend(ReportingMode.OnDemand, ReportingMode.OnDemand, true))
        assertTrue(ReportingMode.maySend(ReportingMode.Automatic, ReportingMode.Automatic, false))
    }
}
