package dev.goodwy.rphone.view.screen.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.auto.AndroidAutoIntegration
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun CallSettingScreen(navigator: DestinationsNavigator, section: String = "normal") {
    // Old number/device routes share this canonical editor without changing a preference.
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    var autoEnabled by remember(version) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, false) && AndroidAutoIntegration.isEnabled(context))
    }
    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(350), label = "callSettingsAlpha")
    LaunchedEffect(Unit) { visible = true }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface,
        topBar = { DotSettingsTopBar(stringResource(R.string.call_behavior), navigator::navigateUp) }) { padding ->
        LazyColumn(state = rememberSettingsListState(), modifier = Modifier.fillMaxSize().padding(padding).alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                SettingsSectionLabel("SIM")
                DotSettingsCard { DotSettingsNavigationRow(stringResource(R.string.default_sim),
                    supporting = stringResource(R.string.calling_accounts_subtitle),
                    onClick = { navigator.navigate(CallAccountsScreenDestination) }) }
            }
            if (AndroidAutoIntegration.isAvailable()) {
                item {
                    SettingsSectionLabel(stringResource(R.string.android_auto))
                    DotSettingsCard { DotSettingsSwitchRow(stringResource(R.string.android_auto_integration),
                        stringResource(R.string.android_auto_integration_subtitle), autoEnabled, { enabled ->
                            AndroidAutoIntegration.setEnabled(context, enabled)
                            prefs.setBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, enabled)
                            autoEnabled = AndroidAutoIntegration.isEnabled(context)
                        }) }
                }
            }
            item {
                DotSettingsCard {
                    DotSettingsNavigationRow(stringResource(R.string.quick_responses),
                        supporting = stringResource(R.string.quick_responses_subtitle),
                        onClick = { navigator.navigate(QuickResponsesScreenDestination) })
                    DotSettingsDivider()
                    SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_POCKET_MODE, false,
                        R.string.pocket_mode_prevention, stringResource(R.string.pocket_mode_prevention_subtitle))
                }
            }
            item {
                SettingsSectionLabel(stringResource(R.string.advanced_settings))
                SettingsSectionLabel(stringResource(R.string.phone_number_formatting))
                DotSettingsCard { AdvancedNumberControls(prefs) }
            }
            item {
                SettingsSectionLabel(stringResource(R.string.compatibility))
                DotSettingsCard { SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_PROXIMITY_SENSOR, true,
                    R.string.proximity_sensor, stringResource(R.string.proximity_sensor_subtitle)) }
            }
            item { SettingsBottomPadding() }
        }
    }
}
