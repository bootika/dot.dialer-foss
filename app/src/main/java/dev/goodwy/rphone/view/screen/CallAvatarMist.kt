package dev.goodwy.rphone.view.screen

import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import dev.goodwy.rphone.view.components.callPhotoRevision
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Colours belong to the current photo; no contact-specific or sample-image palette. */
internal data class CallAvatarPalette(val upper: Int, val left: Int, val right: Int)

internal fun extractCallAvatarPalette(pixels: IntArray): CallAvatarPalette? {
    data class Sample(val color: Int, val hue: Float, val saturation: Float, val weight: Double)
    val samples = mutableListOf<Sample>()
    val hsv = FloatArray(3)
    for (pixel in pixels) {
        if (AndroidColor.alpha(pixel) < 128) continue
        AndroidColor.colorToHSV(pixel, hsv)
        // Ignore black clothing and blown-out windows, not the quieter coloured background.
        if (hsv[2] < .16f || (hsv[2] > .94f && hsv[1] < .12f)) continue
        samples += Sample(pixel, hsv[0], hsv[1], (.25 + hsv[1]) * hsv[2])
    }
    if (samples.isEmpty()) {
        val opaque = pixels.filter { AndroidColor.alpha(it) >= 128 }
        if (opaque.isEmpty() || opaque.any { AndroidColor.colorToHSV(it, hsv); hsv[1] >= .10f }) return null
        val grey = AndroidColor.rgb(
            opaque.map(AndroidColor::red).average().roundToInt(),
            opaque.map(AndroidColor::green).average().roundToInt(),
            opaque.map(AndroidColor::blue).average().roundToInt(),
        )
        return CallAvatarPalette(grey, grey, grey)
    }
    fun average(group: List<Sample>): Int {
        val weight = group.sumOf { it.weight }
        fun channel(get: (Int) -> Int) = (group.sumOf { get(it.color) * it.weight } / weight).roundToInt()
        return AndroidColor.rgb(channel(AndroidColor::red), channel(AndroidColor::green), channel(AndroidColor::blue))
    }
    // Integrate each photographed hue family instead of letting a quantization-bin
    // winner change abruptly after a resize. Tiny coloured noise is not a palette.
    val totalWeight = samples.sumOf { it.weight }
    val dominant = average(samples.groupBy { (it.hue / 30f).toInt() to (it.saturation >= .10f) }
        .values.maxBy { group -> group.sumOf { it.weight } })
    fun pick(range: ClosedFloatingPointRange<Float>, fallback: Int): Int {
        val group = samples.filter { it.saturation >= .10f && it.hue in range }
        return if (group.size >= minOf(3, samples.size) && group.sumOf { it.weight } >= totalWeight * .005) average(group) else fallback
    }
    // Prefer the photographed gold/amber background over red-brown skin and hair;
    // retain red when that is the only warm hue actually present in the photo.
    val warm = pick(25f..75f, pick(0f..75f, dominant))
    val cool = pick(155f..280f, dominant)
    val upper = pick(45f..155f, dominant)
    return CallAvatarPalette(upper, cool, warm)
}

internal fun analogousCallAvatarPalette(color: Int): CallAvatarPalette {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color, hsv)
    fun neighbour(offset: Float): Int = AndroidColor.HSVToColor(
        floatArrayOf((hsv[0] + offset + 360f) % 360f, hsv[1], hsv[2]),
    )
    return CallAvatarPalette(color, neighbour(-28f), neighbour(28f))
}

internal fun callMistColor(color: Int, dark: Boolean): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color, hsv)
    // Cool tints lose chroma when mixed with the warm ivory and upper field. Restore
    // their visibility without making the already-visible warm field more orange.
    // Greys and almost-neutral photos stay neutral; hue still comes from the photo.
    hsv[1] = when {
        dark -> hsv[1].coerceAtMost(.40f)
        hsv[0] in 155f..280f && hsv[1] >= .10f -> (hsv[1] * 1.8f).coerceIn(.40f, .60f)
        else -> hsv[1].coerceAtMost(.32f)
    }
    // Pastel is bright chroma plus transparency, not a grey veil over the photograph.
    hsv[2] = if (dark) hsv[2].coerceIn(.60f, .66f) else hsv[2].coerceIn(.95f, .98f)
    return Color(AndroidColor.HSVToColor(hsv))
}

