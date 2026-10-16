package dev.goodwy.rphone.view.screen.settings

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import dev.goodwy.rphone.view.components.whiteContactPhotoBackground
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items

@Composable
internal fun ContactPhotoProposalList(items: List<WhatsAppAvatarReviewItem>,reviews: WhatsAppAvatarReviewStore,
    processor: WhatsAppAvatarSyncProcessor,scanning: Boolean=false) {
    val ui=rememberContactPhotoReview(items,reviews,processor,scanning)
    if(ui.proposals.isEmpty() && ui.sources.isEmpty())return
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ContactReviewHeader(ui)
        for((key,group) in ui.groups) key(group.first().accountName,key) { ContactReviewGroup(key,group,ui) }
        ContactReviewFooter(ui)
    }
    ContactReviewDetails(ui)
}

// The screen owns vertical scrolling: each contact is a keyed lazy item, never a nested list.
internal fun LazyListScope.contactPhotoReviewItems(ui: ContactPhotoReviewUi) {
    if(ui.proposals.isEmpty() && ui.sources.isEmpty())return
    item(key="contact-review-header") { ContactReviewHeader(ui) }
    items(ui.groups.entries.toList(),key={ "contact-review-${it.value.first().accountName}-${it.key}" }) {
        ContactReviewGroup(it.key,it.value,ui)
    }
    item(key="contact-review-apply") { ContactReviewFooter(ui) }
}
@Composable
private fun ContactReviewHeader(ui: ContactPhotoReviewUi) { with(ui.selection) {
    val proposals=ui.proposals;val candidates=ui.candidates;val source=ui.source;val sources=ui.sources
    val items=proposals;val scanning=ui.scanning;val inheritedDensity=LocalDensity.current
    Column(Modifier.padding(top=8.dp).testTag("contactPhotoProposals")) { DotSettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("contactReviewUtilityCard"),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(proposals.isNotEmpty()) Text(stringResource(R.string.contact_photos_proposals),
                    modifier=Modifier.testTag("contactPhotoProposalsTitle"),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.review_selected_count,selected.size),Modifier.testTag("contactReviewSelectedCount"),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ContactReviewSourceChoice(source,sources,!busy && !scanning) { chosen ->
                val account=items.firstOrNull()?.accountName ?: processor.currentAccount()
                if(account!=null) scope.launch { try { withContext(Dispatchers.IO) { reviews.selectSource(account,chosen) } }
                    catch(error:Exception) { if(error is kotlinx.coroutines.CancellationException)throw error;failure=true } }
            }
            Column(Modifier.fillMaxWidth().testTag("contactPhotoSelectionControls"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(proposals.any { it.kind=="photo" }) Box(Modifier.fillMaxWidth()) {
                    FilledTonalButton(enabled = !busy, onClick = { presetsExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("contactPhotoPresets")) {
                        Text(stringResource(R.string.contact_photos_quick_selection),Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = presetsExpanded, onDismissRequest = { presetsExpanded = false }) {
                        CompositionLocalProvider(LocalDensity provides inheritedDensity) {
                        for (preset in ContactPhotoSelectionPreset.entries) DropdownMenuItem(text = {
                            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(when (preset) {
                                    ContactPhotoSelectionPreset.Optimal -> R.string.contact_photos_selection_optimal
                                    ContactPhotoSelectionPreset.Largest -> R.string.contact_photos_selection_largest
                                    ContactPhotoSelectionPreset.LastFound -> R.string.contact_photos_selection_last_found
                                }))
                                Text(stringResource(when (preset) {
                                    ContactPhotoSelectionPreset.Optimal -> R.string.contact_photos_selection_optimal_summary
                                    ContactPhotoSelectionPreset.Largest -> R.string.contact_photos_selection_largest_summary
                                    ContactPhotoSelectionPreset.LastFound -> R.string.contact_photos_selection_last_found_summary
                                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }, onClick = {
                            val photoIds = candidates.filter { it.kind == "photo" }.map { it.id }.toSet()
                            selected = (selected - photoIds) + contactPhotoPresetSelection(proposals,preset,reviews::isAvailablePhoto)
                            failure = false; photoChanged = false
                            presetsExpanded = false
                        }, modifier = Modifier.testTag("contactPhotoPreset-" + preset.name))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = !busy && proposals.isNotEmpty(),
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("contactPhotoSelectAll"), onClick = {
                            val snapshot=proposals+candidates.filter { it.id in selected && it !in proposals }
                            val prior=selected;busy=true;failure=false;photoChanged=false
                            scope.launch { try { selected=withContext(Dispatchers.IO) { contactReviewSelectAll(snapshot,prior,reviews::isAvailablePhoto) } }
                                catch(error:Exception) { if(error is kotlinx.coroutines.CancellationException)throw error;failure=true }
                                finally { busy=false } }
                        }) { Text(stringResource(R.string.contact_photos_selection_all)) }
                    OutlinedButton(enabled = !busy && selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("contactPhotoClearPhotos"), onClick = {
                            selected = emptySet()
                            failure = false; photoChanged = false
                        }) { Text(stringResource(R.string.contact_photos_clear_photo_selection)) }
            }
        }
        }
    } }
} }
@Composable
private fun ContactReviewGroup(groupKey: String,group: List<WhatsAppAvatarReviewItem>,ui: ContactPhotoReviewUi) { with(ui.selection) {
    val key=groupKey;val candidates=ui.candidates;val inheritedDensity=LocalDensity.current
    val photos=if(group.any { it.kind=="photo" }) candidates.filter { it.kind=="photo" && reviewGroupKey(it)==key } else emptyList()
    val chosen = photos.firstOrNull { it.id in selected }
    val previousItem = group.sortedByDescending { it.createdAtMillis }
        .firstOrNull { it.previousFileName != null }
    val previousFile = previousItem?.let { reviews.previousPreviewFile(it) }
    var expanded by remember(key) { mutableStateOf(false) }
    val caption = if (chosen != null) photoSourceLabel(chosen) else
        stringResource(if (previousItem == null) R.string.whatsapp_avatar_no_photo else R.string.whatsapp_avatar_previous)
    val image = chosen?.let { reviews.candidatePreviewFile(it) } ?: previousFile
    DotSettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("contactPhotoGroup-" + key),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(group.first().displayName.ifBlank { stringResource(R.string.contact_photos_unknown_contact) },
                style = MaterialTheme.typography.titleSmall)
            group.firstNotNullOfOrNull { it.displayNumber }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (photos.isNotEmpty() || previousFile != null) {
                Box {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy && photos.isNotEmpty()) { expanded = true }
                        .testTag("contactPhotoSelect-" + key),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProposalAvatar(image, caption, Modifier.size(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text(caption)
                            photoDimensions(chosen?.candidateWidth ?: previousItem?.previousWidth,
                                chosen?.candidateHeight ?: previousItem?.previousHeight)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (photos.isNotEmpty()) Icon(Icons.Rounded.ArrowDropDown,
                            stringResource(R.string.contact_photos_select), Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CompositionLocalProvider(LocalDensity provides inheritedDensity) {
                        DropdownMenuItem(text = {
                            AvatarChoiceRow(previousFile,
                                stringResource(if (previousItem == null) R.string.whatsapp_avatar_no_photo else R.string.whatsapp_avatar_previous),
                                photoDimensions(previousItem?.previousWidth, previousItem?.previousHeight), chosen == null)
                        }, onClick = { choosePhoto(photos, null); expanded = false })
                        for (option in photos) { androidx.compose.runtime.key(option.id) {
                            val smaller = option.previousWidth != null &&
                                (option.candidateWidth < option.previousWidth ||
                                    option.candidateHeight < (option.previousHeight ?: 0))
                            val valid = reviews.isAvailablePhoto(option)
                            DropdownMenuItem(enabled = !busy && valid, text = {
                                AvatarChoiceRow(reviews.candidatePreviewFile(option), photoSourceLabel(option),
                                    photoDimensions(option.candidateWidth, option.candidateHeight), chosen?.id == option.id,
                                    if (smaller) stringResource(R.string.contact_photos_lower_resolution) else null)
                            }, onClick = { choosePhoto(photos, option); expanded = false })
                        }
                        }
                        }
                    }
                }
            }
            for (option in photos.filter { it.matchStrength == WhatsAppAvatarMatchStrength.Ambiguous }) {
                TextButton(enabled = !busy, onClick = { details = option }) {
                    Text(stringResource(R.string.contact_photos_choose_contact))
                }
            }
            if (group.first().rawContactId > 0 && processor.manualPhotoPinned(group.first().accountName, group.first().rawContactId)) {
                TextButton(enabled = !busy, onClick = { processor.resumeAutomaticPhoto(group.first().accountName, group.first().rawContactId) }) {
                    Text(stringResource(R.string.optimizer_resume_automatic))
                }
            }
            for (number in group.filter { it.kind == "number" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(modifier = Modifier.testTag("contactNumberSelect-" + number.id),
                        checked = number.id in selected, enabled = !busy && processor.canApply(number),
                        onCheckedChange = { value -> selected = if (value) selected + number.id else selected - number.id })
                    Text(number.originalNumber.orEmpty() + " → " + number.proposedNumber.orEmpty(),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }

        }
    }
} }
@Composable
private fun ContactReviewFooter(ui: ContactPhotoReviewUi) { with(ui.selection) {
    val proposals=ui.proposals;val candidates=ui.candidates
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (proposals.isNotEmpty()) Button(enabled = selected.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().testTag("contactPhotoApply"), onClick = {
                busy = true; failure = false; photoChanged = false
                val approved = candidates.filter { it.id in selected }
                scope.launch {
                    try {
                        val result=processor.resolveSelection(approved.map { it.id })
                        failure=result.failed>0;photoChanged=result.photoChanged
                    } catch(error:Exception) { if(error is kotlinx.coroutines.CancellationException)throw error;failure=true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.contact_photos_apply)) }
        if (failure) Text(stringResource(if (photoChanged) R.string.contact_photos_photo_changed else
            R.string.contact_photos_apply_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
} }
@Composable
internal fun ContactReviewDetails(ui: ContactPhotoReviewUi) { with(ui.selection) {
    val candidates=ui.candidates
    details?.let { item ->
        ContactPhotoReviewSheet(item, reviews, processor, onDismiss = { details = null }, onEmpty = { details = null },
            onSelect = { choosePhoto(candidates.filter { reviewGroupKey(it)==reviewGroupKey(item) },item); details = null })
    }
} }
@Composable
private fun AvatarChoiceRow(file: java.io.File?, source: String, dimensions: String?, selected: Boolean, reason: String? = null) {
    Row(Modifier.widthIn(min = 260.dp, max = 320.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProposalAvatar(file, source, Modifier.size(48.dp), 48.dp)
        Column(Modifier.weight(1f)) {
            Text(source, Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
            dimensions?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall) }
            reason?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall) }
        }
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
    }
}
@Composable
private fun ProposalAvatar(file: java.io.File?, description: String, modifier: Modifier,
    imageSize: androidx.compose.ui.unit.Dp = 72.dp) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { imageSize.roundToPx() }
    val request = remember(file, context, pixels) {
        file?.let { ImageRequest.Builder(context).data(it).whiteContactPhotoBackground().size(pixels).crossfade(false).build() }
    }
    if (request == null) Icon(Icons.Rounded.Person, description, modifier)
    else AsyncImage(request, description, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
}
@Composable
private fun photoSourceLabel(item: WhatsAppAvatarReviewItem): String = when (item.source) {
    "Phone number" -> stringResource(R.string.whatsapp_avatar_normalize_numbers)
    "Company logo" -> stringResource(R.string.contact_photos_business) + item.companyDomain?.let { " · $it" }.orEmpty()
    else -> item.source
}
private fun photoDimensions(width: Int?, height: Int?) =
    if (width != null && height != null && width > 0 && height > 0) width.toString() + " × " + height else null
