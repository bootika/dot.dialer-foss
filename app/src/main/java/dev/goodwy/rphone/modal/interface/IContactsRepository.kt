package dev.goodwy.rphone.modal.`interface`

import android.accounts.Account
import android.net.Uri
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactGroupMembership
import dev.goodwy.rphone.modal.repository.ContactsRepository.RawContactInfo

interface IContactsRepository {
    suspend fun getContacts(includePrivate: Boolean = false, includeHidden: Boolean = false): List<Contact>
    suspend fun getContactsFull(includePrivate: Boolean = false, includeHidden: Boolean = false): List<Contact>
    suspend fun getContactById(contactId: String): Contact?
    suspend fun getContactByNumber(number: String): Contact?
    suspend fun getContactByNumberInRegion(number: String, regionIso: String?): Contact? =
        getContactByNumber(number)
    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean)
    suspend fun saveContact(contact: Contact)
    /** Returns the committed identity; callers must not navigate or delete originals on failure. */
    suspend fun saveContactAndRead(contact: Contact): Contact {
        saveContact(contact)
        return requireNotNull(getContactById(contact.id)) { "Saved contact is unavailable" }
    }
    suspend fun deleteContact(contactId: String)
    suspend fun deleteContacts(contactIds: List<String>)
    suspend fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?): Map<String, String>
    suspend fun getAvailableAccounts(): List<Account>
    suspend fun getAvailableAccountsForMoving(): List<Account>
    suspend fun findDuplicates(): List<List<Contact>>
    suspend fun mergeContacts(targetContactId: String, sourceContactIds: List<String>)
    suspend fun unmergeAllSources(contactId: String)
    suspend fun setCustomRingtone(contactId: String, ringtoneUri: String?)
    suspend fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)? = null)
    suspend fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean)
    suspend fun getContactGroups(contactId: String): List<ContactGroupMembership>
    suspend fun setContactGroups(contactId: String, selectedGroupIds: Set<Long>)
    suspend fun createContactGroup(contactId: String, title: String): Long? = null
    suspend fun renameContactGroup(groupId: Long, title: String) = Unit
    suspend fun deleteContactGroup(groupId: Long) = Unit
    suspend fun getRawContactsForContact(contactId: String): List<RawContactInfo>
    suspend fun getRawContactData(rawContactId: String): Contact?
    suspend fun updateRawContact(rawContactId: String, contact: Contact)
    suspend fun dumpContact(contactId: String): String

    // Private Contacts
    suspend fun makeContactPrivate(contactId: String): String?
    suspend fun makeContactPublic(contactId: String): String?
    suspend fun setContactHidden(contactId: String, isHidden: Boolean)
    suspend fun exportPrivateContacts(uri: Uri)
    suspend fun importPrivateContacts(uri: Uri)
    suspend fun isNumberHidden(number: String): Boolean
    suspend fun getHiddenNumbers(): List<String>
}
