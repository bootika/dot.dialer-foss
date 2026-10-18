package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PhoneExtension
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.license.PlanFeature

@Composable
internal fun ContactExtensionFields(value: PhoneExtension, prefs: PreferenceManager, onChange: (PhoneExtension) -> Unit) {
    val revision by prefs.settingsChanged.collectAsStateWithLifecycle()
    val allowed = remember(revision) { prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY) }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 12.dp)
        .alpha(if (allowed) 1f else .45f)
        .clickable(enabled = !allowed) { prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) }) {
        OutlinedTextField(value.digits, { text -> onChange(value.copy(digits = text.filter { it in "0123456789*#" }.take(24))) },
            enabled = allowed, label = { Text(stringResource(R.string.phone_extension)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (value.digits.isNotEmpty()) OutlinedTextField(value.label, { onChange(value.copy(label = it.take(40))) },
            enabled = allowed, label = { Text(stringResource(R.string.extension_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
