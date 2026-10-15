package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R

@Composable
internal fun ContactPhotoSizeCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val title = stringResource(R.string.contact_photos_only_same_or_larger)
    Row(Modifier.fillMaxWidth().settingsSearchTarget(title)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.contact_photos_only_same_or_larger_summary),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
