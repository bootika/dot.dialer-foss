package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Stack the selected value only when the actual text measurements cannot fit side by side. */
@Composable
internal fun SettingsChoiceSummary(title: String, value: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.bodyLarge
    val valueStyle = MaterialTheme.typography.bodyMedium
    BoxWithConstraints(modifier) {
        val required = measurer.measure(title, titleStyle, softWrap = false).size.width +
            measurer.measure(value, valueStyle, softWrap = false).size.width + with(density) { 12.dp.roundToPx() }
        if (required > constraints.maxWidth) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = titleStyle)
                if (value.isNotBlank()) Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = titleStyle, modifier = Modifier.weight(1f))
            if (value.isNotBlank()) Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
