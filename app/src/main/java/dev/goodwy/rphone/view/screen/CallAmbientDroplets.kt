package dev.goodwy.rphone.view.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager

internal const val CALL_AMBIENT_HORIZONTAL_TRAVEL_DP = 100f
internal const val CALL_AMBIENT_VERTICAL_TRAVEL_DP = -150f
internal const val CALL_AMBIENT_ALPHA_WITH_AVATAR = 0.40f
internal const val CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_DARK = 0.18f
internal const val CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_LIGHT = 0.11f
internal val CALL_AMBIENT_BLEND_MODE_WITH_AVATAR: BlendMode = BlendMode.Overlay
internal val CALL_AMBIENT_BLEND_MODE_WITHOUT_AVATAR: BlendMode = BlendMode.SrcOver
internal val CALL_AMBIENT_COMPOSITING_STRATEGY: CompositingStrategy = CompositingStrategy.Offscreen

internal data class AmbientDropletRenderConfig(
    val enabled: Boolean = AmbientDropletPreference.DEFAULT_ENABLED,
    val brightnessPercent: Int = AmbientDropletPreference.DEFAULT_BRIGHTNESS,
)

internal fun PreferenceManager.ambientDropletRenderConfig() = AmbientDropletRenderConfig(
    enabled = getBoolean(
        AmbientDropletPreference.ENABLED_KEY,
        AmbientDropletPreference.DEFAULT_ENABLED,
    ),
    brightnessPercent = getInt(
        AmbientDropletPreference.BRIGHTNESS_KEY,
        AmbientDropletPreference.DEFAULT_BRIGHTNESS,
    ).coerceIn(
        AmbientDropletPreference.MIN_BRIGHTNESS,
        AmbientDropletPreference.MAX_BRIGHTNESS,
    ),
)

private data class AmbientDrop(val x: Float, val y: Float)

internal data class AmbientDropletMiddleZone(val topInRootPx: Float, val bottomInRootPx: Float)

