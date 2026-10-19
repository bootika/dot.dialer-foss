package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.components.*
import io.github.bootika.dotdialer.feature.diagnostics.DiagnosticsScreen
import org.koin.compose.koinInject

/** Search opens the same editor as the menu, with a retained target and no automatic actions. */
@Destination<RootGraph>
@Composable
fun SettingsOptionScreen(navigator: DestinationsNavigator, optionId: String) {
    val option = settingsOptions.firstOrNull { it.id == optionId }
    val requiredFeature = option?.planFeature
    if (requiredFeature != null && planSettingLocked(requiredFeature, navigator::navigateUp)) return
    if (optionId == "T17") { SettingsOptionScreen(navigator, "T19"); return }
    if (option == null || !option.isAvailable(dev.goodwy.rphone.BuildConfig.FLAVOR,
            dev.goodwy.rphone.auto.AndroidAutoIntegration.isAvailable())) {
        AdvancedSettingsScreen(navigator)
        return
    }
    val prefs = koinInject<PreferenceManager>()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val targetId = remember(optionId, version) { settingsEffectiveFocus(optionId, prefs) }
    val target = settingsOptions.first { it.id == targetId }
    val label = stringResource(when (targetId) {
        "B02" -> if (prefs.isAuthenticationEnabled()) R.string.authentication_method else R.string.authentication
        "K01" -> R.string.calling_accounts
        else -> target.label
    })
    SettingsFocusContent(targetId, label, if (target.id == "B02" && prefs.isAuthenticationEnabled()) 1 else settingsFocusIndex(target.id, option.editor)) {
        when (option.editor) {
            SettingsEditor.RECORDINGS -> dev.goodwy.rphone.view.screen.recordings.RecordingsScreen(navigator)
            SettingsEditor.INTERFACE, SettingsEditor.KEYPAD -> InterfaceScreen(navigator)
            SettingsEditor.ACCESSIBILITY -> InterfaceScreen(navigator, accessibility = true)
            SettingsEditor.AVATARS, SettingsEditor.FRAME -> AvatarsPreferenceScreen(navigator)
            SettingsEditor.ADVANCED -> AdvancedSettingsScreen(navigator)
            SettingsEditor.FONT -> InterfaceScreen(navigator, accessibility = true)
            SettingsEditor.NUMBERS, SettingsEditor.DEVICE -> CallSettingScreen(navigator)
            SettingsEditor.HOME, SettingsEditor.HISTORY -> HomeGesturesScreen(navigator)
            SettingsEditor.CALL_UI -> CallerUIScreen(navigator)
            SettingsEditor.CALL_EFFECTS -> CallerUIScreen(navigator, advanced = true)
            SettingsEditor.ACCOUNTS -> CallAccountsScreen(navigator)
            SettingsEditor.QUICK -> QuickResponsesScreen(navigator)
            SettingsEditor.CALLS -> CallSettingScreen(navigator)
            SettingsEditor.SOUND -> SoundVibrationScreen(navigator)
            SettingsEditor.SCROLL -> SoundVibrationScreen(navigator, advanced = true)
            SettingsEditor.SOURCES -> ContactVisibilityScreen(navigator)
            SettingsEditor.CONTACTS -> if (optionId in listOf("T09", "T10", "T11", "T12", "T13", "T14", "T15", "T16", "T17", "T19"))
                ContactPhotosScreen(navigator) else ContactManagementScreen(navigator)
            SettingsEditor.BLOCKED -> BlockedNumbersScreen(navigator)
            SettingsEditor.AUTH, SettingsEditor.CALL_AUTH -> BiometricScreen(navigator)
            SettingsEditor.REPORTING -> ReportingSettingsScreen(navigator)
            SettingsEditor.DIAGNOSTICS -> DiagnosticsScreen(navigator)
            SettingsEditor.SUPPORT -> DonateScreen(navigator)
            SettingsEditor.CONTEXT, SettingsEditor.LEGACY -> error("Unavailable settings option")
        }
    }
}

internal fun settingsEffectiveFocus(id: String, prefs: PreferenceManager): String = if (
    id in listOf("T09", "T10", "T11", "T12", "T14", "T16", "T17", "T19") &&
    prefs.getString(dev.goodwy.rphone.controller.util.GoogleContactPhotoPreferences.ACCOUNT, null).isNullOrBlank()) "T15" else when (id) {
    "A09" -> if (prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD, false)) id else "A06"
    "C04" -> if (prefs.getBoolean(dev.goodwy.rphone.controller.util.AmbientDropletPreference.ENABLED_KEY, true)) id else "C03"
    "K03" -> if (prefs.getBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,
        PreferenceManager.DEFAULT_OUTGOING_NUMBER_NORMALIZATION)) id else "K02"
    "V06" -> "V05" // The custom slider is inside the explicit haptic dialog.
    "V08", "V09" -> if (prefs.getBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, false)) id else "V07"
    "B03", "B04", "B05", "B06", "B07" ->
        if (!prefs.isAuthenticationEnabled()) "B02"
        else if (id == "B04" && !prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)) "B03"
        else if ((id == "B06" || id == "B07") && !prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK, false)) "B05"
        else if (id == "B07" && prefs.getString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_MODE, "all") == "all") "B06" else id
    "T10" -> if (prefs.getBoolean(dev.goodwy.rphone.controller.util.GoogleContactPhotoPreferences.ENABLED, false)) id else "T09"
    else -> id
}

internal fun settingsFocusIndex(id: String, editor: SettingsEditor): Int = when (editor) {
    SettingsEditor.INTERFACE -> when (id) { "A06", "A09" -> 1; "I09" -> 2; else -> 0 }
    SettingsEditor.AVATARS -> 1
    SettingsEditor.HOME -> when (id) { "H09" -> 1; "H01", "H02", "H03" -> 2; "H05", "H06", "H07", "I12" -> 3; else -> 0 }
    SettingsEditor.ADVANCED -> if (id == "K02" || id == "K03") 2 else 1
    SettingsEditor.CALL_UI -> when (id) { "K08", "K09" -> 4; "C01", "C09" -> 1; "A05", "C03", "C04" -> 2; "C07" -> 3; else -> 0 }
    SettingsEditor.CALLS -> when (id) {
        "K04" -> 1
        "K02", "K03" -> if (dev.goodwy.rphone.auto.AndroidAutoIntegration.isAvailable()) 3 else 2
        "K06" -> if (dev.goodwy.rphone.auto.AndroidAutoIntegration.isAvailable()) 4 else 3
        else -> if (dev.goodwy.rphone.auto.AndroidAutoIntegration.isAvailable()) 2 else 1
    }
    SettingsEditor.CALL_EFFECTS -> if (id == "C08") 0 else 1
    SettingsEditor.ACCESSIBILITY -> if (id == "I10") 2 else 0
    SettingsEditor.ACCOUNTS -> if (id == "K10") -2 else 0
    SettingsEditor.SOUND -> when (id) { "V02" -> 1; "V03", "V04" -> 2; "V05", "V07" -> 3; else -> 0 }
    SettingsEditor.AUTH -> if (id == "B02") 0 else if (id == "B06" || id == "B07") 2 else 1
    SettingsEditor.CONTACTS -> when (id) { "T02", "T03" -> 1; "T11", "T12", "T13", "T14", "T16", "T17", "T19" -> 1; "T15" -> 0; "T09", "T10" -> 2; else -> 0 }
    else -> 0
}
