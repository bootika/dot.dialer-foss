package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactGroupMembership
import kotlinx.coroutines.launch

@Composable
fun ManageContactGroupsDialog(
    contact: Contact,
    contactsViewModel: ContactsViewModel,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember(contact.id) { mutableStateOf(true) }
    var groups by remember(contact.id) { mutableStateOf<List<ContactGroupMembership>>(emptyList()) }
    var selectedIds by remember(contact.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var menuGroupId by remember { mutableStateOf<Long?>(null) }
    var editingGroup by remember { mutableStateOf<ContactGroupMembership?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    var deletingGroup by remember { mutableStateOf<ContactGroupMembership?>(null) }

    suspend fun reload() {
        groups = contactsViewModel.getContactGroups(contact.id)
        selectedIds = groups.filter(ContactGroupMembership::isMember).mapTo(mutableSetOf()) { it.groupId }
        loading = false
    }

    LaunchedEffect(contact.id) { reload() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.contact_groups), modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    editingGroup = null
                    groupTitle = ""
                    showGroupEditor = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_group))
                }
            }
        },
        text = {
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
                groups.isEmpty() -> Text(
                    text = stringResource(R.string.no_contact_groups_extended),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(groups, key = ContactGroupMembership::groupId) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (group.groupId in selectedIds) {
                                        selectedIds - group.groupId
                                    } else {
                                        selectedIds + group.groupId
                                    }
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = group.groupId in selectedIds, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.title, style = MaterialTheme.typography.bodyLarge)
                                group.accountName?.takeIf(String::isNotBlank)?.let { account ->
                                    Text(
                                        text = account,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { menuGroupId = group.groupId }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                                }
                                DropdownMenu(
                                    expanded = menuGroupId == group.groupId,
                                    onDismissRequest = { menuGroupId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rename_group)) },
                                        onClick = {
                                            menuGroupId = null
                                            editingGroup = group
                                            groupTitle = group.title
                                            showGroupEditor = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_group), color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuGroupId = null
                                            deletingGroup = group
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    contactsViewModel.setContactGroups(contact.id, selectedIds)
                    onDismissRequest()
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showGroupEditor) {
        AlertDialog(
            onDismissRequest = { showGroupEditor = false },
            title = {
                Text(stringResource(if (editingGroup == null) R.string.create_group else R.string.rename_group))
            },
            text = {
                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = groupTitle.isNotBlank(),
                    onClick = {
                        val group = editingGroup
                        showGroupEditor = false
                        loading = true
                        scope.launch {
                            if (group == null) {
                                contactsViewModel.createContactGroup(contact.id, groupTitle)
                            } else {
                                contactsViewModel.renameContactGroup(group.groupId, groupTitle)
                            }
                            reload()
                        }
                    }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showGroupEditor = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text(stringResource(R.string.delete_group)) },
            text = { Text(stringResource(R.string.delete_group_confirmation, group.title)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingGroup = null
                    loading = true
                    scope.launch {
                        contactsViewModel.deleteContactGroup(group.groupId)
                        reload()
                    }
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
