package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.ContactSource
import dev.goodwy.rphone.modal.data.RawContactInfo

/** Selects an editable Android raw contact without routing users through linked app profiles. */
internal fun selectPreferredEditableRawContact(
    sources: List<RawContactInfo>,
    preferredAccountName: String?,
    preferredAccountType: String?,
): RawContactInfo? {
    val writableSources = sources.filterNot(RawContactInfo::isReadOnly)

    return writableSources.firstOrNull { source ->
        source.accountName == preferredAccountName && source.accountType == preferredAccountType
    } ?: writableSources.firstOrNull { source ->
        source.accountName.isNullOrBlank() && source.accountType.isNullOrBlank()
    } ?: writableSources.firstOrNull()
}

internal fun contactSources(
    rawContacts: List<RawContactInfo>,
): List<ContactSource> = rawContacts.mapIndexed { index, raw ->
    ContactSource(
        accountName = raw.accountName?.takeIf(String::isNotBlank),
        accountType = raw.accountType?.takeIf(String::isNotBlank),
        isPrimary = index == 0,
        rawContactId = raw.rawContactId,
    )
}
