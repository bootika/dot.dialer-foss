package dev.goodwy.rphone.view.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.callPressScale
import dev.goodwy.rphone.view.theme.CallActionColors
import dev.goodwy.rphone.view.theme.CallControlFeedback
import kotlin.math.roundToInt

internal const val HOME_TOUR_SEARCH_SAMPLE = "George"
internal const val HOME_TOUR_POINTER_SIZE_DP = 52

@Composable
internal fun HomeTourDemoOverlay(demo: HomeTourDemo, target: Rect, fixedProgress: Float? = null) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "homeTourDemo-$demo")
    val animatedProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "homeTourDemoProgress",
    )
    val progress = fixedProgress ?: animatedProgress
    val width = with(density) { target.width.toDp() }
    val height = with(density) { target.height.toDp() }
    val pointerPx = with(density) { HOME_TOUR_POINTER_SIZE_DP.dp.toPx() }
    Box(
        Modifier.offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .width(width).height(height).testTag("tour-demo"),
        contentAlignment = Alignment.Center,
    ) {
        if (demo == HomeTourDemo.SearchTyping) {
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(HOME_TOUR_SEARCH_SAMPLE.take(tourTypedCharacterCount(progress, HOME_TOUR_SEARCH_SAMPLE.length)),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            if (demo == HomeTourDemo.DialpadTap) {
                // Cover the square FAB only in the demonstration, so the opaque circular
                // pointer has a clean silhouette instead of merging with the keypad icon.
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.background) {}
            }
            val pressed = progress in 0.18f..0.42f
            val amount = if (pressed) CallControlFeedback.PressedScale else 1f
            val travel = minOf(target.width * 0.16f, ((target.width - pointerPx) / 2f).coerceAtLeast(0f))
            val translation = if (demo == HomeTourDemo.RowSwipe) tourSwipeOffset(progress) * travel else 0f
            if (demo == HomeTourDemo.RowSwipe) {
                Surface(Modifier.fillMaxSize().alpha(0.18f),
                    shape = RoundedCornerShape(24.dp), color = CallActionColors.answerCommitted) {}
            }
            if (demo == HomeTourDemo.AvatarLongPress && progress in 0.35f..0.82f) {
                Surface(Modifier.size(minOf(width, height)), shape = CircleShape,
                    color = CallActionColors.answerCommitted) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = CallActionColors.onAction, modifier = Modifier.size(28.dp))
                    }
                }
            }
            // The pointer never fades or changes symbol: only a 5% visual press.
            Surface(
                Modifier.size(HOME_TOUR_POINTER_SIZE_DP.dp)
                    .graphicsLayer {
                        translationX = translation
                        // Keep the selection check visible beside the common pointer.
                        if (demo == HomeTourDemo.AvatarLongPress) {
                            translationX += pointerPx * 0.55f
                            translationY = pointerPx * 0.35f
                        }
                    }.scale(amount)
                    .testTag("tour-pointer")
                    .semantics { callPressScale = amount },
                shape = CircleShape,
                color = CallActionColors.answerCommitted,
                contentColor = CallActionColors.onAction,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}
