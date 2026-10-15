package dev.goodwy.rphone.controller.util

internal enum class ContactPhotoSelectionPreset { Optimal, Largest, LastFound }

/** Selection only; provider eligibility is supplied by the existing Apply validation. */
internal fun contactPhotoPresetSelection(items: List<WhatsAppAvatarReviewItem>,
    preset: ContactPhotoSelectionPreset, eligible: (WhatsAppAvatarReviewItem) -> Boolean): Set<String> {
    val groups = items.filter { it.kind == "photo" }.groupBy {
        it.accountName to reviewGroupKey(it)
    }
    val largest = compareBy<WhatsAppAvatarReviewItem> { it.candidateWidth.toLong() * it.candidateHeight }
        .thenBy { minOf(it.candidateWidth, it.candidateHeight) }.thenBy { it.createdAtMillis }
    return groups.values.mapNotNull { group ->
        val current = group.filter { it.previousFileName != null }.maxByOrNull { it.createdAtMillis }
        val width = current?.previousWidth ?: 0
        val height = current?.previousHeight ?: 0
        if (preset == ContactPhotoSelectionPreset.Optimal && current != null &&
            (width <= 0 || height <= 0 || (width >= 512 && height >= 512)))
            return@mapNotNull null
        val candidates = group.filter { item ->
            item.candidateWidth > 0 && item.candidateHeight > 0 &&
                (item.previousWidth == null || (item.candidateWidth >= item.previousWidth &&
                    item.candidateHeight >= (item.previousHeight ?: 0))) && eligible(item) &&
                (preset != ContactPhotoSelectionPreset.Optimal || current == null ||
                    (item.candidateWidth >= width && item.candidateHeight >= height &&
                        item.candidateWidth.toLong() * item.candidateHeight > width.toLong() * height))
        }
        val comparator = if (preset == ContactPhotoSelectionPreset.LastFound)
            compareBy<WhatsAppAvatarReviewItem> { it.createdAtMillis }.then(largest) else largest
        candidates.maxWithOrNull(comparator)?.id
    }.toSet()
}

/** Keep explicit choices and fill unselected contacts; number selections are handled by the caller. */
internal fun contactPhotoSelectAll(items: List<WhatsAppAvatarReviewItem>, selected: Set<String>,
    eligible: (WhatsAppAvatarReviewItem) -> Boolean): Set<String> {
    val largest = contactPhotoPresetSelection(items, ContactPhotoSelectionPreset.Largest, eligible)
    return items.filter { it.kind == "photo" }.groupBy {
        it.accountName to reviewGroupKey(it)
    }.values.mapNotNull { group ->
        group.firstOrNull { it.id in selected && it.candidateWidth > 0 && it.candidateHeight > 0 && eligible(it) }?.id
            ?: group.firstOrNull { it.id in largest }?.id
    }.toSet()
}


internal fun contactReviewSelectAll(items: List<WhatsAppAvatarReviewItem>,selected: Set<String>,
    available: (WhatsAppAvatarReviewItem) -> Boolean): Set<String> =
    contactPhotoSelectAll(items,selected,available) + items.filter { it.kind=="number" }.map { it.id }
