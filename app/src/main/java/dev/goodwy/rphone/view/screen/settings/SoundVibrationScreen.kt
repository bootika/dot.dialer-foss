package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

import android.content.Intent
import android.provider.Settings
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.AndroidHaptics
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.components.Title
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SoundVibrationScreen(navigator: DestinationsNavigator, advanced: Boolean = false) {
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current

    var dtmfTone by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, false)) }
    var vibrateOnAnswer by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_VIBRATE_ON_ANSWER, true)) }
    var vibrateOnHangup by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_VIBRATE_ON_HANGUP, false)) }

    var tapHapticsEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) }
    var scrollHapticsEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, false)) }

    // Haptics popup state
    var showHapticsDialog by remember { mutableStateOf(false) }
    var hapticsStrength by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_HAPTICS_STRENGTH, "light") ?: "light") }

    // ── Haptics Dialog ────────────────────────────────────────────────────────
    if (showHapticsDialog) {
        fun triggerPreviewVibration(strength: String) {
            val duration = if (strength == "strong") 80L else 40L
            val amplitude = if (strength == "strong") 255 else 80
            AndroidHaptics.performDirectPulse(context, duration, amplitude)
        }

        // Custom intensity: 0f..1f stored in prefs
        var customIntensity by remember {
            mutableFloatStateOf(prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f))
        }

        RillDialog(
            onDismissRequest = { showHapticsDialog = false },
            title = stringResource(R.string.tap_haptics),
            icon = Icons.Rounded.Vibration,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
            confirmButton = {
                TextButton(onClick = { showHapticsDialog = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.done)) }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.enable_tap_haptics), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = tapHapticsEnabled,
                        onCheckedChange = {
                            tapHapticsEnabled = it
                            prefs.setBoolean(PreferenceManager.KEY_APP_HAPTICS, it)
                        }
                    )
                }

                if (tapHapticsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                    Text(stringResource(R.string.haptics_intensity), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.accentText)

                    // Three-way segmented control: Light / Strong / Custom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(cardColor),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "light" to stringResource(R.string.haptics_soft),
                            "strong" to stringResource(R.string.haptics_strong),
                            "custom" to stringResource(R.string.haptics_custom)
                        ).forEach { (key, label) ->
                            val selected = hapticsStrength == key
                            Surface(
                                onClick = {
                                    hapticsStrength = key
                                    prefs.setString(PreferenceManager.KEY_HAPTICS_STRENGTH, key)
                                    if (key != "custom") triggerPreviewVibration(key)
                                    else {
                                        // preview with current custom intensity
                                        val dur = (10 + customIntensity * 70).toLong().coerceIn(10, 80)
                                        val amp = (40  + (customIntensity * 215)).toInt().coerceIn(40, 255)
                                        AndroidHaptics.performDirectPulse(context, dur, amp)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Custom intensity slider — only shown when "Custom" is selected
                    AnimatedVisibility(
                        visible = hapticsStrength == "custom",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        var lastVibratedSegment by remember { mutableIntStateOf(-1) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Slider(
                                value = customIntensity,
                                onValueChange = { v ->
                                    customIntensity = v
                                    prefs.setFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, v)
                                    // Vibrate every ~6% of range change for continuous multi-level feedback
                                    val segment = (v * 16).toInt()
                                    if (segment != lastVibratedSegment) {
                                        lastVibratedSegment = segment
                                        val dur = (8 + v * 55).toLong().coerceIn(8, 63)
                                        val amp = (30 + (v * 180)).toInt().coerceIn(30, 210)
                                        AndroidHaptics.performDirectPulse(context, dur, amp)
                                    }
                                },
                                onValueChangeFinished = {
                                    // Final vibration at full saved intensity
                                    val dur = (10 + customIntensity * 70).toLong().coerceIn(10, 80)
                                    val amp = (40  + (customIntensity * 215)).toInt().coerceIn(40, 255)
                                    AndroidHaptics.performDirectPulse(context, dur, amp)
                                    lastVibratedSegment = -1
                                },
                                valueRange = 0f..1f,
                                steps = 15,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (hapticsStrength == "custom") {
                                val dur = (10 + customIntensity * 70).toLong().coerceIn(10, 80)
                                val amp = (40  + (customIntensity * 215)).toInt().coerceIn(40, 255)
                                AndroidHaptics.performDirectPulse(context, dur, amp)
                            } else {
                                triggerPreviewVibration(hapticsStrength)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Rounded.Vibration, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.preview_haptic))
                    }
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "soundAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(if (advanced) R.string.settings_scroll_calibration else R.string.sound_and_vibration)) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
if (!advanced) {
            item {
                SettingsImmediateSection() {
                    RillExpressiveCard {
                        RillListItem(
                                expandText = true,
                            headline = stringResource(R.string.ringtone_settings),
                            supporting = stringResource(R.string.system_settings),
                            leadingIcon = DotDialerHandsetIcon,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                        )
//                        RillListItem(
//                            headline = "Do Not Disturb",
//                            supporting = "Manage interruption settings",
//                            leadingIcon = Icons.Outlined.DoNotDisturb,
//                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
//                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
//                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
//                        )
                    }
                }
            }
}

            // ── Dialpad ─────────────────────────────────────────────
if (!advanced) {
            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.keypad))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = stringResource(R.string.dialpad_tones),
                                supporting = stringResource(R.string.dialpad_tones_subtitle),
                                leadingIcon = Icons.Rounded.Audiotrack,
                                iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checked = dtmfTone,
                                onCheckedChange = {
                                    dtmfTone = it
                                    prefs.setBoolean(PreferenceManager.KEY_DTMF_TONE, it)
                                }
                            )
                        }
                    }
                }
            }
}

            // ── Call ─────────────────────────────────────────────
