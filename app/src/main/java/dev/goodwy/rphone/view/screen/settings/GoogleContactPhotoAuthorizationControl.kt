package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallPhotoHydrationManager
import dev.goodwy.rphone.controller.util.GoogleContactPhotoScan
import dev.goodwy.rphone.controller.util.GoogleContactAccounts
import dev.goodwy.rphone.controller.util.GoogleContactsAuthorization
import dev.goodwy.rphone.controller.util.GoogleContactsAuthorizationResult
import dev.goodwy.rphone.controller.util.GoogleContactPhotoPreferences
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun GoogleContactPhotoSettingsControl(showScan: Boolean = true,
    selected: Boolean? = null, onSelectionChanged: (Boolean) -> Unit = {},
    requestedAccount: String? = null, embedded: Boolean = false, interactionEnabled: Boolean = true,
    authorized: Boolean? = null,
    onStateChanged: (String, Boolean) -> Unit = { _, _ -> }) {
    val prefs = koinInject<PreferenceManager>()
    val authorization = koinInject<GoogleContactsAuthorization>()
    if (!authorization.isAvailable) return
    val revision by prefs.settingsChanged.collectAsState()
    val account = remember(revision) { prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null) }
    val enabled = remember(revision) { prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false) }
    val content: @Composable () -> Unit = {
            GoogleContactPhotoAuthorizationControl(
                settingsMode = true,
                selected = selected, onSelectionChanged = onSelectionChanged,
                showScan = showScan,
                onStateChanged = onStateChanged,
                initiallyEnabled = enabled && authorized != false && (requestedAccount == null || requestedAccount == account),
                initialAccount = requestedAccount ?: account,
                requestedAccount = requestedAccount, interactionEnabled = interactionEnabled, showSourceSummary = !embedded,
            )
    }
    if (embedded) content() else Surface(color = cardColor, shape = MaterialTheme.shapes.extraLarge) {
        Column { content() }
    }
}

@Composable
internal fun GoogleContactPhotoOnboardingControl() {
    val prefs = koinInject<PreferenceManager>()
    val authorization = koinInject<GoogleContactsAuthorization>()
    if (!authorization.isAvailable) return
    GoogleContactPhotoAuthorizationControl(
        settingsMode = false,
        initiallyEnabled = prefs.getBoolean(
            GoogleContactPhotoPreferences.ENABLED,
            false,
        ),
        initialAccount = prefs.getString(
            GoogleContactPhotoPreferences.ACCOUNT,
            null,
        ),
    )
}

