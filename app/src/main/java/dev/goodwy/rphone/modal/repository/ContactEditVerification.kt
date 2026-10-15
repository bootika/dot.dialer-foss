package dev.goodwy.rphone.modal.repository

import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail

/** Editor rows retain exact numbers/types/labels; the aggregate phoneNumbers list may be deduplicated. */
internal fun Contact.editablePhoneDetails(): List<ContactPhoneDetail> = phoneDetails.ifEmpty {
    if (isPrivate) emptyList() else phoneNumbers.map { ContactPhoneDetail(number = it) }
}

/** Compare only fields owned by the editor, not provider-generated identity/photo URIs. */
internal fun requireContactEditRoundTrip(expected: Contact, saved: Contact) {
    fun fields(contact: Contact): List<Any?> = with(contact) {
        listOf(namePrefix, givenName, middleName, familyName, nameSuffix, nickname, company, jobTitle,
            if (isPrivate) phoneNumbers.toSet() else null, editablePhoneDetails().toSet(),
            emails.toSet(), addresses.toSet(), events.toSet(),
            notes.orEmpty(), isFavorite)
    }
    check(fields(expected) == fields(saved)) { "Contact fields were not fully saved" }
}
