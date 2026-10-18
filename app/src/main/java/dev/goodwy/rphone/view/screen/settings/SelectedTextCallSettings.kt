package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SelectedTextDialerIntegration

@Composable
internal fun SelectedTextCallSettings(prefs: PreferenceManager) {
    val context = LocalContext.current
    val revision by prefs.settingsChanged.collectAsStateWithLifecycle()
    val checked = remember(revision, context) {
        prefs.getBoolean(PreferenceManager.KEY_SELECTED_TEXT_DIALER, false) && SelectedTextDialerIntegration.isEnabled(context)
    }
    DotSettingsSwitchRow(stringResource(R.string.open_in_dot), stringResource(R.string.open_in_dot_summary),
        checked,
        { enabled -> if (SelectedTextDialerIntegration.setEnabled(context, enabled))
            prefs.setBoolean(PreferenceManager.KEY_SELECTED_TEXT_DIALER, enabled) })
}
