package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

internal data class DialpadKeyTextLayout(
    val digit: TextStyle,
    val letters: TextStyle,
    val fitted: Boolean = false,
)

@Composable
internal fun DialpadKeyLabels(
    number: String,
    letters: String,
    compact: Boolean,
    scaleFactor: Float,
    width: Dp,
    height: Dp,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val factor = scaleFactor.coerceIn(0.6f, 1.4f)
    val legacy = DialpadKeyTextLayout(
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = ((if (compact) 18f else 28f) * factor).sp,
            fontWeight = FontWeight.Medium,
        ),
        MaterialTheme.typography.labelSmall.copy(fontSize = (10f * factor).sp, letterSpacing = 1.sp),
    )
    val layout = remember(legacy, density, width, height, compact, factor, measurer) {
        fitDialpadKeyText(legacy, density, measurer, width, height, compact, factor)
    }
    val textModifier = if (layout.fitted) Modifier.fillMaxWidth() else Modifier
    val textAlignment = if (layout.fitted) TextAlign.Center else TextAlign.Unspecified
    Text(text = number, style = layout.digit, fontWeight = FontWeight.Medium,
        modifier = textModifier, textAlign = textAlignment)
    if (letters.isNotBlank()) {
        Text(text = letters, style = layout.letters,
            modifier = textModifier, textAlign = textAlignment,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = layout.letters.letterSpacing,
            maxLines = if (layout.fitted) 1 else Int.MAX_VALUE)
    } else if (layout.fitted) {
        // Preserve the same digit baseline as the letter-bearing keys in the fallback grid.
        val reservedHeight = measurer.measure(AnnotatedString("WXYZ"), layout.letters, maxLines = 1).size.height
        Spacer(Modifier.height(with(density) { reservedHeight.toDp() }))
    }
}

internal fun fitDialpadKeyText(
    legacy: DialpadKeyTextLayout,
    density: Density,
    measurer: TextMeasurer,
    width: Dp,
    height: Dp,
    compact: Boolean,
    factor: Float,
): DialpadKeyTextLayout {
    // An explicit compatibility guard, not a guessed clipping threshold.
    if (density.fontScale <= 1.2f) return legacy
    fun metrics(layout: DialpadKeyTextLayout): Pair<Int, Int> {
        val digits = "0123456789*#".map {
            measurer.measure(AnnotatedString(it.toString()), layout.digit, maxLines = 1).size
        }
        val subtitles = listOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "+").map {
            measurer.measure(AnnotatedString(it), layout.letters, maxLines = 1).size
        }
        return maxOf(digits.maxOf { it.width }, subtitles.maxOf { it.width }) to
            (digits.maxOf { it.height } + subtitles.maxOf { it.height })
    }
    val original = metrics(legacy)
    val fullWidth = with(density) { width.toPx() }
    val fullHeight = with(density) { height.toPx() }
    if (original.first <= fullWidth && original.second <= fullHeight) return legacy

    val availableWidth = (fullWidth - with(density) { 8.dp.toPx() }).coerceAtLeast(1f)
    val availableHeight = (fullHeight - with(density) { 4.dp.toPx() }).coerceAtLeast(1f)
    val digitPx = with(density) {
        min(legacy.digit.fontSize.toPx(), ((if (compact) 24f else 32f) * factor).dp.toPx())
    }
    val letterPx = with(density) { min(legacy.letters.fontSize.toPx(), (12f * factor).dp.toPx()) }
    val spacingPx = with(density) { min(legacy.letters.letterSpacing.toPx(), (factor).dp.toPx()) }
    fun candidate(scale: Float) = with(density) {
        DialpadKeyTextLayout(
            digit = legacy.digit.copy(fontSize = (digitPx * scale).toSp(), lineHeight = (digitPx * scale * 1.1f).toSp()),
            letters = legacy.letters.copy(fontSize = (letterPx * scale).toSp(),
                lineHeight = (letterPx * scale * 1.25f).toSp(), letterSpacing = (spacingPx * scale).toSp()),
            fitted = true,
        )
    }
    fun fits(layout: DialpadKeyTextLayout): Boolean {
        val (measuredWidth, measuredHeight) = metrics(layout)
        return measuredWidth <= availableWidth && measuredHeight <= availableHeight
    }
    val full = candidate(1f)
    if (fits(full)) return full
    var low = 0f
    var high = 1f
    repeat(12) {
        val mid = (low + high) / 2f
        if (fits(candidate(mid))) low = mid else high = mid
    }
    return candidate(low)
}
