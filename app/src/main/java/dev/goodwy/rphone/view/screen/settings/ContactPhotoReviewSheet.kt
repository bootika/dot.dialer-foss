package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.goodwy.rphone.view.components.rememberWhiteContactPhotoRequest
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactPhotoReviewSheet(item: WhatsAppAvatarReviewItem, reviews: WhatsAppAvatarReviewStore,
    processor: WhatsAppAvatarSyncProcessor, onDismiss: () -> Unit, onEmpty: () -> Unit,
    onSelect: () -> Unit) {
    var busy by remember(item.id) { mutableStateOf(false) }
    var error by remember(item.id) { mutableStateOf(false) }
    var choices by remember(item.id) { mutableStateOf<List<WhatsAppAvatarContactTarget>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val contactId = uri?.let { runCatching { android.content.ContentUris.parseId(it) }.getOrNull() }
        if (contactId != null) scope.launch {
            busy = true; error = !processor.chooseReviewTarget(item.id, contactId); busy = false
        }
    }
    val ambiguous = item.matchStrength == WhatsAppAvatarMatchStrength.Ambiguous
    LaunchedEffect(item.id) { if (ambiguous) choices = processor.reviewCandidates(item.id) }
    fun decide(accept: Boolean) {
        busy = true; error = false
        scope.launch {
            error = !runCatching { processor.resolveReview(item.id, accept, false) }.getOrDefault(false)
            busy = false
            if (reviews.items.value.isEmpty()) onEmpty()
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.headlineSmall)
            item.displayNumber?.let { Text(it) }
            Text(item.source, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoReviewImage(stringResource(R.string.whatsapp_avatar_previous), reviews.previousPreviewFile(item),
                    item.previousWidth, item.previousHeight, Modifier.weight(1f))
                PhotoReviewImage(stringResource(R.string.whatsapp_avatar_new), reviews.candidatePreviewFile(item),
                    item.candidateWidth, item.candidateHeight, Modifier.weight(1f))
            }
            if (ambiguous) {
                Text(stringResource(R.string.contact_photos_choose_contact))
                OutlinedButton(onClick = { picker.launch(null) }, enabled = !busy) {
                    Text(stringResource(R.string.contact_photos_choose_contact))
                }
                for (choice in choices) OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        error = !processor.chooseReviewTarget(item.id, choice.contactId); busy = false
                    }
                }) { Text(choice.displayName + (choice.storedNumber?.let { " — " + it } ?: "")) }
            } else {
                val smaller = item.previousWidth != null && (item.candidateWidth < item.previousWidth ||
                    item.candidateHeight < (item.previousHeight ?: 0))
                if (smaller) Text(stringResource(R.string.contact_photos_lower_resolution))
            }
            Button(enabled = !busy && reviews.isAvailablePhoto(item), onClick = onSelect) {
                Text(stringResource(R.string.contact_photos_select))
            }
            OutlinedButton(enabled = !busy, onClick = { decide(false) }) {
                Text(stringResource(if (ambiguous) R.string.contact_photos_ignore else R.string.whatsapp_avatar_keep_existing))
            }
            if (error) Text(stringResource(R.string.contact_photos_retry), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable
private fun PhotoReviewImage(label: String, file: java.io.File?, width: Int?, height: Int?, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (file != null) AsyncImage(model = rememberWhiteContactPhotoRequest(file), contentDescription = label,
            modifier = Modifier.fillMaxWidth().height(144.dp), contentScale = ContentScale.Crop)
        else Text(stringResource(R.string.whatsapp_avatar_no_photo), Modifier.height(144.dp))
        if (width != null && height != null) Text(width.toString() + " × " + height, style = MaterialTheme.typography.bodySmall)
    }
}