/** Keep text zones 30% quieter, with a smooth feather back to the central artwork. */
internal fun ambientDropReadabilityScale(
    centerY: Float,
    radius: Float,
    middleTop: Float,
    middleBottom: Float,
    featherPx: Float,
): Float {
    if (!middleTop.isFinite() || !middleBottom.isFinite() || middleBottom <= middleTop) return 0.70f
    val distance = minOf(centerY - middleTop, middleBottom - centerY) - radius
    val progress = (distance / featherPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return 0.70f + 0.30f * progress * progress * (3f - 2f * progress)
}

private val callAmbientDrops = listOf(
    AmbientDrop(.08f, .14f),
    AmbientDrop(.24f, .10f),
    AmbientDrop(.76f, .13f),
    AmbientDrop(.91f, .20f),
    AmbientDrop(.11f, .29f),
    AmbientDrop(.89f, .32f),
    AmbientDrop(.07f, .42f),
    AmbientDrop(.93f, .45f),
    AmbientDrop(.13f, .55f),
    AmbientDrop(.85f, .57f),
    AmbientDrop(.06f, .67f),
    AmbientDrop(.94f, .69f),
    AmbientDrop(.15f, .82f),
    AmbientDrop(.42f, .89f),
    AmbientDrop(.72f, .84f),
    AmbientDrop(.92f, .91f),
    AmbientDrop(.36f, .18f),
    AmbientDrop(.58f, .24f),
    AmbientDrop(.31f, .38f),
    AmbientDrop(.65f, .46f),
)

internal data class AmbientDropAnimationTiming(
    val horizontalDurationMillis: Int,
    val verticalDurationMillis: Int,
    val alphaDurationMillis: Int,
)

internal fun ambientDropAnimationTiming(index: Int): AmbientDropAnimationTiming {
    val realDialerIndex = index % 10
    return AmbientDropAnimationTiming(
        horizontalDurationMillis = 10_000 + realDialerIndex * 1_000,
        verticalDurationMillis = 12_000 + realDialerIndex * 1_200,
        alphaDurationMillis = 5_000 + realDialerIndex * 500,
    )
}

internal fun ambientDropDiameterDp(index: Int): Int = 10 + index % 20

internal data class AmbientDropDepthSpec(
    val depth: Float,
    val diameterScale: Float,
    val alphaScale: Float,
    val edgeClarity: Float,
    val solidCoreStop: Float,
    val featherStop: Float,
    val gradientRadiusScale: Float,
)

/** Stable pseudo-random depth keeps recomposition from changing the particle field. */
internal fun ambientDropDepthSpec(index: Int): AmbientDropDepthSpec {
    val depth = ((index * 73 + 19) % 101) / 100f
    val clarityVariation = ((index * 37 + 11) % 97) / 96f
    val edgeClarity = 0.35f + depth * 0.35f + clarityVariation * 0.10f
    return AmbientDropDepthSpec(
        depth = depth,
        diameterScale = 0.74f + depth * 0.52f,
        alphaScale = 1f,
        edgeClarity = edgeClarity,
        solidCoreStop = 0.20f + edgeClarity * 0.30f,
        featherStop = 0.70f + edgeClarity * 0.15f,
        gradientRadiusScale = 1.22f - edgeClarity * 0.12f,
    )
}

internal data class AmbientDropStartOffsets(
    val horizontalMillis: Int,
    val verticalMillis: Int,
    val alphaMillis: Int,
)

internal fun ambientDropStartOffsets(
    index: Int,
    timing: AmbientDropAnimationTiming = ambientDropAnimationTiming(index),
): AmbientDropStartOffsets {
    val seed = index + 1
    return AmbientDropStartOffsets(
        horizontalMillis = seed * 1_379 % (timing.horizontalDurationMillis * 2),
        verticalMillis = seed * 2_137 % (timing.verticalDurationMillis * 2),
        alphaMillis = seed * 977 % (timing.alphaDurationMillis * 2),
    )
}

internal data class AmbientDropVisualSpec(
    val minimumDropAlpha: Float,
    val maximumDropAlpha: Float,
    val blendMode: BlendMode,
    val dropColor: Color,
)

internal fun ambientDropVisualSpec(
    hasAvatar: Boolean,
    isDarkTheme: Boolean,
    onSurfaceColor: Color,
    brightnessPercent: Int = 100,
): AmbientDropVisualSpec {
    val baseAlpha = when {
        hasAvatar -> CALL_AMBIENT_ALPHA_WITH_AVATAR
        isDarkTheme -> CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_DARK
        else -> CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_LIGHT
    }
    val alpha = baseAlpha * ambientDropBrightnessFactor(brightnessPercent)
    return AmbientDropVisualSpec(
        minimumDropAlpha = alpha,
        maximumDropAlpha = alpha,
        blendMode = if (hasAvatar) CALL_AMBIENT_BLEND_MODE_WITH_AVATAR
        else CALL_AMBIENT_BLEND_MODE_WITHOUT_AVATAR,
        dropColor = if (hasAvatar || isDarkTheme) Color.White else onSurfaceColor,
    )
}

internal fun ambientDropBrightnessFactor(brightnessPercent: Int): Float =
    brightnessPercent.coerceIn(AmbientDropletPreference.MIN_BRIGHTNESS,
        AmbientDropletPreference.MAX_BRIGHTNESS) / 100f

private data class AmbientDropMotion(
    val xOffsetDp: State<Float>,
    val yOffsetDp: State<Float>,
    val alpha: State<Float>,
)

@Composable
internal fun CallAmbientDroplets(
    modifier: Modifier = Modifier,
    hasAvatar: Boolean,
    brightnessPercent: Int = 100,
    middleZone: AmbientDropletMiddleZone? = null,
) {
    var canvasTopInRoot by remember { mutableFloatStateOf(0f) }
    val scheme = MaterialTheme.colorScheme
    val visualSpec = ambientDropVisualSpec(
        hasAvatar = hasAvatar,
        isDarkTheme = scheme.surface.luminance() < 0.5f,
        onSurfaceColor = scheme.onSurface,
        brightnessPercent = brightnessPercent,
    )
    val transition = rememberInfiniteTransition(label = "callAmbientDrops")
    val motions = callAmbientDrops.mapIndexed { index, _ ->
        val timing = ambientDropAnimationTiming(index)
        val startOffsets = ambientDropStartOffsets(index, timing)
        AmbientDropMotion(
            xOffsetDp = transition.animateFloat(
                initialValue = 0f,
                targetValue = CALL_AMBIENT_HORIZONTAL_TRAVEL_DP,
                animationSpec = infiniteRepeatable(
                    animation = tween(timing.horizontalDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(
                        startOffsets.horizontalMillis,
                        StartOffsetType.FastForward,
                    ),
                ),
                label = "callAmbientDropX_$index",
            ),
            yOffsetDp = transition.animateFloat(
                initialValue = 0f,
                targetValue = CALL_AMBIENT_VERTICAL_TRAVEL_DP,
                animationSpec = infiniteRepeatable(
                    animation = tween(timing.verticalDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(
                        startOffsets.verticalMillis,
                        StartOffsetType.FastForward,
                    ),
                ),
                label = "callAmbientDropY_$index",
            ),
            alpha = transition.animateFloat(
                initialValue = visualSpec.minimumDropAlpha,
                targetValue = visualSpec.maximumDropAlpha,
                animationSpec = infiniteRepeatable(
                    animation = tween(timing.alphaDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(
                        startOffsets.alphaMillis,
                        StartOffsetType.FastForward,
                    ),
                ),
                label = "callAmbientDropAlpha_$index",
            ),
        )
    }

    Canvas(modifier.fillMaxSize().onGloballyPositioned {
        canvasTopInRoot = it.positionInRoot().y
    }) {
        callAmbientDrops.forEachIndexed { index, drop ->
            val motion = motions[index]
            val depthSpec = ambientDropDepthSpec(index)
            val center = Offset(
                x = size.width * drop.x + motion.xOffsetDp.value.dp.toPx(),
                y = size.height * drop.y + motion.yOffsetDp.value.dp.toPx(),
            )
            val radius = ambientDropDiameterDp(index).dp.toPx() / 2f * depthSpec.diameterScale
            val alpha = motion.alpha.value * depthSpec.alphaScale * ambientDropReadabilityScale(
                centerY = center.y,
                radius = radius * depthSpec.gradientRadiusScale,
                middleTop = (middleZone?.topInRootPx ?: Float.NaN) - canvasTopInRoot,
                middleBottom = (middleZone?.bottomInRootPx ?: Float.NaN) - canvasTopInRoot,
                featherPx = 32.dp.toPx(),
            )
            if (depthSpec.edgeClarity >= 0.999f) {
                drawCircle(
                    color = visualSpec.dropColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    blendMode = visualSpec.blendMode,
                )
            } else {
                val gradientRadius = radius * depthSpec.gradientRadiusScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to visualSpec.dropColor.copy(alpha = alpha),
                            depthSpec.solidCoreStop to visualSpec.dropColor.copy(alpha = alpha * 0.98f),
                            depthSpec.featherStop to visualSpec.dropColor.copy(alpha = alpha * 0.62f),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = gradientRadius,
                    ),
                    radius = gradientRadius,
                    center = center,
                    blendMode = visualSpec.blendMode,
                )
            }
        }
    }
}
