package io.github.bootika.dotdialer.core.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe owner of the app-level call lifecycle.
 *
 * All Android Telecom callbacks must enter through this coordinator before they update UI,
 * notifications or floating surfaces. This makes terminal cleanup idempotent and prevents an
 * event from an ended session from mutating a newer session.
 */
class CallLifecycleCoordinator(
    initialState: CallLifecycleState = CallLifecycleState(),
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<CallLifecycleState> = mutableState.asStateFlow()

    fun dispatch(event: CallLifecycleEvent): CallLifecycleState = synchronized(lock) {
        val current = mutableState.value
        val updated = CallLifecycleReducer.reduce(current, event)
        if (updated !== current) {
            mutableState.value = updated
        }
        updated
    }
}