@Composable
private fun GoogleContactPhotoAuthorizationControl(
    settingsMode: Boolean,
    selected: Boolean? = null, onSelectionChanged: (Boolean) -> Unit = {},
    requestedAccount: String? = null, interactionEnabled: Boolean = true, showSourceSummary: Boolean = true,
    showScan: Boolean = true,
    onStateChanged: (String, Boolean) -> Unit = { _, _ -> },
    initiallyEnabled: Boolean,
    initialAccount: String?,
) {
    val prefs = koinInject<PreferenceManager>()
    val authorization = koinInject<GoogleContactsAuthorization>()
    val accounts = koinInject<GoogleContactAccounts>()
    val hydrationManager = koinInject<CallPhotoHydrationManager>()
    val photoScan = koinInject<GoogleContactPhotoScan>()
    val scope = rememberCoroutineScope()
    var enabled by remember(initiallyEnabled, initialAccount) { mutableStateOf(initiallyEnabled) }
    var accountName by remember(initialAccount) { mutableStateOf(initialAccount) }
    var pendingAccount by remember { mutableStateOf<String?>(null) }
    var accountChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun persistGranted(granted: GoogleContactsAuthorizationResult.Granted) {
        if (requestedAccount != null && granted.accountName != requestedAccount) {
            showError = true
            return
        }
        accountName = granted.accountName
        enabled = true
        prefs.setString(
            GoogleContactPhotoPreferences.ACCOUNT,
            granted.accountName,
        )
        prefs.setBoolean(GoogleContactPhotoPreferences.ENABLED, true)
        onStateChanged(granted.accountName, true)
    }

    lateinit var requestAuthorization: (String?) -> Unit
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        busy = false
        if (result.resultCode != Activity.RESULT_OK) {
            // Closing Google's consent sheet is a normal cancellation, not an error.
        } else {
            when (
                val completion = authorization.completeAuthorization(
                    result.data,
                    pendingAccount,
                )
            ) {
                is GoogleContactsAuthorizationResult.Granted -> persistGranted(completion)
                else -> showError = true
            }
        }
        pendingAccount = null
    }

    requestAuthorization = { requestedAccount ->
        busy = true
        scope.launch {
            when (val result = authorization.authorize(requestedAccount)) {
                is GoogleContactsAuthorizationResult.Granted -> {
                    persistGranted(result)
                    busy = false
                }
                is GoogleContactsAuthorizationResult.ConsentRequired -> {
                    pendingAccount = result.requestedAccountName
                    resolutionLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build(),
                    )
                }
                else -> {
                    busy = false
                    showError = true
                }
            }
        }
    }

    fun beginEnable() {
        if (busy || !interactionEnabled) return
        if (requestedAccount != null) {
            requestAuthorization(requestedAccount)
            return
        }
        busy = true
        scope.launch {
            runCatching { accounts.load() }
                .onSuccess { loadedAccounts ->
                    accountChoices = loadedAccounts
                    when (accountChoices.size) {
                        0 -> requestAuthorization(null)
                        1 -> requestAuthorization(accountChoices.single())
                        else -> {
                            busy = false
                            showAccountDialog = true
                        }
                    }
                }
                .onFailure {
                    busy = false
                    showError = true
                }
        }
    }

    if (settingsMode) {
        DotSettingsSwitchRow(
            title = stringResource(R.string.high_res_call_photos_title),
            supporting = if (enabled && accountName != null) {
                stringResource(R.string.high_res_call_photos_connected, accountName.orEmpty())
            } else if (!showSourceSummary) {
                null
            } else if (selected != null) {
                stringResource(R.string.contact_photos_google_source_summary)
            } else {
                stringResource(R.string.high_res_call_photos_supporting)
            },
            checked = selected ?: enabled,
            enabled = !busy && interactionEnabled,
            onCheckedChange = { shouldEnable ->
                if (busy || !interactionEnabled) return@DotSettingsSwitchRow
                if (selected != null) onSelectionChanged(shouldEnable)
                if (shouldEnable) {
                    beginEnable()
                } else {
                    if (!enabled) return@DotSettingsSwitchRow
                    photoScan.cancel()
                    val currentAccount = accountName
                    if (currentAccount == null) {
                        enabled = false
                        prefs.setBoolean(GoogleContactPhotoPreferences.ENABLED, false)
                    } else {
                        busy = true
                        scope.launch {
                            if (authorization.revoke(currentAccount)) {
                                hydrationManager.clearLocalState()
                                photoScan.clearProgress()
                                enabled = false
                                onStateChanged(currentAccount, false)
                                accountName = null
                                prefs.setBoolean(
                                    GoogleContactPhotoPreferences.ENABLED,
                                    false,
                                )
                                if (showScan) prefs.setString(
                                    GoogleContactPhotoPreferences.ACCOUNT,
                                    null,
                                )
                            } else {
                                showError = true
                            }
                            busy = false
                        }
                    }
                }
            },
        )
        if (selected == true && !enabled) Button(onClick = ::beginEnable, enabled = !busy && interactionEnabled,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("contactGoogleConnect")) {
            Text(stringResource(R.string.contact_photos_google_connect_action))
        }
        if (showScan) GoogleContactPhotoScanControl(enabled = enabled && !busy)
    } else if (enabled && accountName != null) {
        Text(
            text = stringResource(R.string.high_res_call_photos_ready, accountName.orEmpty()),
            modifier = Modifier.padding(top = 12.dp),
        )
    } else {
        Button(
            onClick = ::beginEnable,
            enabled = !busy,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.high_res_call_photos_enable))
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(stringResource(R.string.high_res_call_photos_choose_account)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    accountChoices.forEach { choice ->
                        Text(
                            text = choice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAccountDialog = false
                                    requestAuthorization(choice)
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            text = { Text(stringResource(R.string.high_res_call_photos_error)) },
            confirmButton = {
                TextButton(onClick = { showError = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
