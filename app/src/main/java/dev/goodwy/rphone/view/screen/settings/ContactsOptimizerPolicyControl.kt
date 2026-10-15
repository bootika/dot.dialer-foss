package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*

@Composable
internal fun ContactsOptimizerPolicyControl(settings: ContactOptimizationSettings, choose: (ContactPhotoAutomaticPolicy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val inheritedDensity = LocalDensity.current
    fun labels(policy: ContactPhotoAutomaticPolicy) = when (policy) {
        ContactPhotoAutomaticPolicy.Manual -> R.string.optimizer_manual to R.string.optimizer_manual_summary
        ContactPhotoAutomaticPolicy.FillMissing -> R.string.optimizer_missing to R.string.optimizer_missing_summary
        ContactPhotoAutomaticPolicy.Latest -> R.string.optimizer_latest to R.string.optimizer_latest_summary
        ContactPhotoAutomaticPolicy.Optimal -> R.string.optimizer_quality to R.string.optimizer_quality_summary
        ContactPhotoAutomaticPolicy.Newest -> R.string.contact_photos_auto_newest to R.string.contact_photos_auto_newest_summary
    }
    val current = labels(settings.effectivePhotoPolicy)
    DotSettingsNavigationRow(stringResource(R.string.contact_photos_auto_policy), value = stringResource(current.first),
        supporting = stringResource(current.second), onClick = { expanded = true })
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { Text(stringResource(R.string.contact_photos_auto_policy)) } },
        text = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { Column(Modifier.verticalScroll(rememberScrollState()).testTag("optimizerPolicies")) {
            for (policy in listOf(ContactPhotoAutomaticPolicy.Manual, ContactPhotoAutomaticPolicy.FillMissing,
                ContactPhotoAutomaticPolicy.Latest, ContactPhotoAutomaticPolicy.Optimal, ContactPhotoAutomaticPolicy.Newest)) {
                val label = labels(policy)
                TextButton(modifier = Modifier.fillMaxWidth().testTag("optimizerPolicy-" + policy.storedValue)
                    .semantics { selected = policy == settings.effectivePhotoPolicy },
                    onClick = { choose(policy); expanded = false }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stringResource(label.first), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(label.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } } }, confirmButton = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) } } })
}
