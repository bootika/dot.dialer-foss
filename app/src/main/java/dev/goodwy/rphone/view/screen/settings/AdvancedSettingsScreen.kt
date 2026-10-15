package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun AdvancedSettingsScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    Scaffold(containerColor = MaterialTheme.colorScheme.surface,
        topBar = { DotSettingsTopBar(stringResource(R.string.advanced_settings), navigator::navigateUp) }) { padding ->
        LazyColumn(state = rememberSettingsListState(), modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text(stringResource(R.string.advanced_settings_description),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Column {
                    SettingsSectionLabel(stringResource(R.string.compatibility))
                    DotSettingsCard { AdvancedCompatibilityControls(prefs) }
                }
            }
            item {
                Column {
                    SettingsSectionLabel(stringResource(R.string.phone_number_formatting))
                    DotSettingsCard { AdvancedNumberControls(prefs) }
                }
            }
            item {
                Column {
                    SettingsSectionLabel(stringResource(R.string.settings_fine_tuning))
                    DotSettingsCard {
                        DotSettingsNavigationRow(stringResource(R.string.customize_width),
                            onClick = { navigator.navigate(CallerUIScreenDestination(advanced = true)) })
                        DotSettingsNavigationRow(stringResource(R.string.settings_scroll_calibration),
                            onClick = { navigator.navigate(SoundVibrationScreenDestination(advanced = true)) })
                    }
                }
            }
            item {
                DotSettingsCard {
                    DotSettingsNavigationRow(stringResource(R.string.diagnostics),
                        onClick = { navigator.navigate(DiagnosticsScreenDestination) })
                }
            }
        }
    }
}
