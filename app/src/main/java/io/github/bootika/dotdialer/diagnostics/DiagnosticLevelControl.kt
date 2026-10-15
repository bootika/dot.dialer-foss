package io.github.bootika.dotdialer.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.settings.settingsSearchTarget
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.screen.settings.DotSettingsChoiceRow
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DiagnosticLevelControl(testBuild: Boolean = BuildConfig.DEBUG) {
    if (!testBuild) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = AppDiagnostics.detailSettings
    val selected by settings.level.collectAsStateWithLifecycle()
    val failed = stringResource(R.string.diagnostic_mode_save_failed)
    val choices = listOf("Normal" to 0, "Debug" to 1, "Verbose" to 2)
    val select: (Int) -> Unit = { value -> scope.launch {
        try { AppDiagnostics.selectLevel(DiagnosticLevel.entries[value]) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { context.toast(failed) }
    } }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val widest = choices.maxOf { measurer.measure(AnnotatedString(it.first), labelStyle, softWrap = false).size.width }
    val minimum = with(LocalDensity.current) { widest.toDp() } + 32.dp
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= minimum * choices.size) DotSettingsChoiceRow(
                title = stringResource(R.string.diagnostic_log_level), value = "",
                choices = choices, selectedValue = selected.ordinal, onValueChange = select,
            ) else Column(Modifier.settingsSearchTarget(stringResource(R.string.diagnostic_log_level))) {
                Text(stringResource(R.string.diagnostic_log_level), style = MaterialTheme.typography.bodyLarge)
                choices.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().selectable(selected.ordinal == value, role = Role.RadioButton,
                        onClick = { select(value) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected.ordinal == value, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Text(stringResource(R.string.diagnostic_log_level_help), style = MaterialTheme.typography.bodySmall)
    }
}
