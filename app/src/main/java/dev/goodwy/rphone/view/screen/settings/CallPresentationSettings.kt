package dev.goodwy.rphone.view.screen.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.call.CallPresentationPolicy

@Composable
internal fun CallPresentationSettings(prefs: PreferenceManager) {
    val context = LocalContext.current
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val floating = remember(version) { prefs.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false) }
    DotSettingsSwitchRow(stringResource(R.string.floating_ongoing_call),
        stringResource(R.string.floating_ongoing_call_subtitle), floating, { enabled ->
            if (enabled && !Settings.canDrawOverlays(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else prefs.setBoolean(PreferenceManager.KEY_FLOATING_CALL, enabled)
        })
    DotSettingsDivider()
    SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS,
        CallPresentationPolicy.DEFAULT_ALWAYS_FULL_SCREEN, R.string.fullscreen_calls,
        stringResource(R.string.fullscreen_calls_subtitle))
}
