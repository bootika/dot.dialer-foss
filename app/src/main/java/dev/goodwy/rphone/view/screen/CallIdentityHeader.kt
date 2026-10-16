package dev.goodwy.rphone.view.screen

import android.telecom.Call
import android.telecom.PhoneAccount
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.CallingAccountIdentityBadge

/**
 * Stable identity and call-metadata block shared by every call state.
 *
 * Calling state and connection quality share one stable slot. The timer keeps its own reserved
 * slot so connecting cannot move the middle visual zone.
 */
@Composable
internal fun CallIdentityHeader(
    contactName: String,
    companyName: String?,
    callState: Int,
    duration: String,
    phoneAccount: PhoneAccount?,
    connectionInfo: CallConnectionInfo?,
    spacing: CallScreenSpacing,
    identityVerticalCompensationDp: Int,
    onConnectionDetailsClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.absorbSupplementalIdentityHeight(identityVerticalCompensationDp).testTag("callIdentityHeader"),
    ) {
        Text(
            text = contactName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        companyName?.let { company ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = company,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(spacing.nameToStatusGapDp.dp))
        CallMetadataRegion(
            callState = callState,
            duration = duration,
            phoneAccount = phoneAccount,
            connectionInfo = connectionInfo,
            spacing = spacing,
            onConnectionDetailsClick = onConnectionDetailsClick,
        )
    }
}

@Composable
private fun callStatusText(callState: Int): String = when (callState) {
    Call.STATE_DISCONNECTED -> stringResource(R.string.call_status_ended)
    Call.STATE_HOLDING -> stringResource(R.string.call_status_on_hold)
    Call.STATE_ACTIVE -> stringResource(R.string.call_status_active)
    Call.STATE_DIALING -> stringResource(R.string.call_status_calling)
    Call.STATE_RINGING -> stringResource(R.string.call_status_incoming)
    else -> stringResource(R.string.call_status_connecting)
}

@Composable
private fun CallMetadataRegion(
    callState: Int,
    duration: String,
    phoneAccount: PhoneAccount?,
    connectionInfo: CallConnectionInfo?,
    spacing: CallScreenSpacing,
    onConnectionDetailsClick: () -> Unit,
) {
    var lastNonEndingState by remember { mutableStateOf(Call.STATE_DISCONNECTED) }
    var lastNonEndingDuration by remember { mutableStateOf(duration) }
    SideEffect {
        if (callState != Call.STATE_DISCONNECTING && callState != Call.STATE_DISCONNECTED) {
            lastNonEndingState = callState
            lastNonEndingDuration = duration
        }
    }
    val displayState = callMetadataDisplayState(callState, lastNonEndingState)
    val displayDuration = if (callState == Call.STATE_DISCONNECTING) lastNonEndingDuration else duration
    val ending = callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED
    val fontScale = callMetadataTextScale()
    val timerSlotHeightDp = callTimerSlotHeightDp(spacing.timerSlotHeightDp, fontScale)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val geometry = measuredCallConnectionStatusGeometry(maxWidth, phoneAccount, connectionInfo?.transport ?: CallTransportKind.Cellular)
        val shieldHeightDp = geometry.touchHeightDp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    (
                        shieldHeightDp +
                            spacing.connectionToTimerGapDp +
                            timerSlotHeightDp
                        ).dp,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                targetState = displayState == Call.STATE_ACTIVE && connectionInfo != null,
                transitionSpec = {
                    if (ending) EnterTransition.None.togetherWith(ExitTransition.None).using(null)
                    else (fadeIn(tween(220, delayMillis = 90)) +
                        scaleIn(tween(220, delayMillis = 90), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "callMetadataState",
            ) { showConnectionQuality ->
                if (showConnectionQuality && connectionInfo != null) {
                    CallConnectionStatusRow(
                        info = connectionInfo,
                        duration = displayDuration,
                        onClick = onConnectionDetailsClick,
                        modifier = Modifier.fillMaxWidth(),
                        account = phoneAccount,
                        timerGapDp = spacing.connectionToTimerGapDp,
                        geometryOverride = geometry,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.height(shieldHeightDp.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (displayState == Call.STATE_ACTIVE) {
                                phoneAccount?.let { CallingAccountIdentityBadge(it) }
                            } else {
                                Text(
                                    text = callStatusText(displayState),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Normal,
                                    color = if (displayState == Call.STATE_HOLDING) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(spacing.connectionToTimerGapDp.dp))
                        Box(
                            modifier = Modifier.height(timerSlotHeightDp.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (displayState == Call.STATE_ACTIVE || displayState == Call.STATE_HOLDING) {
                                Text(
                                    text = displayDuration,
                                    modifier = Modifier.width(callDurationSlotWidthDp(displayDuration, fontScale).dp).testTag("callPrimaryTimer"),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun callMetadataDisplayState(state: Int, lastNonEndingState: Int): Int =
    if (state == Call.STATE_DISCONNECTING) lastNonEndingState else state
