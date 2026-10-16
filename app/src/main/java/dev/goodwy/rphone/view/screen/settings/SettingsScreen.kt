package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CreateBackupDocumentContract
import dev.goodwy.rphone.controller.util.OpenBackupDocumentContract
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.exportBackupDocument
import dev.goodwy.rphone.controller.util.getAppVersion
import dev.goodwy.rphone.controller.util.importBackupDocument
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.screen.settings.components.SettingsHomeContent
import dev.goodwy.rphone.view.screen.settings.components.createSettingsSearchEntries
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(style = TabTransitionStyle::class)
@Composable
fun SettingsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val appInfo = getAppVersion(context)
    val appVersion = appInfo.first
    val storeName = when (BuildConfig.FLAVOR) {
        "gplay" -> "GPlay"
        "rustore" -> "RuStore"
        else -> "FOSS"
    }

    val listState = rememberLazyListState()
    val prefs: PreferenceManager = koinInject()
    val scope = rememberCoroutineScope()

    var backupState by remember { mutableStateOf<BackupDialogState>(BackupDialogState.Idle) }
    fun logSettingsFailure(operation: String, error: Exception) {
        Log.e("SettingsScreen", operation, error)
    }

    fun navigateBack() {
        navigator.navigateUp()
    }

    val operationFailedText = stringResource(R.string.operation_failed)

    val restoreLauncher = rememberLauncherForActivityResult(OpenBackupDocumentContract()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupState = BackupDialogState.Restoring
                try {
                    val ok = importBackupDocument(context, uri)
                    backupState = if (ok) {
                        BackupDialogState.RestoreSuccess
                    } else {
                        BackupDialogState.Error(operationFailedText)
                    }
                } catch (error: Exception) {
                    logSettingsFailure("Unable to restore an application backup", error)
                    backupState = BackupDialogState.Error(operationFailedText)
                }
            }
        }
    }

    when (val state = backupState) {
        is BackupDialogState.Restoring -> Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.restoring_backup), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is BackupDialogState.BackupSuccess -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(stringResource(R.string.backup_created)) }, text = { Text(stringResource(R.string.backup_created_description, state.path)) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.ok)) } })
        is BackupDialogState.RestoreSuccess -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(stringResource(R.string.restore_complete)) }, text = { Text(stringResource(R.string.restore_complete_description)) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.ok)) } })
        is BackupDialogState.Error -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error) }, title = { Text(stringResource(R.string.operation_failed)) }, text = { Text(state.message) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.ok)) } })
        else -> {}
    }

    val failedToCreateBackup = stringResource(R.string.failed_to_create_backup)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = CreateBackupDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupState = BackupDialogState.Restoring
                try {
                    val copied = exportBackupDocument(context, uri)
                    if (copied) {
                        backupState = BackupDialogState.BackupSuccess(uri.toString())
                    } else {
                        backupState = BackupDialogState.Error(failedToCreateBackup)
                    }
                } catch (error: Exception) {
                    logSettingsFailure("Unable to create an application backup", error)
                    backupState = BackupDialogState.Error(operationFailedText)
                }
            }
        }
    }
    fun createBackup() {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val defaultFileName = "DotDialer_Backup_$timestamp.rphone"
        createBackupLauncher.launch(defaultFileName)
    }

    fun restoreBackup() {
        restoreLauncher.launch(Unit)
    }

    // ── Search in Settings ─────────────────────────────────────────────────
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    val settingsSearchEntriesFinal = createSettingsSearchEntries(
        context = context,
        navigator = navigator,
        appVersion = appVersion,
        storeName = storeName,
        onCreateBackup = ::createBackup,
        onRestoreBackup = ::restoreBackup,
        onStartUiTour = {
            prefs.setBoolean(PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, false)
            navigateBack()
        },
    )
    val normalizeSearchDiacritics = prefs.getBoolean(
        PreferenceManager.KEY_SEARCH_DIACRITIC_NORMALIZATION,
        true,
    )
    val filteredSettingsResults = if (settingsSearchQuery.isBlank()) emptyList()
    else settingsSearchEntriesFinal.filter {
        SearchTextNormalizer.contains(it.headline, settingsSearchQuery, normalizeSearchDiacritics) ||
                SearchTextNormalizer.contains(it.supporting ?: "", settingsSearchQuery, normalizeSearchDiacritics) ||
                it.options.any { option ->
                    SearchTextNormalizer.contains(option, settingsSearchQuery, normalizeSearchDiacritics)
                }
    }

    // ── Screen ────────────────────────────────────────────────────────────────
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
                title = { Title(stringResource(R.string.settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
        SettingsHomeContent(
            listState = listState,
            scaffoldPadding = padding,
            searchQuery = settingsSearchQuery,
            onSearchQueryChange = { settingsSearchQuery = it },
            searchResults = filteredSettingsResults,
            navigator = navigator,
            appVersion = appVersion,
            storeName = storeName,
            onCreateBackup = ::createBackup,
            onRestoreBackup = ::restoreBackup,
            onStartUiTour = {
                prefs.setBoolean(PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, false)
                navigateBack()
            },
        )
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SettingsBottomPadding(padding: Dp = 36.dp) {
    Spacer(modifier = Modifier.height(padding).navigationBarsPadding())
}
