package dev.goodwy.rphone.view.screen.settings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.toast
import io.github.bootika.dotdialer.observability.AppTelemetry
import io.github.bootika.dotdialer.observability.ReportingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun ReportingSettingsScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val mode = remember(version) { ReportingMode.parse(prefs.getString(AppTelemetry.REPORTING_KEY, null)) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val failure = stringResource(R.string.diagnostic_mode_save_failed)
    Scaffold(containerColor = MaterialTheme.colorScheme.surface,
        topBar = { DotSettingsTopBar(stringResource(R.string.settings_reporting), navigator::navigateUp) }) { padding ->
        LazyColumn(state = rememberSettingsListState(), modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)) {
            item {
                DotSettingsCard {
                    DotSettingsChoiceRow(stringResource(R.string.settings_reporting), "",
                        listOf(stringResource(R.string.diagnostic_automatic) to 0,
                            stringResource(R.string.diagnostic_on_demand) to 1),
                        if (mode == ReportingMode.Automatic) 0 else 1,
                        { selected -> scope.launch {
                            try { AppTelemetry.setReportingMode(prefs,
                                if (selected == 0) ReportingMode.Automatic else ReportingMode.OnDemand) }
                            catch (error: CancellationException) { throw error }
                            catch (error: Exception) {
                                Log.e("ReportingSettings", "Unable to save reporting mode", error)
                                context.toast(failure)
                            }
                        } })
                    Text(stringResource(R.string.diagnostic_reporting_explanation), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall)
                    if (BuildConfig.FLAVOR == "gplay") Text(stringResource(R.string.product_stats_description),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
