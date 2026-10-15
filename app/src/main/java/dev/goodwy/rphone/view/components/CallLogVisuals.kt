package dev.goodwy.rphone.view.components

import android.provider.CallLog
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import dev.goodwy.rphone.R

/** Visual states exposed by Android's call-log provider. */
enum class CallLogVisualStatus {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    NOT_ANSWERED,
    BLOCKED,
    OTHER,
}

data class CallLogVisual(
    val status: CallLogVisualStatus,
    val accentMetadata: Boolean,
    val emphasizeTime: Boolean,
)

/**
 * Keeps provider semantics out of the composables and makes the visual policy testable.
 * Android does not expose a reliable distinction between an unanswered outgoing call and
 * one explicitly declined by the remote party, so both are represented as NOT_ANSWERED.
 */
fun resolveCallLogVisual(type: Int, durationSeconds: Long, isBlocked: Boolean): CallLogVisual {
    val status = when {
        isBlocked || type == CallLog.Calls.BLOCKED_TYPE -> CallLogVisualStatus.BLOCKED
        type == CallLog.Calls.REJECTED_TYPE -> CallLogVisualStatus.REJECTED
        type == CallLog.Calls.MISSED_TYPE -> CallLogVisualStatus.MISSED
        type == CallLog.Calls.OUTGOING_TYPE && durationSeconds <= 0L -> CallLogVisualStatus.NOT_ANSWERED
        type == CallLog.Calls.OUTGOING_TYPE -> CallLogVisualStatus.OUTGOING
        type == CallLog.Calls.INCOMING_TYPE -> CallLogVisualStatus.INCOMING
        else -> CallLogVisualStatus.OTHER
    }
    return CallLogVisual(
        status = status,
        accentMetadata = status == CallLogVisualStatus.MISSED,
        emphasizeTime = status == CallLogVisualStatus.MISSED,
    )
}

@Composable
fun SamsungStyleCallStatusIcon(
    status: CallLogVisualStatus,
    modifier: Modifier = Modifier,
) {
    val spec = callStatusIconSpec(status)
    Icon(
        imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(spec.drawableRes),
        contentDescription = stringResource(spec.contentDescriptionRes),
        tint = callStatusColor(status),
        modifier = modifier,
    )
}

@Composable
fun callStatusLabel(status: CallLogVisualStatus): String =
    stringResource(callStatusIconSpec(status).contentDescriptionRes)

@Composable
fun missedCallRowColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SamsungMissedCallRedDark else SamsungMissedCallRedLight

@Composable
fun callStatusColor(status: CallLogVisualStatus): Color = when (status) {
    CallLogVisualStatus.INCOMING,
    CallLogVisualStatus.OUTGOING,
    -> MaterialTheme.colorScheme.primary
    CallLogVisualStatus.MISSED -> missedCallRowColor()
    CallLogVisualStatus.REJECTED,
    CallLogVisualStatus.BLOCKED,
    -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SamsungRejectedBlueDark else SamsungRejectedBlueLight
    CallLogVisualStatus.NOT_ANSWERED,
    CallLogVisualStatus.OTHER,
    -> MaterialTheme.colorScheme.onSurfaceVariant
}

private data class CallStatusIconSpec(
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val contentDescriptionRes: Int,
)

private fun callStatusIconSpec(status: CallLogVisualStatus): CallStatusIconSpec = when (status) {
    CallLogVisualStatus.INCOMING -> CallStatusIconSpec(R.drawable.ic_one_ui_call_incoming, R.string.incoming)
    CallLogVisualStatus.OUTGOING -> CallStatusIconSpec(R.drawable.ic_one_ui_call_outgoing, R.string.outgoing)
    CallLogVisualStatus.MISSED -> CallStatusIconSpec(R.drawable.ic_one_ui_call_missed, R.string.missed)
    CallLogVisualStatus.REJECTED -> CallStatusIconSpec(R.drawable.ic_one_ui_call_rejected, R.string.rejected)
    CallLogVisualStatus.NOT_ANSWERED -> CallStatusIconSpec(R.drawable.ic_one_ui_call_not_answered, R.string.not_answered)
    CallLogVisualStatus.BLOCKED -> CallStatusIconSpec(R.drawable.ic_one_ui_call_rejected, R.string.blocked)
    CallLogVisualStatus.OTHER -> CallStatusIconSpec(R.drawable.ic_one_ui_call_incoming, R.string.call)
}

// Sampled from the supplied One UI reference, then adjusted for WCAG contrast on both themes.
private val SamsungMissedCallRedLight = Color(0xFFC63E4C)
private val SamsungMissedCallRedDark = Color(0xFFFF8992)
private val SamsungRejectedBlueLight = Color(0xFF3478E5)
private val SamsungRejectedBlueDark = Color(0xFF82AEFF)
