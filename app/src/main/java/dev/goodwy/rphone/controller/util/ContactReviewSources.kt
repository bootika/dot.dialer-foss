package dev.goodwy.rphone.controller.util

internal object ContactReviewSources {
    val keys=listOf("numbers","whatsapp","gravatar","business","google")
    fun of(item: WhatsAppAvatarReviewItem): String? = when {
        item.kind=="number" -> "numbers"
        item.kind!="photo" -> null
        item.source in setOf("WhatsApp","WhatsApp Business") -> "whatsapp"
        item.source=="Gravatar" -> "gravatar"
        item.source=="Company logo" -> "business"
        item.source=="Google" -> "google"
        else -> null
    }
}
internal fun reviewGroupKey(item: WhatsAppAvatarReviewItem): String =
    if(item.contactId>0) "${item.contactId}:${item.rawContactId}" else
        item.matchedNumber?.let { "phone:$it" } ?: item.id

internal fun reviewSelectionCandidates(visible: List<WhatsAppAvatarReviewItem>, all: List<WhatsAppAvatarReviewItem>): List<WhatsAppAvatarReviewItem> {
    val photoGroups=visible.filter { it.kind=="photo" }.map { it.accountName to reviewGroupKey(it) }.toSet()
    return (visible+all.filter { it.kind=="photo" && (it.accountName to reviewGroupKey(it)) in photoGroups }).distinctBy { it.id }
}
internal data class ContactReviewApplySummary(val attempted: Int,val failed: Int,val photoChanged: Boolean)
