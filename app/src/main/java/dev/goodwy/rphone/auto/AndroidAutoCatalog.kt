package dev.goodwy.rphone.auto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.controller.accountKey
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.preferredCallNumber
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AndroidAutoContact(
    val id: String,
    val displayName: String,
    val number: String,
)

internal data class AndroidAutoRecent(
    val id: Long,
    val displayName: String,
    val number: String,
    val date: Long,
)

internal data class AndroidAutoCatalog(
    val favorites: List<AndroidAutoContact>,
    val recents: List<AndroidAutoRecent>,
    val contacts: List<AndroidAutoContact>,
)

internal enum class AndroidAutoPermissionState {
    Granted,
    ContactsMissing,
    CallLogMissing,
    CallPermissionMissing,
}

internal data class AndroidAutoCatalogResult(
    val catalog: AndroidAutoCatalog,
    val permissionState: AndroidAutoPermissionState,
)

internal class AndroidAutoCatalogSource(
    private val context: Context,
    private val contactsRepository: IContactsRepository,
    private val callLogRepository: ICallLogRepository,
    private val preferences: PreferenceManager,
) {
    suspend fun load(): AndroidAutoCatalogResult = withContext(Dispatchers.IO) {
        val permissionState = permissionState()
        if (permissionState != AndroidAutoPermissionState.Granted) {
            return@withContext AndroidAutoCatalogResult(emptyCatalog(), permissionState)
        }

        val contacts = contactsRepository.getContacts(includePrivate = true, includeHidden = true)
        val favoriteNumbers = contacts.associate { contact ->
            contact.id to preferences.getFavoriteNumber(contact.id)
        }
        AndroidAutoCatalogResult(
            catalog = buildAndroidAutoCatalog(
                contacts = contacts,
                callLogs = callLogRepository.getCallLogs(),
                visibleAccounts = preferences.getVisibleAccounts(),
                sortOrder = preferences.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0),
                displayOrder = preferences.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0),
                favoriteOrder = preferences.getFavoritesOrder(),
                favoriteNumbers = favoriteNumbers,
            ),
            permissionState = permissionState,
        )
    }

    private fun permissionState(): AndroidAutoPermissionState = when {
        !hasPermission(Manifest.permission.READ_CONTACTS) -> AndroidAutoPermissionState.ContactsMissing
        !hasPermission(Manifest.permission.READ_CALL_LOG) -> AndroidAutoPermissionState.CallLogMissing
        !hasPermission(Manifest.permission.CALL_PHONE) -> AndroidAutoPermissionState.CallPermissionMissing
        else -> AndroidAutoPermissionState.Granted
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun buildAndroidAutoCatalog(
    contacts: List<Contact>,
    callLogs: List<CallLogEntry>,
    visibleAccounts: Set<String>?,
    sortOrder: Int,
    displayOrder: Int,
    favoriteOrder: List<String>,
    favoriteNumbers: Map<String, String?>,
): AndroidAutoCatalog {
    val sensitivePhoneKeys = contacts.asSequence()
        .filter { it.isPrivate || it.isHidden }
        .flatMap { it.phoneNumbers.asSequence() }
        .mapNotNull(SearchTextNormalizer::phoneIdentityKey)
        .toSet()

    val sourceFilteredPhoneKeys = contacts.asSequence()
        .filterNot { it.isPrivate || it.isHidden }
        .filter { contact -> visibleAccounts?.contains(contact.accountKey()) == false }
        .flatMap { it.phoneNumbers.asSequence() }
        .mapNotNull(SearchTextNormalizer::phoneIdentityKey)
        .toSet()

    val visibleContacts = contacts.asSequence()
        .filterNot { it.isPrivate || it.isHidden }
        .filter { contact -> visibleAccounts?.contains(contact.accountKey()) != false }
        .filter { it.phoneNumbers.any(String::isNotBlank) }
        .sortedWith(
            compareBy<Contact> { contact ->
                val sortName = if (sortOrder == 1) contact.familyName else contact.givenName
                sortName.ifBlank { getDisplayName(contact, displayOrder) }.lowercase()
            }.thenBy { getDisplayName(it, displayOrder).lowercase() },
        )
        .toList()

    val visibleById = visibleContacts.associateBy(Contact::id)
    val visibleByPhone = buildMap<String, Contact> {
        visibleContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                SearchTextNormalizer.phoneIdentityKey(number)?.let { key -> putIfAbsent(key, contact) }
            }
        }
    }

    fun toCarContact(contact: Contact): AndroidAutoContact? {
        val number = preferredCallNumber(contact, favoriteNumbers[contact.id]) ?: return null
        return AndroidAutoContact(
            id = contact.id,
            displayName = getDisplayName(contact, displayOrder).ifBlank { number },
            number = number,
        )
    }

    val favoritePosition = favoriteOrder.withIndex().associate { (index, id) -> id to index }
    val favorites = visibleContacts.asSequence()
        .filter(Contact::isFavorite)
        .sortedWith(
            compareBy<Contact> { favoritePosition[it.id] ?: Int.MAX_VALUE }
                .thenBy { getDisplayName(it, displayOrder).lowercase() },
        )
        .mapNotNull(::toCarContact)
        .toList()

    val carContacts = visibleContacts.mapNotNull(::toCarContact)
    val recents = callLogs.asSequence()
        .filter { it.number.isNotBlank() }
        .filterNot { call ->
            SearchTextNormalizer.phoneIdentityKey(call.number) in sensitivePhoneKeys
        }
        .map { call ->
            val phoneKey = SearchTextNormalizer.phoneIdentityKey(call.number)
            val contact = call.contactId?.let(visibleById::get) ?: phoneKey?.let(visibleByPhone::get)
            AndroidAutoRecent(
                id = call.id,
                displayName = contact?.let { getDisplayName(it, displayOrder) }
                    ?.takeIf(String::isNotBlank)
                    ?: call.name?.takeIf {
                        call.contactId == null &&
                            phoneKey !in sourceFilteredPhoneKeys &&
                            it.isNotBlank()
                    }
                    ?: call.number,
                number = call.number,
                date = call.date,
            )
        }
        .toList()

    return AndroidAutoCatalog(favorites = favorites, recents = recents, contacts = carContacts)
}

private fun emptyCatalog(): AndroidAutoCatalog = AndroidAutoCatalog(
    favorites = emptyList(),
    recents = emptyList(),
    contacts = emptyList(),
)
