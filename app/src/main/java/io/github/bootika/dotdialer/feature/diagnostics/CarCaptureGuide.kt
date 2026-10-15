package io.github.bootika.dotdialer.feature.diagnostics

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.toast

internal fun openCarCaptureDeveloperOptions(context: Context): Boolean = try {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch(failure: RuntimeException) { false }

@Composable
internal fun CarCaptureGuide(testBuild: Boolean = BuildConfig.DEBUG) {
    if(!testBuild) return
    val context = LocalContext.current
    val failure = stringResource(R.string.car_capture_settings_failed)
    Column(Modifier.fillMaxWidth().padding(top=16.dp).testTag("carCaptureGuide"), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.car_capture_title), style=MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.car_capture_steps), style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={ if(!openCarCaptureDeveloperOptions(context)) context.toast(failure) },
            modifier=Modifier.fillMaxWidth().testTag("carCaptureDeveloperOptions")) {
            Text(stringResource(R.string.car_capture_developer_options))
        }
    }
}
