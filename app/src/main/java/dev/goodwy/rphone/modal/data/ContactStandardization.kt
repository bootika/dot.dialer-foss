package dev.goodwy.rphone.modal.data

data class StandardizeStats(
    val totalContacts: Int,
    val contactsWithChanges: Int,
    val totalNumbersChanged: Int,
    val examples: List<NumberChangeExample>,
)

data class NumberChangeExample(
    val contactName: String,
    val oldNumber: String,
    val newNumber: String,
)
