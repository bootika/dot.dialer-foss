package dev.goodwy.rphone.view.screen.settings

import android.util.Log
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository

internal data class SpamNumberInfo(
    val number: String,
    val name: String,
    val photoUri: String?,
)

private const val SPAM_SCREEN_DATA_LOG_TAG = "SpamScreenData"

internal suspend fun loadBlockedNumberInfo(
    numbers: List<String>,
    contactsRepository: IContactsRepository,
): List<SpamNumberInfo> = numbers.map { number ->
    resolveNumberInfo(number, contactsRepository)
}

internal suspend fun loadRecentNumberInfo(
    callLogRepository: ICallLogRepository,
    contactsRepository: IContactsRepository,
): List<SpamNumberInfo> = try {
    val seen = mutableSetOf<String>()
    callLogRepository.getCallLogs().mapNotNull { entry ->
        val number = entry.number
        if (number.isBlank() || !seen.add(number)) {
            null
        } else {
            resolveNumberInfo(number, contactsRepository)
        }
    }
} catch (error: Exception) {
    Log.w(SPAM_SCREEN_DATA_LOG_TAG, "Unable to load recent numbers for blocking", error)
    emptyList()
}

internal suspend fun loadContactNumberInfo(
    contactsRepository: IContactsRepository,
): List<SpamNumberInfo> = try {
    contactsRepository.getContacts()
        .filter { it.phoneNumbers.isNotEmpty() }
        .flatMap { contact ->
            contact.phoneNumbers.map { number ->
                SpamNumberInfo(number, contact.displayName, contact.photoUri)
            }
        }
        .distinctBy(SpamNumberInfo::number)
        .sortedBy(SpamNumberInfo::name)
} catch (error: Exception) {
    Log.w(SPAM_SCREEN_DATA_LOG_TAG, "Unable to load contact numbers for blocking", error)
    emptyList()
}

private suspend fun resolveNumberInfo(
    number: String,
    contactsRepository: IContactsRepository,
): SpamNumberInfo {
    val contact = try {
        contactsRepository.getContactByNumber(number)
    } catch (error: Exception) {
        Log.w(SPAM_SCREEN_DATA_LOG_TAG, "Unable to resolve contact metadata", error)
        null
    }
    return SpamNumberInfo(
        number = number,
        name = contact?.displayName ?: number,
        photoUri = contact?.photoUri,
    )
}
