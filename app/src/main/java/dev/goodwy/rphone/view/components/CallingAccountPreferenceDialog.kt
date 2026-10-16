package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.view.theme.controlLabel
import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallingAccountInfo
import dev.goodwy.rphone.controller.util.getCallingAccounts

@Composable
fun CallingAccountPreferenceDialog(
    selectedKey: String?,
    onSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val accounts = remember { getCallingAccounts(context) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.preferred_calling_account)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
            ) {
                item {
                    CallingAccountChoiceRow(
                        account = null,
                        label = stringResource(R.string.use_app_default),
                        description = null,
                        color = MaterialTheme.colorScheme.primary,
                        selected = selectedKey == null,
                        onClick = { onSelected(null) },
                    )
                }
                items(accounts, key = CallingAccountInfo::key) { account ->
                    CallingAccountChoiceRow(
                        account = account,
                        label = account.label,
                        description = account.description,
                        color = account.highlightColor
                            .takeIf { Color(it).alpha > 0f }
                            ?.let(::Color)
                            ?: MaterialTheme.colorScheme.primary,
                        selected = selectedKey == account.key || selectedKey == account.handle.id,
                        onClick = { onSelected(account.key) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CallingAccountChoiceRow(
    account: CallingAccountInfo?,
    label: String,
    description: String?,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.large,
            color = color.copy(alpha = 0.15f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (account != null) {
                    CallingAccountIdentityIcon(
                        account = account,
                        fallbackColor = color,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.SimCard,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.controlLabel)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RadioButton(selected = selected, onClick = null)
    }
}
