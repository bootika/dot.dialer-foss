package dev.goodwy.rphone.view.screen

import androidx.compose.ui.text.font.FontWeight
import android.telecom.Call
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import dev.goodwy.rphone.view.components.callPressFeedback
import dev.goodwy.rphone.view.components.rememberCallPressColor
import dev.goodwy.rphone.view.components.rememberCallTapHaptic
import dev.goodwy.rphone.view.components.rememberCallTapFeedback
import dev.goodwy.rphone.view.components.rememberEnabledCallAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.components.DotDialerMergeIcon
import dev.goodwy.rphone.view.components.CallControlIcons

import dev.goodwy.rphone.view.theme.color_call_end

internal fun secondaryCallActionsNeedSeparateRow(widthDp: Float, fontScale: Float, canSwap: Boolean): Boolean =
    canSwap || widthDp < 280f * fontScale.coerceAtLeast(1f)

internal object SecondaryCallCardStyle {
    const val OuterVerticalPaddingDp = 6
    const val InnerVerticalPaddingDp = 12
    const val ActionMinHeightDp = 48
    const val ActionGapDp = 12
    const val IdentityActionGapDp = 12
    fun actionShield(tint: Color, dark: Boolean): Color = tint.copy(alpha = if (dark) .14f else .08f)
}

@Composable
internal fun SecondaryCallCard(
    otherCall: Call?,
    otherCallName: String,
    otherCallColorSeed: String = otherCallName,
    otherCallPhotoUri: String? = null,
    otherCallNumber: String = "",
    currentCallState: Int,
    onSwap: () -> Unit,
    canMerge: Boolean = false,
    onMerge: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = otherCall != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        otherCall?.let { secondaryCall ->
            val snapshot by rememberTelecomCallSnapshot(secondaryCall)
            val otherState = snapshot.state
            val elapsed = rememberCallElapsedSeconds(otherState, snapshot.details?.connectTimeMillis ?: 0L)
            val otherStatus = when (otherState) {
                Call.STATE_ACTIVE -> stringResource(R.string.call_status_active)
                Call.STATE_HOLDING -> stringResource(R.string.call_status_on_hold)
                Call.STATE_DIALING -> stringResource(R.string.call_status_calling)
                Call.STATE_RINGING -> stringResource(R.string.call_status_incoming)
                else -> stringResource(R.string.call_status_connecting)
            }
            val isEstablishedPair = currentCallState != Call.STATE_RINGING &&
                (otherState == Call.STATE_ACTIVE || otherState == Call.STATE_HOLDING)
            val canSwap = isEstablishedPair &&
                (currentCallState == Call.STATE_ACTIVE || currentCallState == Call.STATE_HOLDING)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SecondaryCallCardStyle.OuterVerticalPaddingDp.dp, horizontal = 12.dp)
                    .testTag("secondaryCallCard"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                BoxWithConstraints(modifier = Modifier.padding(horizontal = 12.dp, vertical = SecondaryCallCardStyle.InnerVerticalPaddingDp.dp)) {
                    val separateActions = isEstablishedPair && secondaryCallActionsNeedSeparateRow(
                        maxWidth.value, LocalDensity.current.fontScale, canSwap,
                    )
                    val horizontalActions = maxWidth.value >= 280f * LocalDensity.current.fontScale
                    val actions: @Composable () -> Unit = {
                        Row(
                            modifier = Modifier
                                .then(if (separateActions) Modifier.fillMaxWidth() else Modifier)
                                .height(IntrinsicSize.Min)
                                .testTag("secondaryCallActions"),
                            horizontalArrangement = Arrangement.spacedBy(SecondaryCallCardStyle.ActionGapDp.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (canSwap) {
                                SecondaryCallAction(
                                    icon = CallControlIcons.Swap,
                                    label = stringResource(R.string.swap),
                                    horizontal = horizontalActions,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    testTag = "secondarySwapAction",
                                    onClick = onSwap,
                                )
                                SecondaryCallAction(
                                    icon = DotDialerMergeIcon,
                                    label = stringResource(R.string.merge_calls),
                                    horizontal = horizontalActions,
                                    enabled = canMerge,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    testTag = "secondaryMergeAction",
                                    onClick = onMerge,
                                )
                            }
                            SecondaryCallAction(
                                icon = DotDialerHangupIcon,
                                label = stringResource(R.string.end_call_short),
                                tint = color_call_end,
                                compactEnd = true,
                                modifier = Modifier.size(48.dp),
                                testTag = "secondaryEndAction",
                                onClick = secondaryCall::disconnect,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(if (separateActions) SecondaryCallCardStyle.IdentityActionGapDp.dp else 0.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("secondaryCallIdentity"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SmallCallAvatar(
                                name = otherCallName,
                                colorSeed = otherCallColorSeed,
                                number = otherCallNumber,
                                photoUri = otherCallPhotoUri,
                                isConference = snapshot.isConference,
                                modifier = Modifier.testTag("secondaryCallAvatar"),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = otherCallName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = otherStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = callParticipantStatusColor(otherState, MaterialTheme.colorScheme),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            CallElapsedTimer(elapsed, "secondaryCallTimer")
                            if (isEstablishedPair && !separateActions) actions()
                        }
                        if (separateActions) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SecondaryCallAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    horizontal: Boolean = false,
    compactEnd: Boolean = false,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interaction, enabled)
    val pressed = feedback.pressed
    val haptic = rememberCallTapHaptic()
    val color = rememberCallPressColor(if (compactEnd) tint else SecondaryCallCardStyle.actionShield(tint,
        MaterialTheme.colorScheme.surface.luminance() < .5f), pressed, enabled)
    Surface(
        onClick = rememberEnabledCallAction(enabled) {
            feedback.acknowledge()
            haptic()
            onClick()
        },
        enabled = enabled,
        modifier = modifier.heightIn(min = SecondaryCallCardStyle.ActionMinHeightDp.dp)
            .alpha(if (enabled) 1f else .5f).testTag(testTag).callPressFeedback(pressed, enabled, color.alpha),
        interactionSource = interaction,
        color = color,
        shape = if (compactEnd) CircleShape else RoundedCornerShape(24.dp),
    ) {
        if (compactEnd) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White,
                    modifier = Modifier.size(26.dp).testTag("$testTag-icon"))
            }
        } else if (horizontal) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = tint,
                    modifier = Modifier.size(22.dp).testTag("$testTag-icon"))
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp).testTag("$testTag-icon"),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}




