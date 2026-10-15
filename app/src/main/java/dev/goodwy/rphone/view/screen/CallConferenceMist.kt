package dev.goodwy.rphone.view.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

internal data class CallMistSource(val id: Any, val photoUri: String?, val fallbackColor: Color)

/** Every participant contributes; preserve separate hues rather than averaging them into grey. */
internal fun conferenceMistZones(palettes: List<List<Color>>, dark: Boolean, strengthScales: List<Float> = emptyList()): List<CallMistZone> {
    if (palettes.isEmpty()) return emptyList()
    val rows = (palettes.size + 1) / 2
    val strength = (if (dark) .58f else .48f) / (palettes.size / 4f).coerceAtLeast(1f)
    return palettes.flatMapIndexed { index, colors ->
        val distinct = colors.distinct()
        val x = if (palettes.size == 1) .50f else if (index % 2 == 0) .20f else .80f
        val y = if (rows == 1) .41f else (.30f + .22f * (index / 2) / (rows - 1)).coerceIn(.30f, .52f)
        distinct.mapIndexed { colorIndex, color ->
            val offset = if (distinct.size == 1) 0f else -.10f + .20f * colorIndex / (distinct.size - 1)
            // A harmony must not cancel the participant's own identity into grey.
            // Base / counterpoint / neighbour / accent still share the same total budget.
            val share = if (distinct.size == 4) listOf(.50f, .20f, .20f, .10f)[colorIndex] else 1f / distinct.size
            CallMistZone(color, x + offset, y, .70f, .33f, strength * share * (strengthScales.getOrNull(index) ?: 1f).coerceIn(0f, 1f))
        }
    }
}

@Composable
internal fun CallConferenceMist(sources: List<CallMistSource>, middleZone: AmbientDropletMiddleZone? = null) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val palettes = sources.map { source ->
        key(source.id) {
            val palette = rememberCallAvatarPalette(source.photoUri)
            val current = remember(palette, source.fallbackColor) {
                harmonizedCallMistPalette(palette ?: analogousCallAvatarPalette(source.fallbackColor.toArgb()), source.fallbackColor.toArgb())
            }
            val upper by animateColorAsState(callMistColor(current.upper, dark), tween(600), label = "Participant upper")
            val left by animateColorAsState(callMistColor(current.left, dark), tween(600), label = "Participant left")
            val right by animateColorAsState(callMistColor(current.right, dark), tween(600), label = "Participant right")
            val lower by animateColorAsState(callMistColor(current.lower, dark), tween(600), label = "Participant lower")
            listOf(upper, left, right, lower) to if (current.usesNeutralPalette) CALL_NEUTRAL_MIST_STRENGTH else 1f
        }
    }
    CallMistCanvas(conferenceMistZones(palettes.map { it.first }, dark, palettes.map { it.second }), middleZone)
}
