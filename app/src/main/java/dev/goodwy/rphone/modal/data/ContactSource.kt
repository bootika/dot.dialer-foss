package dev.goodwy.rphone.modal.data

data class ContactSource(
    val accountName: String?,
    val accountType: String?,
    val isPrimary: Boolean = false,
    val rawContactId: String
)
