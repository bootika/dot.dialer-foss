package io.github.bootika.dotdialer.core.diagnostics

sealed interface DiagnosticEvent {
    data class AppStarted(
        val versionName: String,
        val versionCode: Long,
        val flavor: String,
        val apiLevel: Int,
        val manufacturer: String,
        val model: String,
    ) : DiagnosticEvent

    data class ActivityLifecycle(
        val stage: ActivityStage,
        val changingConfiguration: Boolean = false,
    ) : DiagnosticEvent

    data class CallActivityLifecycle(
        val stage: ActivityStage,
        val pendingOutgoingLaunch: Boolean,
        val telecomInCall: Boolean?,
    ) : DiagnosticEvent

    data object CallServiceStarted : DiagnosticEvent

    data class CallAdded(
        val sessionId: String,
        val phase: String,
        val activeCallCount: Int,
    ) : DiagnosticEvent

    data class CallPhaseChanged(
        val sessionId: String,
        val phase: String,
    ) : DiagnosticEvent

    data class CallRemoved(
        val sessionId: String,
        val remainingCallCount: Int,
    ) : DiagnosticEvent

    data class CallsReconciled(
        val activeCallCount: Int,
        val foregroundPhase: String?,
        val showCallUi: Boolean,
        val showNotification: Boolean,
        val showFloatingUi: Boolean,
    ) : DiagnosticEvent

    data class CallServiceStopped(val activeCallCount: Int) : DiagnosticEvent

    data class Failure(
        val component: DiagnosticComponent,
        val operation: DiagnosticOperation,
        val errorType: String,
    ) : DiagnosticEvent
}

enum class ActivityStage {
    CREATED,
    RESUMED,
    STOPPED,
    DESTROYED,
    NEW_INTENT,
}

enum class DiagnosticComponent {
    APPLICATION,
    MAIN_ACTIVITY,
    CALL_SERVICE,
    DIAGNOSTICS,
}

enum class DiagnosticOperation {
    INITIALIZE,
    START_ACTIVITY,
    REGISTER_RECEIVER,
    UNREGISTER_RECEIVER,
    PERSIST_EVENT,
}
