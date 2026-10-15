package dev.goodwy.rphone.view.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SetupPermissions
import dev.goodwy.rphone.controller.util.VoiceSearchContract
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@Composable
fun VoiceSearchButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoStart: Boolean = false,
    prefs: PreferenceManager = koinInject(),
) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val enabled = remember(version) { prefs.getBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED, false) }
    if (!enabled) return
    val context = LocalContext.current
    val latestResult by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    var permissionHelp by rememberSaveable { mutableStateOf(false) }
    var unavailable by rememberSaveable { mutableStateOf(false) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    val recognition = rememberLauncherForActivityResult(VoiceSearchContract()) { text ->
        busy = false
        if (text != null) scope.launch {
            // Include an Off selection already queued for persistence before accepting a late result.
            val snapshot = prefs.getPersistedPreferences()
            if (snapshot[PreferenceManager.KEY_VOICE_SEARCH_ENABLED] == true) latestResult(text)
        }
    }
    fun listen() {
        if (!prefs.getBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED, false) || busy) return
        try { busy = true; recognition.launch(Unit) }
        catch (_: ActivityNotFoundException) { busy = false; unavailable = true }
        catch (error: SecurityException) {
            android.util.Log.w("VoiceSearch", "permission: ${error.javaClass.simpleName}")
            busy = false; permissionHelp = true
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        busy = false
        if (SetupPermissions.granted(context, Manifest.permission.RECORD_AUDIO)) listen()
        else permissionHelp = true
    }
    fun start() {
        if (busy) return
        if (SetupPermissions.granted(context, Manifest.permission.RECORD_AUDIO)) listen()
        else { busy = true; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    LaunchedEffect(autoStart) {
        if (autoStart && !autoStarted) { autoStarted = true; start() }
    }
    IconButton(onClick = ::start, enabled = !busy, modifier = modifier.size(48.dp)) {
        Icon(Icons.Rounded.MicNone, contentDescription = stringResource(R.string.voice_input))
    }
    if (permissionHelp) AlertDialog(
        onDismissRequest = { permissionHelp = false },
        title = { Text(stringResource(R.string.voice_search_title)) },
        text = { Text(stringResource(R.string.voice_search_permission)) },
        confirmButton = { TextButton(onClick = {
            permissionHelp = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.open_app_settings)) } },
        dismissButton = { TextButton(onClick = { permissionHelp = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (unavailable) AlertDialog(
        onDismissRequest = { unavailable = false },
        title = { Text(stringResource(R.string.voice_search_title)) },
        text = { Text(stringResource(R.string.voice_search_unavailable)) },
        confirmButton = { TextButton(onClick = { unavailable = false }) { Text(stringResource(R.string.ok)) } },
    )
}
