package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.RecordingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
internal fun RecordingDiscoveryCard(onOpen: () -> Unit) {
    if (!BuildConfig.DICTATE_INTEGRATION_ENABLED) return
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("feature_discovery", 0) }
    var dismissed by remember(preferences) { mutableStateOf(preferences.getBoolean("recording_dismissed", false)) }
    if (dismissed) return
    val repository = remember(context) { RecordingsRepository.get(context) }
    val revision by repository.revision.collectAsState()
    val hasOpportunity by produceState(false, context, revision) {
        value = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            val oldEnough = System.currentTimeMillis() - installedAt >= TimeUnit.DAYS.toMillis(3)
            oldEnough && try {
                repository.catalog.textCounts().values.none { it.first > 0 }
            } catch (_: android.database.sqlite.SQLiteException) {
                false
            }
        }
    }
    if (!hasOpportunity) return
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary)
                Text(stringResource(R.string.feature_tip_recording_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.feature_tip_recording_body), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text(stringResource(R.string.feature_tip_see_how)) }
                TextButton(onClick = {
                    preferences.edit().putBoolean("recording_dismissed", true).apply()
                    dismissed = true
                }) { Text(stringResource(R.string.feature_tip_hide)) }
            }
        }
    }
}
