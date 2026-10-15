package io.github.bootika.dotdialer.feature.diagnostics

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R

/** Keep the existing buttons side by side only when both localized labels fit. */
@Composable
internal fun DiagnosticsLogActions(onRefresh: () -> Unit, onClear: () -> Unit) {
    val refresh = stringResource(R.string.refresh)
    val clear = stringResource(R.string.clear_diagnostics)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val textWidth = maxOf(
        measurer.measure(AnnotatedString(refresh), style, softWrap = false).size.width,
        measurer.measure(AnnotatedString(clear), style, softWrap = false).size.width,
    )
    // Icon 24dp + gap 8dp + the larger button's horizontal padding 48dp.
    val minimumButtonWidth = with(LocalDensity.current) { textWidth.toDp() } + 80.dp
    val refreshButton: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(onClick = onRefresh, modifier = modifier) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(refresh, softWrap = false)
        }
    }
    val clearButton: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = onClear, modifier = modifier, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(clear, softWrap = false)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= minimumButtonWidth * 2 + 12.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                refreshButton(Modifier.weight(1f))
                clearButton(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                refreshButton(Modifier.fillMaxWidth())
                clearButton(Modifier.fillMaxWidth())
            }
        }
    }
}
