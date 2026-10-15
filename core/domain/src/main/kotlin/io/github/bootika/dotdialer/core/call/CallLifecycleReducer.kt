package io.github.bootika.dotdialer.core.call

/**
 * Framework-independent call lifecycle model.
 *
 * Android Telecom objects must stay in the Android adapter. The rest of the app consumes this
 * immutable state so delayed callbacks from an old call cannot revive or overwrite a new call.
 */
enum class CallPhase {
    RINGING,
    DIALING,
    ACTIVE,
    HELD,
    ENDING,
    ENDED,
}

data class CallSessionState(
    val id: String,
    val phase: CallPhase,
    internal val sequence: Long,
)

data class CallSurfaceState(
    val showCallUi: Boolean = false,
    val showIncomingNotification: Boolean = false,
    val showOngoingNotification: Boolean = false,
    val showFloatingUi: Boolean = false,
)

data class CallLifecycleState(
    val sessions: Map<String, CallSessionState> = emptyMap(),
    val foregroundSessionId: String? = null,
    val endedSessionIds: Set<String> = emptySet(),
    internal val nextSequence: Long = 1L,
) {
    val foregroundSession: CallSessionState?
        get() = foregroundSessionId?.let(sessions::get)

    val surfaces: CallSurfaceState
        get() = when (foregroundSession?.phase) {
            CallPhase.RINGING -> CallSurfaceState(
                showCallUi = true,
                showIncomingNotification = true,
            )

            CallPhase.DIALING,
            CallPhase.ACTIVE,
            CallPhase.HELD,
            -> CallSurfaceState(
                showCallUi = true,
                showOngoingNotification = true,
                showFloatingUi = true,
            )

            CallPhase.ENDING -> CallSurfaceState(showCallUi = true)
            CallPhase.ENDED,
            null,
            -> CallSurfaceState()
        }
}

sealed interface CallLifecycleEvent {
    data class CallAdded(
        val sessionId: String,
        val initialPhase: CallPhase,
    ) : CallLifecycleEvent

    data class PhaseChanged(
        val sessionId: String,
        val phase: CallPhase,
    ) : CallLifecycleEvent

    data class CallRemoved(val sessionId: String) : CallLifecycleEvent

    /** Fresh snapshot from the Android Telecom adapter. Missing sessions are no longer active. */
    data class Reconciled(val activeCalls: Map<String, CallPhase>) : CallLifecycleEvent

    /** Used only when the service is known to have no calls to recover. */
    data object ServiceStopped : CallLifecycleEvent
}

object CallLifecycleReducer {
    private const val MAX_ENDED_SESSION_IDS = 128

    fun reduce(
        state: CallLifecycleState,
        event: CallLifecycleEvent,
    ): CallLifecycleState = when (event) {
        is CallLifecycleEvent.CallAdded -> addCall(state, event.sessionId, event.initialPhase)
        is CallLifecycleEvent.PhaseChanged -> changePhase(state, event.sessionId, event.phase)
        is CallLifecycleEvent.CallRemoved -> removeCall(state, event.sessionId)
        is CallLifecycleEvent.Reconciled -> reconcile(state, event.activeCalls)
        CallLifecycleEvent.ServiceStopped -> stopService(state)
    }

    private fun addCall(
        state: CallLifecycleState,
        sessionId: String,
        initialPhase: CallPhase,
    ): CallLifecycleState {
        if (sessionId.isBlank() || sessionId in state.endedSessionIds) return state
        if (initialPhase == CallPhase.ENDED) return finishSession(state, sessionId)

        val existing = state.sessions[sessionId]
        if (existing != null) {
            return if (existing.phase == initialPhase) state else changePhase(state, sessionId, initialPhase)
        }

        val sessions = state.sessions + (
            sessionId to CallSessionState(
                id = sessionId,
                phase = initialPhase,
                sequence = state.nextSequence,
            )
        )
        return state.copy(
            sessions = sessions,
            foregroundSessionId = chooseForeground(sessions),
            nextSequence = state.nextSequence + 1,
        )
    }

    private fun changePhase(
        state: CallLifecycleState,
        sessionId: String,
        phase: CallPhase,
    ): CallLifecycleState {
        if (sessionId in state.endedSessionIds) return state
        val current = state.sessions[sessionId] ?: return state
        if (phase == CallPhase.ENDED) return finishSession(state, sessionId)
        if (current.phase == phase) return state

        val sessions = state.sessions + (sessionId to current.copy(phase = phase))
        return state.copy(
            sessions = sessions,
            foregroundSessionId = chooseForeground(sessions),
        )
    }

    private fun removeCall(
        state: CallLifecycleState,
        sessionId: String,
    ): CallLifecycleState {
        if (sessionId in state.endedSessionIds) return state
        return finishSession(state, sessionId)
    }

    private fun finishSession(
        state: CallLifecycleState,
        sessionId: String,
    ): CallLifecycleState {
        if (sessionId.isBlank()) return state
        val sessions = state.sessions - sessionId
        return state.copy(
            sessions = sessions,
            foregroundSessionId = chooseForeground(sessions),
            endedSessionIds = appendEndedSession(state.endedSessionIds, sessionId),
        )
    }

    private fun reconcile(
        state: CallLifecycleState,
        activeCalls: Map<String, CallPhase>,
    ): CallLifecycleState {
        var result = state

        val activeIds = activeCalls
            .filterValues { it != CallPhase.ENDED }
            .keys
        (result.sessions.keys - activeIds).forEach { missingId ->
            result = finishSession(result, missingId)
        }

        activeCalls.forEach { (sessionId, phase) ->
            result = when {
                phase == CallPhase.ENDED -> finishSession(result, sessionId)
                sessionId in result.sessions -> changePhase(result, sessionId, phase)
                else -> addCall(result, sessionId, phase)
            }
        }

        return result.copy(foregroundSessionId = chooseForeground(result.sessions))
    }

    private fun stopService(state: CallLifecycleState): CallLifecycleState {
        val ended = state.sessions.keys.fold(state.endedSessionIds, ::appendEndedSession)
        return state.copy(
            sessions = emptyMap(),
            foregroundSessionId = null,
            endedSessionIds = ended,
        )
    }

    private fun chooseForeground(sessions: Map<String, CallSessionState>): String? = sessions.values
        .minWithOrNull(
            compareBy<CallSessionState> { priority(it.phase) }
                .thenByDescending { it.sequence }
        )
        ?.id

    private fun priority(phase: CallPhase): Int = when (phase) {
        CallPhase.RINGING -> 0
        CallPhase.DIALING -> 1
        CallPhase.ACTIVE -> 2
        CallPhase.HELD -> 3
        CallPhase.ENDING -> 4
        CallPhase.ENDED -> 5
    }

    private fun appendEndedSession(
        endedSessionIds: Set<String>,
        sessionId: String,
    ): Set<String> = buildSet {
        addAll(endedSessionIds)
        add(sessionId)
    }.toList().takeLast(MAX_ENDED_SESSION_IDS).toSet()
}
