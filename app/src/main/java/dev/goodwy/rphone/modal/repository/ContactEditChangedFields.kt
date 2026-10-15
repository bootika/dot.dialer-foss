package dev.goodwy.rphone.modal.repository

import android.provider.ContactsContract.CommonDataKinds as Kinds
import dev.goodwy.rphone.modal.data.Contact

/** Do not rewrite unrelated provider rows when the user changes only one field family. */
internal fun changedContactEditMimeTypes(before: Contact, draft: Contact): Set<String> = buildSet {
    fun names(value: Contact) = with(value) {
        listOf(namePrefix, givenName, middleName, familyName, nameSuffix, displayName)
    }
    if (names(before) != names(draft)) add(Kinds.StructuredName.CONTENT_ITEM_TYPE)
    if (before.nickname != draft.nickname) add(Kinds.Nickname.CONTENT_ITEM_TYPE)
    if (before.company != draft.company || before.jobTitle != draft.jobTitle) add(Kinds.Organization.CONTENT_ITEM_TYPE)
    if (before.editablePhoneDetails().toSet() != draft.editablePhoneDetails().toSet()) add(Kinds.Phone.CONTENT_ITEM_TYPE)
    if (before.emails.toSet() != draft.emails.toSet()) add(Kinds.Email.CONTENT_ITEM_TYPE)
    if (before.addresses.toSet() != draft.addresses.toSet()) add(Kinds.StructuredPostal.CONTENT_ITEM_TYPE)
    if (before.events.toSet() != draft.events.toSet()) add(Kinds.Event.CONTENT_ITEM_TYPE)
    if (before.notes != draft.notes) add(Kinds.Note.CONTENT_ITEM_TYPE)
    if (before.photoUri != draft.photoUri) add(Kinds.Photo.CONTENT_ITEM_TYPE)
}
