package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.launch

@Composable
internal fun ManualPhotoChoicesControl(account: String, revision: Int, processor: WhatsAppAvatarSyncProcessor) {
    val pinned = remember(account, revision) { runCatching { processor.hasManualPhotos(account) }.getOrDefault(false) }
    var expanded by remember(account) { mutableStateOf(false) }
    var targets by remember(account) { mutableStateOf<List<WhatsAppAvatarContactTarget>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val inheritedDensity = LocalDensity.current
    if (pinned) TextButton(modifier = Modifier.padding(horizontal = 12.dp), onClick = { expanded = true }) {
        Text(stringResource(R.string.optimizer_manual_choices))
    }
    if (expanded) {
        LaunchedEffect(account, revision) { targets = runCatching { processor.manualPhotoTargets(account) }.getOrDefault(emptyList()) }
        AlertDialog(onDismissRequest = { expanded = false }, title = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { Text(stringResource(R.string.optimizer_manual_choices)) } },
            text = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { Column(Modifier.verticalScroll(rememberScrollState())) {
                for (target in targets) Column(Modifier.padding(vertical = 8.dp)) {
                    Text(target.displayName, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { scope.launch { processor.resumeAutomaticPhoto(account, target.rawContactId) } }) {
                        Text(stringResource(R.string.optimizer_resume_automatic))
                    }
                }
            } } }, confirmButton = { CompositionLocalProvider(LocalDensity provides inheritedDensity) { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) } } })
    }
}
