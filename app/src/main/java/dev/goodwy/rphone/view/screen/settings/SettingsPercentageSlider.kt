package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.accentText
import kotlin.math.roundToInt

/** Shared appearance for droplets and keypad; only range/step/value callbacks differ. */
@Composable
internal fun SettingsPercentageSlider(
    title: String,
    label: String,
    value: Float,
    minimum: Float,
    maximum: Float,
    step: Float,
    enabled: Boolean,
    tag: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.bodyLarge
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val labelColor = if (enabled) MaterialTheme.colorScheme.accentText else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().settingsSearchTarget(title).alpha(if (enabled) 1f else 0.42f)
        .padding(horizontal = 16.dp, vertical = 14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val required = measurer.measure(title, titleStyle, softWrap = false).size.width +
                measurer.measure(label, labelStyle, softWrap = false).size.width + with(density) { 12.dp.toPx() }
            if (required > constraints.maxWidth) {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
                    Text(label, modifier = Modifier.align(Alignment.End), style = labelStyle, color = labelColor)
                }
            } else Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp), style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = labelStyle, color = labelColor)
            }
        }
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
            enabled = enabled, valueRange = minimum..maximum, steps = ((maximum - minimum) / step).roundToInt() - 1,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("${minimum.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${maximum.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