if (!advanced) {
            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.calls))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = stringResource(R.string.vibrate_on_answer),
                                supporting = stringResource(R.string.vibrate_on_answer_subtitle),
                                leadingIcon = Icons.AutoMirrored.Rounded.CallReceived,
                                iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checked = vibrateOnAnswer,
                                onCheckedChange = {
                                    vibrateOnAnswer = it
                                    prefs.setBoolean(PreferenceManager.KEY_VIBRATE_ON_ANSWER, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.vibrate_on_hang_up),
                                supporting = stringResource(R.string.vibrate_on_hang_up_subtitle),
                                leadingIcon = DotDialerHangupIcon,
                                iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checked = vibrateOnHangup,
                                onCheckedChange = {
                                    vibrateOnHangup = it
                                    prefs.setBoolean(PreferenceManager.KEY_VIBRATE_ON_HANGUP, it)
                                }
                            )
                        }
                    }
                }
            }
}

            // ── Haptics Across App ───────────────────────────────────────────
            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.haptics_across_app))
                        RillExpressiveCard {
                            val tapHapticsValue = when(hapticsStrength) {
                                "light" -> stringResource(R.string.haptics_soft)
                                "strong" -> stringResource(R.string.haptics_strong)
                                else -> (prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f) * 100)
                                    .toString().substringBefore(".") + "%"
                            }
                            val tapHapticsSubtitle = if (tapHapticsEnabled) {
                                stringResource(R.string.on) + " · " + stringResource(R.string.haptics_intensity) + ": $tapHapticsValue"
                            } else stringResource(R.string.off)
                            if (!advanced) {
RillListItem(
                                expandText = true,
                                headline   = stringResource(R.string.tap_haptics),
                                supporting = tapHapticsSubtitle,
                                leadingIcon = Icons.Rounded.Vibration,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { showHapticsDialog = true }
                            )
}

                            if (!advanced) {
RillSwitchListItem(
                                headline   = stringResource(R.string.scroll_haptics),
                                supporting = stringResource(R.string.scroll_haptics_subtitle),
                                leadingIcon = Icons.Rounded.SwipeVertical,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = scrollHapticsEnabled,
                                onCheckedChange = {
                                    scrollHapticsEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, it)
                                }
                            )
}

                            if (!advanced && scrollHapticsEnabled) {
                                DotSettingsNavigationRow(stringResource(R.string.settings_scroll_calibration),
                                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.SoundVibrationScreenDestination(advanced = true)) })
                            }
                            if (advanced && !scrollHapticsEnabled) {
                                DotSettingsNavigationRow(stringResource(R.string.scroll_haptics),
                                    supporting = stringResource(R.string.scroll_haptics_subtitle),
                                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.SoundVibrationScreenDestination()) })
                            }
                            AnimatedVisibility(visible = advanced && scrollHapticsEnabled) {
                                ScrollHapticCalibrationControls(prefs)
                            }
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}
