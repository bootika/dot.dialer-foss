package io.github.bootika.dotdialer.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFormatterTest {
    @Test
    fun `call activity lifecycle shows whether foreground launch reached the screen`() {
        val line = DiagnosticFormatter.format(
            DiagnosticEvent.CallActivityLifecycle(
                stage = ActivityStage.RESUMED,
                pendingOutgoingLaunch = true,
                telecomInCall = true,
            )
        )

        assertEquals(
            "call_activity | stage=RESUMED | pending_outgoing=true | telecom_in_call=true",
            line,
        )
    }

    @Test
    fun `call events contain only session identity and lifecycle state`() {
        val line = DiagnosticFormatter.format(
            DiagnosticEvent.CallAdded(
                sessionId = "call-7",
                phase = "ACTIVE",
                activeCallCount = 1,
            )
        )

        assertEquals("call_added | session=call-7 | phase=ACTIVE | active_calls=1", line)
        assertFalse(line.contains("phone", ignoreCase = true))
        assertFalse(line.contains("contact", ignoreCase = true))
    }

    @Test
    fun `untrusted platform labels cannot create extra fields or lines`() {
        val line = DiagnosticFormatter.format(
            DiagnosticEvent.AppStarted(
                versionName = "test\nforged=true",
                versionCode = 1,
                flavor = "foss|secret=value",
                apiLevel = 37,
                manufacturer = "Example\rMaker",
                model = "Model",
            )
        )

        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
        assertTrue(line.contains("flavor=foss/secret=value"))
    }

    @Test
    fun `untrusted values are bounded`() {
        val longErrorType = "x".repeat(500)
        val line = DiagnosticFormatter.format(
            DiagnosticEvent.Failure(
                component = DiagnosticComponent.DIAGNOSTICS,
                operation = DiagnosticOperation.PERSIST_EVENT,
                errorType = longErrorType,
            )
        )

        assertTrue(line.length < 200)
        assertTrue(line.endsWith("x".repeat(80)))
    }
}
