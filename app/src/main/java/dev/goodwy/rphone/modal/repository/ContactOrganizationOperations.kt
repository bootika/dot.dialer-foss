package dev.goodwy.rphone.modal.repository

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

/** Replaces organization data so blank Company/Job values remove stale provider fields. */
internal fun MutableList<ContentProviderOperation>.replaceOrganization(
    rawContactId: String,
    company: String,
    jobTitle: String,
) {
    add(
        ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId, CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            )
            .build()
    )
    if (company.isBlank() && jobTitle.isBlank()) return
    add(
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Organization.COMPANY, company)
            .withValue(CommonDataKinds.Organization.TITLE, jobTitle)
            .build()
    )
}
