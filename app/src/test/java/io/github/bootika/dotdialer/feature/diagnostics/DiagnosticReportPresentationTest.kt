package io.github.bootika.dotdialer.feature.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportPresentationTest {
    @Test fun fullSizeJournalRemainsCompleteAndEveryLayoutIsBounded() {
        val text = "DOT DIALER DIAGNOSTICS\nEVENTS\n" + (1..2400).joinToString("\n") {
            "2026-10-06T08:00:00Z | call_audio | route=1 | bluetooth_available=false | bluetooth_permission=true | event=$it"
        }
        val report = prepareDiagnosticReport(text)
        assertEquals(text, report.fullText)
        assertEquals(text, report.blocks.joinToString(""))
        assertTrue(report.blocks.size > 200)
        assertTrue(report.blocks.all { it.length <= 1024 && it.count { char -> char == '\n' } <= 12 })
        assertTrue(report.blocks.last().contains("event=2400"))
    }
    @Test fun emptyTrailingNewlineBlankLinesAndUnicodeArePreserved() {
        for (text in listOf("", "\n", "header\n\nEVENTS\n\n", "🟢".repeat(2000), "x".repeat(5000))) {
            val report = prepareDiagnosticReport(text)
            assertEquals(text, report.blocks.joinToString(""))
            assertTrue(report.blocks.all { it.length <= 1024 })
            if (text.contains("🟢")) assertTrue(report.blocks.none { it.lastOrNull()?.isHighSurrogate() == true })
        }
    }
}
