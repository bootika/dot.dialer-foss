package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.view.theme.accentText

import android.accounts.Account
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.modal.data.Contact
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.toast
import kotlinx.coroutines.launch

@Composable
fun MoveToAccountDialog(
    title: String = stringResource(R.string.move_to_another_account),
    icon: ImageVector? = Icons.AutoMirrored.Rounded.DriveFileMove,
    availableAccounts: List<Account>,
    currentAccountKey: String? = "-1",
    onDismiss: () -> Unit,
    onAccountSelected: (Account?, isPrivate: Boolean) -> Unit
) {
    RillDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon,
        confirmButton = {
            TextButton(onClick = onDismiss, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        Surface(
            onClick = {
                if (currentAccountKey != null) {
                    onAccountSelected(null, false)
                }
            },
            shape = RoundedCornerShape(16.dp),
            color = if (currentAccountKey == device_only) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            ContactUtils.getAccountIcon(null),
                            ContactUtils.getFriendlyAccountName(null),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        ContactUtils.getFriendlyAccountName(null),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        availableAccounts.forEachIndexed { index, account ->
            val isCurrentAccount = currentAccountKey == "${account.name}|${account.type}"

            Surface(
                onClick = {
                    if (!isCurrentAccount) {
                        onAccountSelected(account, false)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = if (isCurrentAccount) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                ContactUtils.getAccountIcon(account),
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            ContactUtils.getAccountName(account),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            ContactUtils.getFriendlyAccountName(account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoveSingleContactDialog(
    contact: Contact,
    availableAccounts: List<Account>,
    currentAccountKey: String? = null,
    contactsViewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Account?, isPrivate: Boolean, newContactId: String) -> Unit = {_, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by contactsViewModel.maintenance.busy.collectAsStateWithLifecycle()
    val failure = stringResource(R.string.contact_maintenance_error)
    MoveToAccountDialog(
        availableAccounts = availableAccounts,
        currentAccountKey = currentAccountKey,
        onDismiss = onDismiss,
        onAccountSelected = { account, _ ->
            if (!busy) scope.launch {
                var newId: String? = null
                val result = contactsViewModel.maintenance.execute {
                    newId = requireNotNull(it.moveContacts(listOf(contact.id), account?.name, account?.type)[contact.id])
                }
                if (result.isSuccess) { onSuccess(account, false, requireNotNull(newId)); onDismiss() }
                else context.toast(failure)
            }
        }
    )
}

@Composable
fun MoveMultipleContactsDialog(
    contactIds: List<String>,
    availableAccounts: List<Account>,
    currentAccountKey: String? = null,
    contactsViewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Account?, isPrivate: Boolean) -> Unit = {_, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by contactsViewModel.maintenance.busy.collectAsStateWithLifecycle()
    val failure = stringResource(R.string.contact_maintenance_error)
    MoveToAccountDialog(
        availableAccounts = availableAccounts,
        currentAccountKey = currentAccountKey,
        onDismiss = onDismiss,
        onAccountSelected = { account, _ ->
            if (!busy) scope.launch {
                val result = contactsViewModel.maintenance.execute {
                    it.moveContacts(contactIds, account?.name, account?.type)
                }
                if (result.isSuccess) { onSuccess(account, false); onDismiss() }
                else context.toast(failure)
            }
        }
    )
}
