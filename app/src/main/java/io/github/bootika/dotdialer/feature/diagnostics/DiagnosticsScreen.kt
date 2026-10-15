package io.github.bootika.dotdialer.feature.diagnostics

import dev.goodwy.rphone.view.theme.accentText

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.Title
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import io.github.bootika.dotdialer.observability.AppTelemetry
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.DotSettingsChoiceRow
import io.github.bootika.dotdialer.observability.ReportingMode

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun DiagnosticsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = koinInject<PreferenceManager>()
    val settingsVersion by preferences.settingsChanged.collectAsStateWithLifecycle()
    val reportingMode = remember(settingsVersion) { ReportingMode.parse(preferences.getString(AppTelemetry.REPORTING_KEY, null)) }
    var sending by remember { mutableStateOf(false) }
    val loadingText = stringResource(R.string.diagnostics_loading)
    val copiedText = stringResource(R.string.diagnostics_copied)
    val copyFailedText = stringResource(R.string.diagnostics_copy_failed)
    val sentryTestFailedText = stringResource(R.string.sentry_test_failed)
    val modeSaveFailedText = stringResource(R.string.diagnostic_mode_save_failed)
    var refreshRequest by remember { mutableIntStateOf(0) }
    var presentation by remember { mutableStateOf<DiagnosticReportPresentation?>(null) }
    val report = presentation?.fullText ?: loadingText
    val clipboardFits = remember(report) { diagnosticClipboardFits(report) }
    var sharing by remember { mutableStateOf(false) }
    val shareFailedText = stringResource(R.string.diagnostics_share_failed)
    var showClearConfirmation by remember { mutableStateOf(false) }
    var sentryEventId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshRequest) {
        AppDiagnostics.revision.collect {
            val snapshot = AppDiagnostics.report(context)
            presentation = withContext(Dispatchers.Default) { prepareDiagnosticReport(snapshot) }
            // Bound live rebuilds while retaining the latest queued revision. Refresh restarts immediately.
            delay(250)
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.clear_diagnostics_title)) },
            text = { Text(stringResource(R.string.clear_diagnostics_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch {
                            AppDiagnostics.clear(context)
                            refreshRequest += 1
                        }
                    }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
                ) {
                    Text(stringResource(R.string.clear_diagnostics))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title(stringResource(R.string.diagnostics)) },
                navigationIcon = { NavigationIcon(onClick = navigator::navigateUp) },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        SelectionContainer {
            LazyColumn(state = dev.goodwy.rphone.view.screen.settings.rememberSettingsListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("diagnosticsList")
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                item(key = "controls") {
                    DisableSelection {
                        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.diagnostics_privacy_notice),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    dev.goodwy.rphone.view.screen.settings.DotSettingsNavigationRow(
                                        title = stringResource(R.string.settings_reporting),
                                        onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.ReportingSettingsScreenDestination) },
                                    )
                                    Text(
                                        text = stringResource(
                                            if (reportingMode == ReportingMode.OnDemand && AppTelemetry.isConfigured) {
                                                R.string.diagnostic_on_demand
                                            } else if (AppTelemetry.isEnabled) {
                                                R.string.sentry_connected
                                            } else {
                                                R.string.sentry_not_configured
                                            }
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                sending = true
                                                try {
                                                    sentryEventId = AppTelemetry.captureTestEvent()
                                                    if (sentryEventId == null) context.toast(sentryTestFailedText)
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    Log.e("Diagnostics", "Unable to submit manual report", error)
                                                    context.toast(sentryTestFailedText)
                                                } finally { sending = false }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = AppTelemetry.isConfigured && !sending,
                                    ) {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                                        Spacer(Modifier.padding(horizontal = 5.dp))
                                        Text(stringResource(R.string.send_sentry_test))
                                    }
                                    sentryEventId?.let { eventId ->
                                        Text(
                                            text = stringResource(R.string.sentry_test_sent, eventId),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    runCatching {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Dot Dialer diagnostics", report))
                                    }.onSuccess {
                                        context.toast(copiedText)
                                    }.onFailure {
                                        context.toast(copyFailedText)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                enabled = presentation != null && clipboardFits,
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 5.dp))
                                Text(stringResource(R.string.copy_diagnostics))
                            }

                            if (!clipboardFits && presentation != null) Text(stringResource(R.string.diagnostics_use_file),
                                style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled=presentation != null && !sharing,
                                modifier=Modifier.fillMaxWidth().testTag("diagnosticsShareFile"), onClick={
                                    sharing=true
                                    scope.launch {
                                        try {
                                            val share = withContext(Dispatchers.IO) { diagnosticFileShareIntent(context,report) }
                                            context.startActivity(android.content.Intent.createChooser(share,null))
                                        } catch(cancelled: CancellationException) { throw cancelled }
                                        catch(failure: Exception) { context.toast(shareFailedText) }
                                        finally { sharing=false }
                                    }
                                }) { Text(stringResource(R.string.diagnostics_share_file)) }

                            DiagnosticsLogActions(
                                onRefresh = { refreshRequest += 1 },
                                onClear = { showClearConfirmation = true },
                            )

                            io.github.bootika.dotdialer.diagnostics.DiagnosticLevelControl()
                            CarCaptureGuide()
                        }
                    }
                }
                val blocks = presentation?.blocks
                if (blocks == null) {
                    item(key = "loading") { DiagnosticReportBlock(loadingText, first = true, last = true) }
                } else {
                    itemsIndexed(blocks, key = { index, _ -> "report_$index" }) { index, block ->
                        DiagnosticReportBlock(block, first = index == 0, last = index == blocks.lastIndex)
                    }
                }
                item(key = "footer") { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
