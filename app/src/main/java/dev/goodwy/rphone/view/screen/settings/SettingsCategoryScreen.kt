package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.settings.components.SettingsHomeCard
import dev.goodwy.rphone.view.screen.settings.components.SettingsHomeListItem

@Destination<RootGraph>
@Composable
fun SettingsCategoryScreen(navigator: DestinationsNavigator, category: String) {
    // Compatibility for old saved routes; the single canonical editor is now Interface.
    if (category == "keypad") { InterfaceScreen(navigator); return }
    if (category == "effects" || category == "compatibility") { AdvancedSettingsScreen(navigator); return }
    val prefs = org.koin.compose.koinInject<dev.goodwy.rphone.controller.util.PreferenceManager>()
    val title = when (category) {
        "help" -> R.string.help_and_diagnostics
        "privacy" -> R.string.privacy_and_blocking
        "effects" -> R.string.appearance_effects
        "compatibility" -> R.string.compatibility
        else -> error("Unknown settings category: $category")
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface,
        topBar = { DotSettingsTopBar(stringResource(title), navigator::navigateUp) },
    ) { padding ->
        LazyColumn(state = rememberSettingsListState(),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsHomeCard {
                    when (category) {
                        "privacy" -> {
                            SettingsCategoryLink(R.string.manage_blocked, Icons.Rounded.Block) {
                                navigator.navigate(BlockedNumbersScreenDestination)
                            }
                            SettingsCategoryLink(R.string.authentication, Icons.Rounded.Fingerprint) {
                                navigator.navigate(BiometricScreenDestination())
                            }
                            SettingsCategoryLink(R.string.settings_reporting, Icons.Rounded.BugReport) {
                                navigator.navigate(ReportingSettingsScreenDestination)
                            }
                        }
                        "help" -> {
                            SettingsCategoryLink(R.string.diagnostics, Icons.Rounded.BugReport) {
                                navigator.navigate(DiagnosticsScreenDestination)
                            }
                            SettingsCategoryLink(R.string.plan_guide_title, Icons.Rounded.AutoAwesome) {
                                navigator.navigate(PlanGuideReplayScreenDestination)
                            }
                            SettingsCategoryLink(R.string.ui_tour_title, Icons.Rounded.TouchApp) {
                                prefs.setBoolean(dev.goodwy.rphone.controller.util.PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, false)
                                navigator.navigateUp()
                                navigator.navigateUp()
                            }
                            SettingsCategoryLink(if (dev.goodwy.rphone.BuildConfig.FLAVOR == "foss") R.string.continue_support else R.string.plans_title, Icons.Rounded.VolunteerActivism) {
                                navigator.navigate(DonateScreenDestination)
                            }
                            SettingsCategoryLink(R.string.about, Icons.Rounded.Info) { navigator.navigate(AboutAppScreenDestination) }
                        }
                        "effects" -> {
                            SettingsCategoryLink(R.string.call_effects, Icons.Rounded.Person) {
                                navigator.navigate(CallerUIScreenDestination(advanced = true))
                            }
                            SettingsCategoryLink(R.string.avatar_frame, Icons.Rounded.AccountCircle) {
                                navigator.navigate(AvatarsPreferenceScreenDestination(advanced = true))
                            }
                        }
                        "compatibility" -> {
                            SettingsCategoryLink(R.string.ignore_system_font_scale, Icons.Rounded.TextFields) {
                                navigator.navigate(InterfaceScreenDestination(advanced = true))
                            }
                            SettingsCategoryLink(R.string.call_behavior, Icons.Rounded.Sensors) {
                                navigator.navigate(CallSettingScreenDestination(section = "device"))
                            }
                            SettingsCategoryLink(R.string.lock_call_actions, Icons.Rounded.Lock) {
                                navigator.navigate(BiometricScreenDestination(callActionsOnly = true))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsCategoryLink(label: Int, icon: ImageVector, onClick: () -> Unit) {
    SettingsHomeListItem(
        headline = stringResource(label), leadingIcon = icon,
        trailingIcon = Icons.Default.ChevronRight, onClick = onClick,
    )
}
