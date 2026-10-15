package dev.goodwy.rphone.modal.repository

import android.Manifest
import android.accounts.Account
import android.net.Uri
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactGroupMembership
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CachingContactsRepositoryTest {
    @After
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `repository can be created and queried before contacts permission is granted`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        val delegate = EmptyContactsRepository()

        val repository = CachingContactsRepository(context, delegate)

        assertTrue(repository.getContacts().isEmpty())
    }

    @Test
    fun `manual invalidation reloads the contact snapshot`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        val delegate = MutableContactsRepository(
            contacts = listOf(Contact(id = "first", givenName = "Alexandru")),
        )
        val repository = CachingContactsRepository(context, delegate)

        assertEquals("first", repository.getContacts().single().id)
        delegate.contacts = listOf(Contact(id = "second", givenName = "Mara"))
        assertEquals("first", repository.getContacts().single().id)

        repository.invalidate()

        assertEquals("second", repository.getContacts().single().id)
    }

    private class EmptyContactsRepository : IContactsRepository {
        override suspend fun getContacts(includePrivate: Boolean, includeHidden: Boolean) = emptyList<Contact>()
        override suspend fun getContactsFull(includePrivate: Boolean, includeHidden: Boolean) = emptyList<Contact>()
        override suspend fun getContactById(contactId: String) = null
        override suspend fun getContactByNumber(number: String) = null
        override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) = Unit
        override suspend fun saveContact(contact: Contact) = Unit
        override suspend fun deleteContact(contactId: String) = Unit
        override suspend fun deleteContacts(contactIds: List<String>) = Unit
        override suspend fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?) = emptyMap<String, String>()
        override suspend fun getAvailableAccounts() = emptyList<Account>()
        override suspend fun getAvailableAccountsForMoving() = emptyList<Account>()
        override suspend fun findDuplicates() = emptyList<List<Contact>>()
        override suspend fun mergeContacts(targetContactId: String, sourceContactIds: List<String>) = Unit
        override suspend fun unmergeAllSources(contactId: String) = Unit
        override suspend fun setCustomRingtone(contactId: String, ringtoneUri: String?) = Unit
        override suspend fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)?) = Unit
        override suspend fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean) = Unit
        override suspend fun getContactGroups(contactId: String) = emptyList<ContactGroupMembership>()
        override suspend fun setContactGroups(contactId: String, selectedGroupIds: Set<Long>) = Unit
        override suspend fun getRawContactsForContact(contactId: String) = emptyList<ContactsRepository.RawContactInfo>()
        override suspend fun getRawContactData(rawContactId: String) = null
        override suspend fun updateRawContact(rawContactId: String, contact: Contact) = Unit
        override suspend fun dumpContact(contactId: String) = ""
        override suspend fun makeContactPrivate(contactId: String): String? = contactId
        override suspend fun makeContactPublic(contactId: String): String? = contactId
        override suspend fun setContactHidden(contactId: String, isHidden: Boolean) = Unit
        override suspend fun exportPrivateContacts(uri: Uri) = Unit
        override suspend fun importPrivateContacts(uri: Uri) = Unit
        override suspend fun isNumberHidden(number: String) = false
        override suspend fun getHiddenNumbers() = emptyList<String>()
    }

    private class MutableContactsRepository(
        var contacts: List<Contact>,
    ) : IContactsRepository by EmptyContactsRepository() {
        override suspend fun getContacts(includePrivate: Boolean, includeHidden: Boolean) = contacts
    }
}
