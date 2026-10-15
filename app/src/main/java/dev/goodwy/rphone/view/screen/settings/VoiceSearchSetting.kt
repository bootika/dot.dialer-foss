package dev.goodwy.rphone.view.screen.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SetupPermissions

@Composable
internal fun VoiceSearchSetting(prefs: PreferenceManager) {
    val context = LocalContext.current
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val enabled = remember(version) { prefs.getBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED, false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    fun enableIfGranted() {
        if (pendingEnable && SetupPermissions.granted(context, Manifest.permission.RECORD_AUDIO)) {
            prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED, true)
            help = false
            pendingEnable = false
        } else help = pendingEnable
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enableIfGranted() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { enableIfGranted() }
    DotSettingsSwitchRow(
        title = stringResource(R.string.voice_search_title),
        supporting = stringResource(R.string.voice_search_description),
        checked = enabled,
        onCheckedChange = { on ->
            if (!on) { pendingEnable = false; prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED, false) }
            else { pendingEnable = true
                if (SetupPermissions.granted(context, Manifest.permission.RECORD_AUDIO)) enableIfGranted()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )
    if (help) AlertDialog(
        onDismissRequest = { help = false; pendingEnable = false },
        title = { Text(stringResource(R.string.voice_search_title)) },
        text = { Text(stringResource(R.string.voice_search_permission)) },
        confirmButton = { TextButton(onClick = {
            settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.open_app_settings)) } },
        dismissButton = { TextButton(onClick = { help = false; pendingEnable = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
