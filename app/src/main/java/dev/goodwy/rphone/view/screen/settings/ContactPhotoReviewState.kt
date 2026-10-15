package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.*
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.CoroutineScope

internal class ContactReviewSelection(val reviews: WhatsAppAvatarReviewStore,val processor: WhatsAppAvatarSyncProcessor,
    val scope: CoroutineScope) {
    var selected by mutableStateOf<Set<String>>(emptySet())
    var details by mutableStateOf<WhatsAppAvatarReviewItem?>(null)
    var busy by mutableStateOf(false)
    var failure by mutableStateOf(false)
    var photoChanged by mutableStateOf(false)
    var presetsExpanded by mutableStateOf(false)
    fun choosePhoto(group: List<WhatsAppAvatarReviewItem>,item: WhatsAppAvatarReviewItem?) {
        if(item==null && group.first().rawContactId>0)
            processor.protectPhoto(group.first().accountName,group.first().rawContactId)
        failure=false;photoChanged=false
        selected=(selected-group.filter { it.kind=="photo" }.map { it.id }.toSet())+listOfNotNull(item?.id)
    }
}
internal data class ContactPhotoReviewUi(val proposals: List<WhatsAppAvatarReviewItem>,
    val candidates: List<WhatsAppAvatarReviewItem>,val source: String?,val sources: List<String>,
    val selection: ContactReviewSelection,val scanning: Boolean) {
    val groups=proposals.groupBy(::reviewGroupKey)
}
@Composable
internal fun rememberContactPhotoReview(items: List<WhatsAppAvatarReviewItem>,reviews: WhatsAppAvatarReviewStore,
    processor: WhatsAppAvatarSyncProcessor,scanning: Boolean=false): ContactPhotoReviewUi {
    val stored by reviews.allItems.collectAsState()
    val source by reviews.selectedSource.collectAsState()
    val sources by reviews.availableSources.collectAsState()
    val scope=rememberCoroutineScope()
    val selection=remember(reviews,processor,source,processor.currentAccount()) { ContactReviewSelection(reviews,processor,scope) }
    val proposals=items.contactPhotoProposals()
    val candidates=reviewSelectionCandidates(proposals,(items+stored).distinctBy { it.id })
    LaunchedEffect(candidates) { selection.selected=selection.selected.intersect(candidates.map { it.id }.toSet()) }
    return ContactPhotoReviewUi(proposals,candidates,source,sources,selection,scanning)
}
