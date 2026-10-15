package dev.goodwy.rphone.modal.data

/** A writable Android Contacts group available to one raw source of an aggregate contact. */
data class ContactGroupMembership(
    val groupId: Long,
    val rawContactId: Long,
    val title: String,
    val accountName: String?,
    val accountType: String?,
    val isMember: Boolean,
)
