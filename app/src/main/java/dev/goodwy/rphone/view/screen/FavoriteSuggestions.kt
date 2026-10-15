package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName

internal const val FAVORITE_SUGGESTION_LIMIT = 5
internal const val FAVORITE_SUGGESTION_WINDOW_MILLIS = 30L * 24L * 60L * 60L * 1_000L

private data class ContactUsage(var calls: Int = 0, var mostRecent: Long = 0L)

internal fun suggestedFavoriteContacts(
    contacts: List<Contact>,
    callLogs: List<CallLogEntry>,
    nowMillis: Long,
    displayOrder: Int,
    limit: Int = FAVORITE_SUGGESTION_LIMIT,
): List<Contact> {
    if (limit <= 0) return emptyList()
    val candidates = contacts.filter { !it.isFavorite && it.phoneNumbers.isNotEmpty() }
    val contactsById = candidates.associateBy(Contact::id)
    val contactsByNumber = candidates
        .flatMap { contact -> contact.phoneNumbers.map { normalizedPhoneKey(it) to contact } }
        .filter { (number, _) -> number.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (number, matches) ->
            matches.distinctBy(Contact::id).singleOrNull()?.let { number to it }
        }
        .toMap()
    val cutoff = nowMillis - FAVORITE_SUGGESTION_WINDOW_MILLIS
    val usage = mutableMapOf<String, ContactUsage>()

    callLogs.asSequence()
        .filter { it.date >= cutoff && !it.isBlocked }
        .forEach { call ->
            val contact = call.contactId?.let(contactsById::get)
                ?: contactsByNumber[normalizedPhoneKey(call.number)]
                ?: return@forEach
            usage.getOrPut(contact.id, ::ContactUsage).apply {
                calls += call.count
                mostRecent = maxOf(mostRecent, call.date)
            }
        }

    return candidates
        .filter { it.id in usage }
        .sortedWith(
            compareByDescending<Contact> { usage.getValue(it.id).calls }
                .thenByDescending { usage.getValue(it.id).mostRecent }
                .thenBy { getDisplayName(it, displayOrder).lowercase() },
        )
        .take(limit)
}

private fun normalizedPhoneKey(number: String): String {
    val digits = number.filter(Char::isDigit)
    return if (digits.length > 10) digits.takeLast(10) else digits
}
