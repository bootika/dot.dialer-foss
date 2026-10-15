package dev.goodwy.rphone.controller.util

import dev.goodwy.rphone.modal.data.Contact

/**
 * Resolves the number used by one-tap calling surfaces such as Favorites and Android Auto.
 *
 * The stored favorite remains authoritative while it still belongs to the contact. If the
 * contact changed, fall back to the primary number and then to the first available number.
 */
internal fun preferredCallNumber(contact: Contact, savedNumber: String?): String? {
    val numbers = contact.phoneNumbers.filter(String::isNotBlank).distinct()
    val normalizedSaved = savedNumber?.phoneComparisonKey()
    return numbers.firstOrNull { number ->
        normalizedSaved != null && number.phoneComparisonKey() == normalizedSaved
    } ?: contact.phoneDetails.firstOrNull { it.isPrimary }?.number?.takeIf(String::isNotBlank)
        ?: numbers.firstOrNull()
}

private fun String.phoneComparisonKey(): String = filter(Char::isDigit).takeLast(10)
