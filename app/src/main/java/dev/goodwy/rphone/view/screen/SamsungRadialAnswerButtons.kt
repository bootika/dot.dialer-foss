package dev.goodwy.rphone.view.screen

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.CallActionColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Samsung-inspired radial controls with progressive, threshold-aligned visual feedback. */
@Composable
fun SamsungRadialAnswerButtons(
    answerOnStart: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val actionRowDirection = if (answerOnStart) LayoutDirection.Rtl else LayoutDirection.Ltr

    @Composable
    fun RadialCallAction(
        label: String,
        icon: ImageVector,
        color: Color,
        answer: Boolean,
        controlDiameter: Dp,
        onTriggered: () -> Unit,
    ) {
        val trackDiameter = controlDiameter
        val handleDiameter = controlDiameter / 2
        val maxRadiusPx = with(density) { (trackDiameter / 2).toPx() }
        val triggerRadiusPx = maxRadiusPx * SAMSUNG_RADIAL_TRIGGER_FRACTION
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var thresholdHapticSent by remember { mutableStateOf(false) }
        var completingGesture by remember { mutableStateOf(false) }
        val distance = sqrt(dragOffset.x * dragOffset.x + dragOffset.y * dragOffset.y)
        val dragProgress = if (maxRadiusPx > 0f) distance / maxRadiusPx else 0f
        val visualProgress = incomingActionFeedbackProgress(
            dragProgress,
            SAMSUNG_RADIAL_TRIGGER_FRACTION,
        )
        val trackBorderAlpha by animateFloatAsState(
            targetValue = SAMSUNG_RADIAL_IDLE_BORDER_ALPHA +
                (SAMSUNG_RADIAL_DRAG_BORDER_ALPHA - SAMSUNG_RADIAL_IDLE_BORDER_ALPHA) * visualProgress,
            animationSpec = tween(durationMillis = 80),
            label = "radialTrackBorderAlpha",
        )
        val trackFillAlpha by animateFloatAsState(
            targetValue = SAMSUNG_RADIAL_IDLE_FILL_ALPHA +
                (SAMSUNG_RADIAL_DRAG_FILL_ALPHA - SAMSUNG_RADIAL_IDLE_FILL_ALPHA) * visualProgress,
            animationSpec = tween(durationMillis = 80),
            label = "radialTrackFillAlpha",
        )
        val motionSpec: AnimationSpec<Float> = if (completingGesture) {
            tween(
                durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                easing = FastOutSlowInEasing,
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        }
        val animatedX by animateFloatAsState(
            targetValue = dragOffset.x,
            animationSpec = motionSpec,
            label = "radialCallX",
        )
        val animatedY by animateFloatAsState(
            targetValue = dragOffset.y,
            animationSpec = motionSpec,
            label = "radialCallY",
        )
        val handleColor = incomingActionSurfaceColor(
            color,
            dragProgress,
            SAMSUNG_RADIAL_TRIGGER_FRACTION,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(controlDiameter)
                    .semantics {
                        role = Role.Button
                        onClick {
                            performIncomingCommitHaptic(view, answer)
                            onTriggered()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(trackDiameter)
                        .border(1.dp, color.copy(alpha = trackBorderAlpha), CircleShape)
                        .background(color.copy(alpha = trackFillAlpha), CircleShape),
                )
                Surface(
                    modifier = Modifier
                        .size(handleDiameter)
                        .graphicsLayer {
                            translationX = animatedX
                            translationY = animatedY
                        }
                        .pointerInput(onTriggered, maxRadiusPx) {
                            detectDragGestures(
                                onDragStart = {
                                    dragOffset = Offset.Zero
                                    thresholdHapticSent = false
                                    completingGesture = false
                                },
                                onDragCancel = {
                                    dragOffset = Offset.Zero
                                    thresholdHapticSent = false
                                    completingGesture = false
                                },
                                onDragEnd = {
                                    val finalDistance = sqrt(
                                        dragOffset.x * dragOffset.x + dragOffset.y * dragOffset.y,
                                    )
                                    if (finalDistance >= triggerRadiusPx) {
                                        val completionDirection = if (finalDistance > 0f) {
                                            dragOffset * (maxRadiusPx / finalDistance)
                                        } else {
                                            Offset.Zero
                                        }
                                        completingGesture = true
                                        dragOffset = completionDirection
                                        scope.launch {
                                            delay(INCOMING_ACTION_COMPLETION_DURATION_MS.toLong())
                                            performIncomingCommitHaptic(view, answer)
                                            onTriggered()
                                            dragOffset = Offset.Zero
                                            completingGesture = false
                                        }
                                    } else {
                                        dragOffset = Offset.Zero
                                        completingGesture = false
                                    }
                                    thresholdHapticSent = false
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val candidate = dragOffset + amount
                                    val candidateDistance = sqrt(
                                        candidate.x * candidate.x + candidate.y * candidate.y,
                                    )
                                    dragOffset = if (candidateDistance > maxRadiusPx && candidateDistance > 0f) {
                                        candidate * (maxRadiusPx / candidateDistance)
                                    } else {
                                        candidate
                                    }
                                    if (!thresholdHapticSent && candidateDistance >= triggerRadiusPx) {
                                        thresholdHapticSent = true
                                        performIncomingThresholdHaptic(view)
                                    } else if (thresholdHapticSent && candidateDistance < triggerRadiusPx) {
                                        thresholdHapticSent = false
                                    }
                                },
                            )
                        },
                    shape = CircleShape,
                    color = handleColor,
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = CallActionColors.onAction,
                            modifier = Modifier.size(callActionIconSize(icon)),
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides actionRowDirection) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val controlDiameter = samsungRadialControlDiameterDp(maxWidth.value).dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = SAMSUNG_RADIAL_ROW_HORIZONTAL_PADDING_DP.dp,
                        vertical = 24.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadialCallAction(
                    label = stringResource(R.string.decline),
                    icon = DotDialerHangupIcon,
                    color = color_call_end,
                    answer = false,
                    controlDiameter = controlDiameter,
                    onTriggered = onDecline,
                )
                RadialCallAction(
                    label = stringResource(R.string.answer),
                    icon = DotDialerHandsetIcon,
                    color = color_call_button,
                    answer = true,
                    controlDiameter = controlDiameter,
                    onTriggered = onAnswer,
                )
            }
        }
    }
}
