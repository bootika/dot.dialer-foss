package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor

/** Test-only backdrop. There is no preview in the shipped Settings UI. */
@Composable
internal fun DialpadGlassTestCard(state: DialpadEffectState) {
    val backdrop = rememberLayerBackdrop()
    val accent = MaterialTheme.colorScheme.primary
    val cream = MaterialTheme.colorScheme.secondaryContainer
    Box(Modifier.fillMaxWidth().height(258.dp).clip(RoundedCornerShape(28.dp)).testTag("dialpad-qa-card")) {
        Canvas(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            drawRect(cream)
            drawCircle(accent.copy(alpha = 0.65f), size.width * 0.43f, Offset(size.width * 0.15f, size.height * 0.22f))
            drawCircle(accent.copy(alpha = 0.25f), size.width * 0.34f, Offset(size.width * 0.9f, size.height * 0.82f))
            for (i in 1..6) drawLine(accent.copy(alpha = 0.5f), Offset(0f, size.height * i / 7f),
                Offset(size.width, size.height * i / 7f), strokeWidth = 2.dp.toPx())
        }
        DialpadGlassSurface(state, Modifier.matchParentSize(), RoundedCornerShape(28.dp), backdrop) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
                    "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
                    .chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (digit, letters) ->
                                Surface(Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(42.dp), color = dialpadKeyColor) {
                                    BoxWithConstraints {
                                        val width = maxWidth; val height = maxHeight
                                        Column(Modifier.fillMaxSize(), Arrangement.Center, androidx.compose.ui.Alignment.CenterHorizontally) {
                                            DialpadKeyLabels(digit, letters, true, 1f, width, height)
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}