internal fun callMistSurface(themeSurface: Color): Color =
    if (themeSurface.luminance() < .5f) Color(0xFF141817) else Color(0xFFFFFDF7)

@Composable
internal fun rememberCallAvatarPalette(photoUri: String?): CallAvatarPalette? {
    val context = LocalContext.current
    val revision = dev.goodwy.rphone.view.components.LocalCallPhotoRevision.current
    var palette by remember(photoUri, revision) { mutableStateOf<CallAvatarPalette?>(null) }
    LaunchedEffect(photoUri, context, revision) {
        if (photoUri.isNullOrBlank()) return@LaunchedEffect
        try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(photoUri).callPhotoRevision(photoUri, revision).size(64).allowHardware(false).build(),
            )
            if (result is SuccessResult) {
                palette = withContext(Dispatchers.Default) {
                    val bitmap = result.drawable.toBitmap(64, 64)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    extractCallAvatarPalette(pixels)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A malformed/unavailable provider photo must retain the matching circle colour.
            Log.w("CallAvatarMist", "Unable to load avatar palette; using circle colour (${error.javaClass.simpleName})")
            palette = null
        }
    }
    return palette
}

@Composable
internal fun CallAvatarMist(photoUri: String?, fallbackColor: Color, middleZone: AmbientDropletMiddleZone? = null) {
    val palette = rememberCallAvatarPalette(photoUri)
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val current = remember(palette, fallbackColor) {
        harmonizedCallMistPalette(palette ?: analogousCallAvatarPalette(fallbackColor.toArgb()), fallbackColor.toArgb())
    }
    val upper by animateColorAsState(callMistColor(current.upper, dark), tween(600), label = "Upper mist")
    val left by animateColorAsState(callMistColor(current.left, dark), tween(600), label = "Left mist")
    val right by animateColorAsState(callMistColor(current.right, dark), tween(600), label = "Right mist")
    val lower by animateColorAsState(callMistColor(current.lower, dark), tween(600), label = "Lower mist")
    val accentScale = if (current.usesNeutralPalette) CALL_NEUTRAL_MIST_STRENGTH else 1f
    val strength = (if (dark) .58f else .48f) * accentScale
    CallMistCanvas(listOf(
        CallMistZone(upper, .36f, .29f, .78f, .37f, strength * .88f),
        CallMistZone(left, .16f, .46f, .64f, .34f, if (dark) strength else .64f * accentScale),
        CallMistZone(right, .83f, .46f, .67f, .32f, strength * .94f),
        CallMistZone(lower, .53f, .58f, .66f, .27f, strength * .30f),
    ), middleZone)
}

internal data class CallMistZone(val tint: Color, val x: Float, val y: Float, val width: Float, val height: Float, val alpha: Float)

/** Shared long transparent tails keep the hang-up and navigation regions quiet. */
@Composable
internal fun CallMistCanvas(zones: List<CallMistZone>, middleZone: AmbientDropletMiddleZone? = null) {
    var canvasTopInRoot by remember { mutableFloatStateOf(0f) }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { canvasTopInRoot = it.positionInRoot().y }) {
        for (original in zones) {
            val zone = callMistZoneInMiddle(original, middleZone, canvasTopInRoot, size.height)
            val center = Offset(size.width * zone.x, size.height * zone.y)
            val radius = size.width * zone.width
            scale(1f, size.height * zone.height / radius, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to zone.tint.copy(alpha = zone.alpha),
                        .32f to zone.tint.copy(alpha = zone.alpha * .78f),
                        .64f to zone.tint.copy(alpha = zone.alpha * .30f),
                        1f to zone.tint.copy(alpha = 0f),
                        center = center, radius = radius,
                    ),
                    radius = radius, center = center,
                )
            }
        }
    }
}
