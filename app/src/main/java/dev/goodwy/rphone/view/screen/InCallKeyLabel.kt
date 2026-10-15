package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight

/** Keep the existing digit style unless the compact key actually overflows at larger fonts. */
@Composable
internal fun InCallKeyLabel(key: Char, style: TextStyle) {
    InCallPanelLabel(key.toString(), style.copy(fontWeight = FontWeight.Medium))
}

@Composable
internal fun InCallPanelLabel(text: String, style: TextStyle) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val fitted = remember(text, style, density, constraints, measurer) {
            val size = measurer.measure(AnnotatedString(text), style, maxLines = 1).size
            val ratio = minOf(1f, constraints.maxWidth.toFloat() / size.width.coerceAtLeast(1),
                constraints.maxHeight.toFloat() / size.height.coerceAtLeast(1))
            if (ratio >= 1f) style else style.copy(fontSize = style.fontSize * ratio,
                lineHeight = style.lineHeight * ratio)
        }
        Text(text, style = fitted, maxLines = 1)
    }
}
