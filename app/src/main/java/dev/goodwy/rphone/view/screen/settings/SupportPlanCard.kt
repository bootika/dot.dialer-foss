package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.performAppHaptic
import org.koin.compose.koinInject

internal data class SupportPlan(
    val id: String,
    val title: String,
    val description: String,
    val price: String,
    val label: String,
    val subscription: Boolean,
    val owned: Boolean = false,
    val available: Boolean = true,
    val productId: String = id,
)

@Composable
internal fun SupportPlanCard(plan: SupportPlan, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("support-plan-" + plan.id)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = {
                if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                    performAppHaptic(context,
                        prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                        prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f))
                }
                onSelect()
            }),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceBright,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(1.5.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                RadioButton(selected = selected, onClick = null, enabled = enabled)
            }
            if (selected && plan.available) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.primary.copy(alpha = 0.10f)) {
                    Text(stringResource(R.string.support_selected),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(plan.description, style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
            Text(
                if (plan.owned) stringResource(R.string.support_purchased) else plan.price,
                style = if (plan.available && !plan.owned) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}
