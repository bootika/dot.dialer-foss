package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail

internal data class ContactPhoneSourceData(
    val accountName: String?,
    val accountType: String?,
    val phoneDetails: List<ContactPhoneDetail>,
)

internal data class ContactPhoneSourceIdentity(
    val accountName: String?,
    val accountType: String?,
)

internal data class ContactPhoneRow(
    val detail: ContactPhoneDetail,
    val sources: List<ContactPhoneSourceIdentity>,
)

internal fun buildContactPhoneRows(
    phoneDetails: List<ContactPhoneDetail>,
    sourceData: List<ContactPhoneSourceData>,
): List<ContactPhoneRow> {
    val rows = mutableListOf<ContactPhoneRow>()

    phoneDetails.forEach { detail ->
        val existingIndex = rows.indexOfFirst { phoneNumbersMatch(it.detail.number, detail.number) }
        if (existingIndex == -1) {
            rows += ContactPhoneRow(detail = detail, sources = emptyList())
        } else {
            val existing = rows[existingIndex]
            val preferredDetail = when {
                detail.isPrimary && !existing.detail.isPrimary -> detail
                detail.number.startsWith("+") && !existing.detail.number.startsWith("+") -> detail
                else -> existing.detail
            }
            rows[existingIndex] = existing.copy(
                detail = preferredDetail.copy(isPrimary = existing.detail.isPrimary || detail.isPrimary),
            )
        }
    }

    sourceData.forEach { source ->
        val identity = ContactPhoneSourceIdentity(source.accountName, source.accountType)
        source.phoneDetails.forEach { sourceDetail ->
            val rowIndex = rows.indexOfFirst { phoneNumbersMatch(it.detail.number, sourceDetail.number) }
            if (rowIndex >= 0) {
                val row = rows[rowIndex]
                if (identity !in row.sources) {
                    rows[rowIndex] = row.copy(sources = row.sources + identity)
                }
            }
        }
    }

    return rows
}

internal fun Contact?.companyAndJobLabel(): String = when {
    this == null -> ""
    company.isNotBlank() && jobTitle.isNotBlank() -> "$jobTitle • $company"
    company.isNotBlank() -> company
    jobTitle.isNotBlank() -> jobTitle
    else -> ""
}

internal fun Contact.withPrimaryPhone(number: String, isPrimary: Boolean): Contact = copy(
    phoneDetails = phoneDetails.map { detail ->
        when {
            phoneNumbersMatch(detail.number, number) -> detail.copy(isPrimary = isPrimary)
            isPrimary -> detail.copy(isPrimary = false)
            else -> detail
        }
    },
)

internal fun List<CallLogEntry>.matchingContactDetailsLogs(
    contact: Contact?,
    phoneNumber: String?,
): List<CallLogEntry> {
    val normalizedRequestedNumber = phoneNumber?.withoutSpaces()
    val contactNumbers = contact?.phoneNumbers.orEmpty().mapTo(hashSetOf()) { it.withoutSpaces() }
    return filter { log ->
        val normalizedLogNumber = log.number.withoutSpaces()
        (contact != null && (log.contactId == contact.id || normalizedLogNumber in contactNumbers)) ||
            (phoneNumber != null && normalizedLogNumber == normalizedRequestedNumber)
    }
}

private fun String.withoutSpaces(): String = replace(" ", "")

private fun phoneNumbersMatch(first: String, second: String): Boolean {
    val firstDigits = first.filter(Char::isDigit)
    val secondDigits = second.filter(Char::isDigit)
    if (firstDigits.isEmpty() || secondDigits.isEmpty()) return first == second
    if (firstDigits == secondDigits) return true

    val comparableLength = minOf(9, firstDigits.length, secondDigits.length)
    return comparableLength >= 7 &&
        firstDigits.takeLast(comparableLength) == secondDigits.takeLast(comparableLength)
}
