package dev.goodwy.rphone.modal.data

data class RawContactInfo(
    val rawContactId: String,
    val accountName: String?,
    val accountType: String?,
    val contactId: String,
    val isReadOnly: Boolean = false,
)
