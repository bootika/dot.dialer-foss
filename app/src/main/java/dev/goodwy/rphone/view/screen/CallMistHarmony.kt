package dev.goodwy.rphone.view.screen

import android.graphics.Color
import kotlin.math.abs
import androidx.compose.ui.graphics.toArgb
import dev.goodwy.rphone.view.components.modernPastelAvatarColors
import kotlin.random.Random

internal data class CallMistPalette(val upper: Int, val left: Int, val right: Int, val lower: Int, val usesNeutralPalette: Boolean = false) {
    val colors: List<Int> get() = listOf(upper, left, right, lower)
}

internal const val CALL_NEUTRAL_MIST_STRENGTH = .85f

private fun mistHsv(color: Int): FloatArray = FloatArray(3).also { Color.colorToHSV(color, it) }
private fun hueDistance(a: Float, b: Float): Float = abs(a - b).let { minOf(it, 360f - it) }
private fun isCoolMistColor(color: Int): Boolean = mistHsv(color).let { it[1] >= .10f && it[0] in 155f..280f }

/** Stable pseudo-random choices reuse Thunderbird's actual avatar palette without duplicating it. */
internal fun neutralAvatarMistPalette(seed: Int): CallMistPalette {
    val available = modernPastelAvatarColors.map { it.first.toArgb() }
        .filter { mistHsv(it)[1] >= .28f }.shuffled(Random(seed))
    // Random within hue families keeps the field balanced, not four adjacent warm colours.
    val upper = available.first { mistHsv(it)[0] in 90f..190f }
    val left = available.first { it != upper && isCoolMistColor(it) }
    val right = available.first { it != upper && it != left && mistHsv(it)[0] !in 90f..280f }
    val chosen = listOf(upper, left, right)
    val lower = available.first { candidate -> candidate !in chosen && chosen.all {
        hueDistance(mistHsv(candidate)[0], mistHsv(it)[0]) >= 20f
    } }
    return CallMistPalette(upper, left, right, lower, usesNeutralPalette = true)
}

/** Harmony is relative to the actual identity/photo colour, never to a contact name or sample. */
internal fun harmonizedCallMistPalette(source: CallAvatarPalette, neutralSeed: Int = source.upper): CallMistPalette {
    val base = source.upper
    val original = listOf(source.upper, source.left, source.right)
    // Keep the photo/circle untouched; only the artwork borrows avatar palette colours.
    if (original.all { mistHsv(it)[1] < .10f }) return neutralAvatarMistPalette(neutralSeed)
    val chromatic = original.filter { mistHsv(it)[1] >= .10f }
    val hsv = mistHsv(if (base in chromatic) base else chromatic.first())
    fun neighbour(offset: Float): Int = Color.HSVToColor(
        floatArrayOf((hsv[0] + offset + 360f) % 360f, hsv[1], hsv[2]),
    )
    fun coolCounterpoint(): Int {
        val complement = (hsv[0] + 180f) % 360f
        // Split complements cover pink/purple as well as red/orange; choose the nearest
        // cold candidate instead of always adding the same turquoise to every caller.
        val hue = listOf(150f, 180f, 210f).map { (hsv[0] + it) % 360f }
            .filter { it in 155f..280f }.minByOrNull { hueDistance(it, complement) }
            ?: if (hueDistance(155f, complement) <= hueDistance(280f, complement)) 155f else 280f
        return Color.HSVToColor(floatArrayOf(hue, hsv[1].coerceIn(.28f, .48f), hsv[2]))
    }
    val cold = listOf(source.left, source.upper, source.right).firstOrNull(::isCoolMistColor)
    val left = when {
        cold == null -> coolCounterpoint()
        cold != base -> cold
        else -> listOf(-28f, 28f).map(::neighbour).firstOrNull(::isCoolMistColor) ?: cold
    }
    val right = source.right.takeUnless { it == base || it == left }
        ?: listOf(28f, -28f, 56f, -56f).map(::neighbour).firstOrNull { it != base && it != left } ?: base
    val used = listOf(base, left, right)
    val lower = when {
        chromatic.all(::isCoolMistColor) -> neighbour(180f)
        source.left !in used -> source.left
        else -> listOf(-28f, 28f, -56f, 56f, 180f).map(::neighbour).firstOrNull { candidate ->
            val hue = mistHsv(candidate)[0]
            used.all { hueDistance(hue, mistHsv(it)[0]) >= 12f }
        } ?: base
    }
    return CallMistPalette(base, left, right, lower)
}
