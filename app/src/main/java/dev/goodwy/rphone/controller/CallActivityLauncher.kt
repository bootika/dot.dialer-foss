package dev.goodwy.rphone.controller

import android.content.Context
import android.content.Intent
import android.telecom.Call
import dev.goodwy.rphone.controller.util.currentTelecomState
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticComponent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticOperation
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import kotlinx.coroutines.delay

internal fun Context.launchCallActivitySafely(outgoing: Boolean = false): Boolean {
    if (outgoing && CallActivity.isInForeground.value) {
        CallUiHandoff.controller.contentReady()
        return true
    }
    val transferToken = if (outgoing) CallUiHandoff.controller.claimLaunch() ?: return true else null
    return try {
        startActivity(
            Intent(this, CallActivity::class.java).apply {
                if (outgoing) putExtra(CallActivity.EXTRA_PENDING_OUTGOING_CALL, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
        true
    } catch (error: Exception) {
        transferToken?.let(CallUiHandoff.controller::cancel)
        AppDiagnostics.record(
            DiagnosticEvent.Failure(
                component = DiagnosticComponent.CALL_SERVICE,
                operation = DiagnosticOperation.START_ACTIVITY,
                errorType = error.javaClass.simpleName,
            ),
        )
        false
    }
}

internal suspend fun incomingCallActivityDidNotAppear(
    call: Call,
    isActivityVisible: () -> Boolean,
): Boolean {
    delay(1_200L)
    return call.currentTelecomState() == Call.STATE_RINGING && !isActivityVisible()
}
