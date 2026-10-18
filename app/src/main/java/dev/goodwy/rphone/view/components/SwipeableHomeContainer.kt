package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.supportedHomeSwipeAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs

internal const val HOME_SWIPE_ACTION_WIDTH_DP = 85
internal const val HOME_SWIPE_TRIGGER_DP = 58

internal fun homeSwipeOffset(current: Float, delta: Float, maximum: Float, rightEnabled: Boolean, leftEnabled: Boolean, isRtl: Boolean): Float {
    val next = (current + delta * 0.65f).coerceIn(-maximum, maximum)
    val logical = if (isRtl) -next else next
    return if ((logical > 0f && !rightEnabled) || (logical < 0f && !leftEnabled)) 0f else next
}

/** One-action-per-direction swipe surface used by every row on the unified home screen. */
@Composable
fun SwipeableHomeContainer(
    enabled: Boolean,
    swipeRightAction: Int,
    swipeLeftAction: Int,
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rightVisual = homeSwipeVisual(swipeRightAction)
    val leftVisual = homeSwipeVisual(swipeLeftAction)
    if (!enabled || (rightVisual == null && leftVisual == null)) {
        Box(modifier = modifier) { content() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var offset by remember(swipeRightAction, swipeLeftAction, isRtl) { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val threshold = with(LocalDensity.current) { HOME_SWIPE_TRIGGER_DP.dp.toPx() }
    val maxOffset = with(LocalDensity.current) { HOME_SWIPE_ACTION_WIDTH_DP.dp.toPx() }
    val accessibilityActions = buildList {
        rightVisual?.let { visual ->
            add(CustomAccessibilityAction(visual.label) {
                onAction(swipeRightAction)
                true
            })
        }
        leftVisual?.let { visual ->
            add(CustomAccessibilityAction(visual.label) {
                onAction(swipeLeftAction)
                true
            })
        }
    }

    Box(
        modifier = modifier
            .semantics { customActions = accessibilityActions }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // Pointer deltas arrive on the UI thread already. Updating this scalar
                    // synchronously avoids launching a coroutine for every movement event.
                    offset = homeSwipeOffset(offset, delta, maxOffset, rightVisual != null, leftVisual != null, isRtl)
                },
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = {
                    settleJob = scope.launch {
                        val logicalOffset = if (isRtl) -offset else offset
                        val action = when {
                            logicalOffset >= threshold && rightVisual != null -> swipeRightAction
                            logicalOffset <= -threshold && leftVisual != null -> swipeLeftAction
                            else -> PreferenceManager.SWIPE_ACTION_NONE
                        }
                        if (action != PreferenceManager.SWIPE_ACTION_NONE) {
                            if (haptics) performAppHaptic(context, hapticsStrength, hapticsIntensity)
                            onAction(action)
                        }
                        animate(
                            initialValue = offset,
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) { value, _ -> offset = value }
                    }
                },
            ),
    ) {
        val logicalOffset = if (isRtl) -offset else offset
        Row(modifier = Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            if (logicalOffset > 0f && rightVisual != null) {
                HomeSwipeAction(rightVisual, progress = abs(logicalOffset / threshold))
            }
            Spacer(Modifier.weight(1f))
            if (logicalOffset < 0f && leftVisual != null) {
                HomeSwipeAction(leftVisual, progress = abs(logicalOffset / threshold))
            }
        }
        Box(modifier = Modifier.graphicsLayer { translationX = offset }) { content() }
    }
}

@Composable
private fun HomeSwipeAction(visual: HomeSwipeVisual, progress: Float) {
    Surface(
        modifier = Modifier
            .width(HOME_SWIPE_ACTION_WIDTH_DP.dp)
            .fillMaxHeight()
            .graphicsLayer {
                val p = progress.coerceIn(0f, 1f)
                alpha = p
            },
        shape = RectangleShape,
        color = visual.background,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = visual.icon,
                contentDescription = visual.label,
                tint = visual.foreground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

internal data class HomeSwipeVisual(
    val icon: ImageVector,
    val label: String,
    val background: Color,
    val foreground: Color,
)

@Composable
internal fun homeSwipeVisual(action: Int): HomeSwipeVisual? = when (supportedHomeSwipeAction(action)) {
    PreferenceManager.SWIPE_ACTION_CALL -> HomeSwipeVisual(
        DotDialerHandsetIcon,
        stringResource(R.string.call),
        Color(0xFF28C779),
        Color.White,
    )
    PreferenceManager.SWIPE_ACTION_MESSAGE -> HomeSwipeVisual(
        Icons.AutoMirrored.Rounded.Message,
        stringResource(R.string.sms),
        Color(0xFF3483E8),
        Color.White,
    )
    PreferenceManager.SWIPE_ACTION_CONTACT -> HomeSwipeVisual(
        Icons.Rounded.Person,
        stringResource(R.string.contact_action),
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
    )
    PreferenceManager.SWIPE_ACTION_WHATSAPP -> HomeSwipeVisual(
        ImageVector.vectorResource(R.drawable.ic_whatsapp),
        "WhatsApp",
        Color(0xFF00A86B),
        Color.White,
    )
    PreferenceManager.SWIPE_ACTION_COPY_NUMBER -> HomeSwipeVisual(
        Icons.Rounded.ContentCopy,
        stringResource(R.string.copy_action),
        Color(0xFF58655F),
        Color.White,
    )
    PreferenceManager.SWIPE_ACTION_DELETE_ENTRY -> HomeSwipeVisual(
        Icons.Rounded.Delete,
        stringResource(R.string.delete),
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.onError,
    )
    else -> null
}
