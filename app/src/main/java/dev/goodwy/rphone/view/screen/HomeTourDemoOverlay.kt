package dev.goodwy.rphone.view.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.accentText
import kotlin.math.roundToInt

@Composable
internal fun HomeTourDemoOverlay(
    demo: HomeTourDemo,
    target: Rect,
    cornerRadius: Float,
    fixedProgress: Float? = null,
) {
    val density = LocalDensity.current
    val progress = fixedProgress ?: if (demo == HomeTourDemo.RowTap || demo == HomeTourDemo.AvatarLongPress) {
        val transition = rememberInfiniteTransition(label = "homeTourDemo-$demo")
        val animatedProgress by transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "homeTourDemoProgress",
        )
        animatedProgress
    } else 0f
    val outline = MaterialTheme.colorScheme.accentText
    Canvas(
        Modifier.absoluteOffset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .testTag("tour-demo"),
    ) {
        val inset = 1.dp.toPx()
        if (size.width <= 2 * inset || size.height <= 2 * inset) return@Canvas
        drawRoundRect(
            color = outline,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(inset * 2),
        )
        when (demo) {
            HomeTourDemo.RowTap -> {
                val pulseInset = (2f + progress * 2f).dp.toPx()
                if (size.width > 2 * pulseInset && size.height > 2 * pulseInset) {
                    drawRoundRect(
                        color = outline.copy(alpha = 0.44f * (1f - progress)),
                        topLeft = Offset(pulseInset, pulseInset),
                        size = Size(size.width - 2 * pulseInset, size.height - 2 * pulseInset),
                        cornerRadius = CornerRadius((cornerRadius - pulseInset).coerceAtLeast(0f)),
                        style = Stroke(inset * 2),
                    )
                }
            }
            HomeTourDemo.AvatarLongPress -> {
                if (progress in 0.35f..0.82f) {
                    val badge = Offset(size.width * 0.85f, size.height * 0.15f)
                    val radius = 7.dp.toPx()
                    drawCircle(outline, radius, badge)
                    val start = badge + Offset(-radius * 0.40f, 0f)
                    val middle = badge + Offset(-radius * 0.08f, radius * 0.30f)
                    val end = badge + Offset(radius * 0.45f, -radius * 0.32f)
                    drawLine(Color.White, start, middle, inset * 1.5f, StrokeCap.Round)
                    drawLine(Color.White, middle, end, inset * 1.5f, StrokeCap.Round)
                }
            }
            else -> Unit
        }
    }
}
