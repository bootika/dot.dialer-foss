package io.github.bootika.dotdialer.core.diagnostics

object DiagnosticFormatter {
    fun format(event: DiagnosticEvent): String = when (event) {
        is DiagnosticEvent.AppStarted -> fields(
            "app_started",
            "version" to event.versionName,
            "version_code" to event.versionCode,
            "flavor" to event.flavor,
            "api" to event.apiLevel,
            "manufacturer" to event.manufacturer,
            "model" to event.model,
        )

        is DiagnosticEvent.ActivityLifecycle -> fields(
            "main_activity",
            "stage" to event.stage,
            "changing_configuration" to event.changingConfiguration,
        )

        is DiagnosticEvent.CallActivityLifecycle -> fields(
            "call_activity",
            "stage" to event.stage,
            "pending_outgoing" to event.pendingOutgoingLaunch,
            "telecom_in_call" to (event.telecomInCall ?: "unknown"),
        )

        DiagnosticEvent.CallServiceStarted -> "call_service_started"

        is DiagnosticEvent.CallAdded -> fields(
            "call_added",
            "session" to event.sessionId,
            "phase" to event.phase,
            "active_calls" to event.activeCallCount,
        )

        is DiagnosticEvent.CallPhaseChanged -> fields(
            "call_phase_changed",
            "session" to event.sessionId,
            "phase" to event.phase,
        )

        is DiagnosticEvent.CallRemoved -> fields(
            "call_removed",
            "session" to event.sessionId,
            "remaining_calls" to event.remainingCallCount,
        )

        is DiagnosticEvent.CallsReconciled -> fields(
            "calls_reconciled",
            "active_calls" to event.activeCallCount,
            "foreground_phase" to (event.foregroundPhase ?: "none"),
            "call_ui" to event.showCallUi,
            "notification" to event.showNotification,
            "floating_ui" to event.showFloatingUi,
        )

        is DiagnosticEvent.CallServiceStopped -> fields(
            "call_service_stopped",
            "active_calls" to event.activeCallCount,
        )

        is DiagnosticEvent.Failure -> fields(
            "failure",
            "component" to event.component,
            "operation" to event.operation,
            "error_type" to event.errorType,
        )
    }

    private fun fields(name: String, vararg values: Pair<String, Any>): String = buildString {
        append(name)
        values.forEach { (key, value) ->
            append(" | ")
            append(key)
            append('=')
            append(sanitize(value.toString()))
        }
    }

    internal fun sanitize(value: String): String = value
        .map { character ->
            when {
                character == '|' -> '/'
                character.isISOControl() -> ' '
                else -> character
            }
        }
        .joinToString(separator = "")
        .trim()
        .take(MAX_VALUE_LENGTH)

    private const val MAX_VALUE_LENGTH = 80
}
