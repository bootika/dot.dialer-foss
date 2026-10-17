package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*

@Composable
internal fun ContactsOptimizerPolicyControl(settings: ContactOptimizationSettings, choose: (ContactPhotoAutomaticPolicy) -> Unit) {
    val plans=rememberPlanAccessManager()
    val planState by plans.state.collectAsState()
    val allowed = remember(planState) { plans.allows(io.github.bootika.dotdialer.core.license.PlanFeature.AUTOMATIC_OPTIMIZER) }
    var expanded by remember { mutableStateOf(false) }
    val inheritedDensity = LocalDensity.current
    fun labels(policy: ContactPhotoAutomaticPolicy) = when (policy) {
        ContactPhotoAutomaticPolicy.Manual -> R.string.optimizer_manual to R.string.optimizer_manual_summary
        ContactPhotoAutomaticPolicy.FillMissing -> R.string.optimizer_missing to R.string.optimizer_missing_summary
        ContactPhotoAutomaticPolicy.Latest -> R.string.optimizer_latest to R.string.optimizer_latest_summary
        ContactPhotoAutomaticPolicy.Optimal -> R.string.optimizer_quality to R.string.optimizer_quality_summary
        ContactPhotoAutomaticPolicy.Newest -> R.string.contact_photos_auto_newest to R.string.contact_photos_auto_newest_summary
    }
    val current = labels(if(plans.allows(io.github.bootika.dotdialer.core.license.PlanFeature.AUTOMATIC_OPTIMIZER)) settings.effectivePhotoPolicy else ContactPhotoAutomaticPolicy.Manual)
    DotSettingsNavigationRow(stringResource(R.string.contact_photos_auto_policy), value = stringResource(current.first),
        supporting = stringResource(current.second), valueBelowTitle = true, contentActive = allowed, onClick = { if(plans.allows(io.github.bootika.dotdialer.core.license.PlanFeature.AUTOMATIC_OPTIMIZER)) expanded = true else plans.requestUpgrade(io.github.bootika.dotdialer.core.license.PlanFeature.AUTOMATIC_OPTIMIZER) })
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { Text(stringResource(R.string.contact_photos_auto_policy)) } },
        text = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup().testTag("optimizerPolicies"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (policy in listOf(ContactPhotoAutomaticPolicy.Manual, ContactPhotoAutomaticPolicy.FillMissing,
                ContactPhotoAutomaticPolicy.Latest, ContactPhotoAutomaticPolicy.Optimal, ContactPhotoAutomaticPolicy.Newest)) {
                val label = labels(policy)
                val selected = policy == settings.effectivePhotoPolicy
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent)
                    .testTag("optimizerPolicy-" + policy.storedValue)
                    .selectable(selected = selected, role = Role.RadioButton,
                        onClick = { choose(policy); expanded = false })
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(label.first), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(label.second), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } } }, confirmButton = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) } } })
}
