package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.Contact

internal data class ContactFilterInputs(
    val contacts: List<Contact>,
    val accountName: String?,
    val accountType: String?,
    val privateOnly: Boolean,
    val localOnly: Boolean,
    val visibleAccounts: Set<String>?,
)

/** Pure contact filtering used by [ContactsViewModel] and covered independently in unit tests. */
internal fun filterAndSortContacts(
    inputs: ContactFilterInputs,
    sortOrder: Int,
): List<Contact> {
    val activeContacts = inputs.contacts.filterNot { it.isPrivate || it.id.matches(Regex("p[0-9]+")) }
    val baseFiltered = when {
        inputs.localOnly -> activeContacts.filter {
            it.accountName == null && it.accountType == null && !it.isPrivate
        }
        inputs.accountName == null && inputs.accountType == null -> inputs.visibleAccounts?.let { visibleAccounts ->
            activeContacts.filter { contact -> visibleAccounts.contains(contact.accountKey()) }
        } ?: activeContacts
        else -> activeContacts.filter {
            it.accountName == inputs.accountName && it.accountType == inputs.accountType
        }
    }

    return if (sortOrder == SORT_BY_FAMILY_NAME) {
        baseFiltered.sortedBy { it.familyName.lowercase().ifBlank { it.displayName.lowercase() } }
    } else {
        baseFiltered.sortedBy { it.givenName.lowercase().ifBlank { it.displayName.lowercase() } }
    }
}

internal fun groupContactsByInitial(
    contacts: List<Contact>,
    sortOrder: Int,
): Map<Char, List<Contact>> {
    val grouped = contacts.groupBy { contact ->
        val name = if (sortOrder == SORT_BY_FAMILY_NAME) {
            contact.familyName.ifBlank { contact.displayName }
        } else {
            contact.givenName.ifBlank { contact.displayName }
        }
        name.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#'
    }

    return buildMap {
        grouped.keys.filter(Char::isLetter).sorted().forEach { initial ->
            put(initial, grouped.getValue(initial))
        }
        grouped['#']?.let { put('#', it) }
    }
}

internal fun contactCountByAccount(contacts: List<Contact>): Map<String, Int> = buildMap {
    contacts.forEach { contact ->
        val accountName = contact.accountName ?: return@forEach
        val key = "$accountName|${contact.accountType}"
        put(key, getOrDefault(key, 0) + 1)
    }
}

internal fun Contact.accountKey(): String = when {
    accountType != null || accountName != null -> "$accountType|$accountName"
    isPrivate -> PRIVATE_ACCOUNT_KEY
    else -> LOCAL_ACCOUNT_KEY
}

private const val SORT_BY_FAMILY_NAME = 1
private const val LOCAL_ACCOUNT_KEY = "local|local"
private const val PRIVATE_ACCOUNT_KEY = "private|private"
