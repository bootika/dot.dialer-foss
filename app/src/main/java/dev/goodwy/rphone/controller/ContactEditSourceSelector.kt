package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.repository.ContactsRepository

/** Selects an editable Android raw contact without routing users through linked app profiles. */
internal fun selectPreferredEditableRawContact(
    sources: List<ContactsRepository.RawContactInfo>,
    preferredAccountName: String?,
    preferredAccountType: String?,
): ContactsRepository.RawContactInfo? {
    val writableSources = sources.filterNot(ContactsRepository.RawContactInfo::isReadOnly)

    return writableSources.firstOrNull { source ->
        source.accountName == preferredAccountName && source.accountType == preferredAccountType
    } ?: writableSources.firstOrNull { source ->
        source.accountName.isNullOrBlank() && source.accountType.isNullOrBlank()
    } ?: writableSources.firstOrNull()
}

internal fun contactSources(
    rawContacts: List<ContactsRepository.RawContactInfo>,
): List<ContactsRepository.ContactSource> = rawContacts.mapIndexed { index, raw ->
    ContactsRepository.ContactSource(
        accountName = raw.accountName?.takeIf(String::isNotBlank),
        accountType = raw.accountType?.takeIf(String::isNotBlank),
        isPrimary = index == 0,
        rawContactId = raw.rawContactId,
    )
}
