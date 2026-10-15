package dev.goodwy.rphone.view.screen.settings


import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SpatialTracking
import androidx.compose.material.icons.rounded.TouchApp
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.auto.AndroidAutoIntegration
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.LocalRillNeutralSettingsIcons
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import androidx.core.net.toUri
import com.ramcosta.composedestinations.generated.destinations.QuickResponsesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallAccountsScreenDestination
import dev.goodwy.rphone.view.components.Title
import io.github.bootika.dotdialer.core.call.CallPresentationPolicy
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallSettingScreen(navigator: DestinationsNavigator, section: String = "normal") {
    if (section != "normal") { AdvancedSettingsScreen(navigator); return }
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current
    val historySettingsVersion by prefs.settingsChanged.collectAsStateWithLifecycle()
    var showAllCalls by remember(historySettingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, false))
    }

    var proximityBg by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) }
    var pocketModePrevention by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_POCKET_MODE, false)) }
    var floatingCall by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) }
    var autoSpeaker by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AUTO_SPEAKER, false)) }
    var androidAutoEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, false) &&
                AndroidAutoIntegration.isEnabled(context),
        )
    }
    var fullscreenCalls by remember {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS,
                CallPresentationPolicy.DEFAULT_ALWAYS_FULL_SCREEN,
            )
        )
    }
    var normalizeOutgoingNumbers by remember {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,
                PreferenceManager.DEFAULT_OUTGOING_NUMBER_NORMALIZATION,
            ),
        )
    }
    var outgoingRegionIso by remember {
        mutableStateOf(prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, "").orEmpty())
    }
    val automaticSelectedSim = stringResource(R.string.automatic_selected_sim)
    val countryChoices = remember(automaticSelectedSim) {
        listOf(automaticSelectedSim to "") +
            Locale.getISOCountries()
                .map { iso -> Locale.Builder().setRegion(iso).build().displayCountry to iso }
                .filter { it.first.isNotBlank() }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase(Locale.getDefault()) }
    }
    val outgoingRegionLabel = countryChoices.firstOrNull { it.second == outgoingRegionIso }?.first
        ?: automaticSelectedSim

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "callSettingsAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    CompositionLocalProvider(LocalRillNeutralSettingsIcons provides true) {
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(when (section) { "numbers" -> R.string.phone_number_formatting; "device" -> R.string.compatibility; else -> R.string.call_settings })) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(state = rememberSettingsListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Caller Accounts ───────────────────────────────────────────────
if (section == "normal") {
            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel("SIM")
                        RillExpressiveCard {
                            RillListItem(
                                expandText = true,
                                headline = stringResource(R.string.default_sim),
                                supporting = stringResource(R.string.calling_accounts_subtitle),
                                leadingIcon = Icons.Rounded.SimCard,
                                iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(CallAccountsScreenDestination) },
                            )
                        }
                    }
                }
            }
}


if (section == "numbers") {
            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.phone_number_formatting))
                        DotSettingsCard {
                            DotSettingsSwitchRow(
                                title = stringResource(R.string.add_country_code_automatically),
                                supporting = stringResource(R.string.add_country_code_automatically_subtitle),
                                checked = normalizeOutgoingNumbers,
                                onCheckedChange = {
                                    normalizeOutgoingNumbers = it
                                    prefs.setBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION, it)
                                },
                            )
                            if (normalizeOutgoingNumbers) {
                                DotSettingsDivider()
                                DotSettingsStringChoiceRow(
                                    title = stringResource(R.string.phone_number_region),
                                    value = outgoingRegionLabel,
                                    choices = countryChoices,
                                    selectedValue = outgoingRegionIso,
                                    onValueChange = {
                                        outgoingRegionIso = it
                                        prefs.setString(
                                            PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,
                                            it.takeIf(String::isNotBlank),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
}


            if (section == "normal" && AndroidAutoIntegration.isAvailable()) {
                item {
                    SettingsImmediateSection() {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.android_auto))
                            RillExpressiveCard {
                                RillSwitchListItem(
                                    headline = stringResource(R.string.android_auto_integration),
                                    supporting = stringResource(R.string.android_auto_integration_subtitle),
                                    leadingIcon = Icons.Rounded.DirectionsCar,
                                    iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    checked = androidAutoEnabled,
                                    onCheckedChange = { enabled ->
                                        AndroidAutoIntegration.setEnabled(context, enabled)
                                        prefs.setBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, enabled)
                                        androidAutoEnabled = AndroidAutoIntegration.isEnabled(context)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (section == "normal") {
                item {
                    DotSettingsCard {
                        DotSettingsNavigationRow(title = stringResource(R.string.call_screen),
                            onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination()) })
                    }
                }
            }

            // ── Call Behavior ─────────────────────────────────────────────────
            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.call_behavior))
                        RillExpressiveCard {
                            

if (section == "normal") {
RillListItem(
                                expandText = true,
                                headline = stringResource(R.string.quick_responses),
                                supporting = stringResource(R.string.quick_responses_subtitle),
                                leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_message_filled),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(QuickResponsesScreenDestination) }
                            )

}

                            if (section == "device") {
RillSwitchListItem(
                                headline   = stringResource(R.string.proximity_sensor),
                                supporting = stringResource(R.string.proximity_sensor_subtitle),
                                leadingIcon = Icons.Rounded.SpatialTracking,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = proximityBg,
                                onCheckedChange = {
                                    proximityBg = it
                                    prefs.setBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, it)
                                }
                            )
}

                            if (section == "normal") {
RillSwitchListItem(
                                headline   = stringResource(R.string.pocket_mode_prevention),
                                supporting = stringResource(R.string.pocket_mode_prevention_subtitle),
                                leadingIcon = Icons.Rounded.ScreenLockPortrait,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = pocketModePrevention,
                                onCheckedChange = {
                                    pocketModePrevention = it
                                    prefs.setBoolean(PreferenceManager.KEY_POCKET_MODE, it)
                                }
                            )
}

                            if (section == "normal") {
RillSwitchListItem(
                                headline   = stringResource(R.string.floating_ongoing_call),
                                supporting = stringResource(R.string.floating_ongoing_call_subtitle),
                                leadingIcon = Icons.Rounded.PictureInPicture,
                                iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checked = floatingCall,
                                onCheckedChange = { newValue ->
                                    if (newValue && !Settings.canDrawOverlays(context)) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                "package:${context.packageName}".toUri()
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } else {
                                        floatingCall = newValue
                                        prefs.setBoolean(PreferenceManager.KEY_FLOATING_CALL, newValue)
                                    }
                                }
                            )
}

                            if (section == "normal") {
RillSwitchListItem(
                                headline   = stringResource(R.string.fullscreen_calls),
                                supporting = stringResource(R.string.fullscreen_calls_subtitle),
                                leadingIcon = Icons.Rounded.CropFree,
                                iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checked = fullscreenCalls,
                                onCheckedChange = {
                                    fullscreenCalls = it
                                    prefs.setBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, it)
                                }
                            )
}

//                            RillSwitchListItem(
//                                headline   = stringResource(R.string.auto_speaker),
//                                supporting = stringResource(R.string.auto_speaker_subtitle),
//                                leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
//                                checked = autoSpeaker,
//                                onCheckedChange = {
//                                    autoSpeaker = it
//                                    prefs.setBoolean(PreferenceManager.KEY_AUTO_SPEAKER, it)
//                                }
//                            )
                        }
                    }
                }
            }

            
            item { SettingsBottomPadding() }
        }
    }
    }
}
