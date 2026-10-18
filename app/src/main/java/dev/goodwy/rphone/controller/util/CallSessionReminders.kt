package dev.goodwy.rphone.controller.util

import android.content.Context
import android.os.SystemClock
import android.telecom.Call
import android.widget.Toast
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import io.github.bootika.dotdialer.core.haptics.HapticIntent
import io.github.bootika.dotdialer.core.license.PlanFeature
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class CallReminderStatus(val deadlineElapsed: Long, val due: Boolean = false)

/** Session-only state survives UI recreation, but never starts calls or creates notifications. */
internal class CallSessionReminders(
    context: Context,
    private val repository: ICallRepository,
    private val prefs: PreferenceManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val alert: () -> Unit = {
        AndroidHaptics.performPulse(context.applicationContext, HapticIntent.CONFIRM,
            prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
            customIntensity = prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, .5f),
            appEnabled = prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true))
        Toast.makeText(context.applicationContext, R.string.call_reminder_due, Toast.LENGTH_SHORT).show()
    },
) {
    private val current = MutableStateFlow<Map<Call, CallReminderStatus>>(emptyMap())
    val state = current.asStateFlow()
    private val jobs = mutableMapOf<Call, Job>()

    init {
        scope.launch {
            combine(repository.allCalls, repository.currentCallSession) { calls, _ -> calls }.collect { calls ->
                current.value.keys.filter { target -> calls.none { it === target } ||
                    target.currentTelecomState() in setOf(Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED) }
                    .forEach(::cancel)
            }
        }
    }

    fun schedule(call: Call, minutes: Int): Boolean {
        if (minutes !in setOf(2, 5, 10, 15) || !prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY) || !isLive(call)) return false
        cancel(call)
        val deadline = clock() + minutes * 60_000L
        current.value = current.value + (call to CallReminderStatus(deadline))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                delay((deadline - clock()).coerceAtLeast(0))
                if (isLive(call) && prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY)) {
                    current.value = current.value + (call to CallReminderStatus(deadline, due = true))
                    alert()
                } else current.value = current.value - call
            } finally { if (jobs[call] === coroutineContext[Job]) jobs.remove(call) }
        }
        jobs[call] = job
        job.start()
        return true
    }

    fun cancel(call: Call) {
        jobs.remove(call)?.cancel()
        current.value = current.value - call
    }

    private fun isLive(call: Call) = repository.allCalls.value.any { it === call } &&
        call.currentTelecomState() in setOf(Call.STATE_ACTIVE, Call.STATE_HOLDING) && !call.isTelecomConference()
}
