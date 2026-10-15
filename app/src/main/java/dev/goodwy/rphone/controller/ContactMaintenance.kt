package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.NumberChangeExample
import dev.goodwy.rphone.modal.data.StandardizeStats

internal fun buildStandardizePreview(contacts: List<Contact>): StandardizeStats {
    var contactsWithChanges = 0
    var totalNumbersChanged = 0
    val examples = mutableListOf<NumberChangeExample>()

    contacts.forEach { contact ->
        val changedNumbers = contact.phoneNumbers.mapNotNull { number ->
            val standardized = number.replace(" ", "")
            if (number == standardized) null else number to standardized
        }

        if (changedNumbers.isNotEmpty()) {
            contactsWithChanges++
            totalNumbersChanged += changedNumbers.size
            changedNumbers.take(MAX_STANDARDIZE_EXAMPLES - examples.size).forEach { (old, new) ->
                examples += NumberChangeExample(contact.displayName, old, new)
            }
        }
    }

    return StandardizeStats(
        totalContacts = contacts.size,
        contactsWithChanges = contactsWithChanges,
        totalNumbersChanged = totalNumbersChanged,
        examples = examples,
    )
}

internal fun resolveFavoriteState(current: Boolean, requested: Boolean?): Boolean =
    requested ?: !current

private const val MAX_STANDARDIZE_EXAMPLES = 3
