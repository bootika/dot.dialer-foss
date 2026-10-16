package dev.goodwy.rphone.modal.repository

import dev.goodwy.rphone.modal.data.RawContactInfo
import android.Manifest
import android.accounts.Account
import android.app.Application
import android.net.Uri
import android.os.Looper
import android.provider.ContactsContract
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactGroupMembership
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [36], application = Application::class)
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

        val repository: IContactsRepository = CachingContactsRepository(context, delegate)

        assertTrue(repository.getContacts().isEmpty())
    }

    @Test
    fun `manual invalidation reloads the contact snapshot`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        val delegate = MutableContactsRepository(
            contacts = listOf(Contact(id = "first", givenName = "Alexandru")),
        )
        val repository: IContactsRepository = CachingContactsRepository(context, delegate)

        assertEquals("first", repository.getContacts().single().id)
        delegate.contacts = listOf(Contact(id = "second", givenName = "Mara"))
        assertEquals("first", repository.getContacts().single().id)

        repository.invalidate()

        assertEquals("second", repository.getContacts().single().id)
    }

    @Test
    fun `non caching repository keeps inert refresh defaults`() = runTest {
        val repository: IContactsRepository = EmptyContactsRepository()

        repository.invalidate()
        repository.invalidateAndNotify()

        assertTrue(repository.changes.toList().isEmpty())
        assertTrue(repository.getContacts().isEmpty())
    }

    @Test
    fun `notifying invalidation clears both snapshots before publishing`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val delegate = MutableContactsRepository(listOf(Contact(id = "before")))
        val repository: IContactsRepository = CachingContactsRepository(context, delegate)
        repository.getContacts()
        repository.getContactsFull()
        val seen = mutableListOf<Pair<String, String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes.collect {
                seen += repository.getContacts().single().id to repository.getContactsFull().single().id
            }
        }
        delegate.contacts = listOf(Contact(id = "after"))

        repository.invalidateAndNotify()

        assertEquals(listOf("after" to "after"), seen)
    }

    @Test
    fun `manual invalidation clears both snapshots without publishing`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val delegate = MutableContactsRepository(listOf(Contact(id = "before")))
        val repository: IContactsRepository = CachingContactsRepository(context, delegate)
        repository.getContacts()
        repository.getContactsFull()
        var events = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.changes.collect { events++ } }
        delegate.contacts = listOf(Contact(id = "after"))

        repository.invalidate()

        assertEquals(0, events)
        assertEquals("after", repository.getContacts().single().id)
        assertEquals("after", repository.getContactsFull().single().id)
    }

    @Test
    fun `provider callback invalidates before notifying subscribers`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        val delegate = MutableContactsRepository(listOf(Contact(id = "before")))
        val repository: IContactsRepository = CachingContactsRepository(context, delegate)
        repository.getContacts()
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes.collect { seen += repository.getContacts().single().id }
        }
        delegate.contacts = listOf(Contact(id = "after"))

        context.contentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it == "after" })
    }

    @Test
    fun `all successful mutation paths invalidate and publish once`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val delegate = MutableContactsRepository(listOf(Contact(id = "initial")))
        val repository: IContactsRepository = CachingContactsRepository(context, delegate)
        val mutations: List<suspend () -> Unit> = listOf(
            { repository.toggleFavorite("id", true) },
            { repository.saveContact(Contact(id = "id")) },
            { repository.saveContactAndRead(Contact(id = "id")) },
            { repository.deleteContact("id") },
            { repository.deleteContacts(listOf("id")) },
            { repository.moveContacts(listOf("id"), null, null); Unit },
            { repository.mergeContacts("id", listOf("source")) },
            { repository.unmergeAllSources("id") },
            { repository.setCustomRingtone("id", null) },
            { repository.formatAllPhoneNumbers(null) },
            { repository.setDefaultPhoneNumber("id", "0700000011", true) },
            { repository.setContactGroups("id", emptySet()) },
            { repository.createContactGroup("id", "Test group"); Unit },
            { repository.renameContactGroup(1, "Test group") },
            { repository.deleteContactGroup(1) },
            { repository.updateRawContact("raw", Contact(id = "id")) },
            { repository.makeContactPrivate("id"); Unit },
            { repository.makeContactPublic("id"); Unit },
            { repository.setContactHidden("id", false) },
            { repository.importPrivateContacts(Uri.EMPTY) },
        )
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes.collect { seen += repository.getContacts().single().id }
        }
        mutations.forEachIndexed { index, mutate ->
            repository.getContacts()
            delegate.contacts = listOf(Contact(id = "revision-$index"))
            mutate()
            assertEquals(index + 1, seen.size)
            assertEquals("revision-$index", seen.last())
        }
    }

    @Test
    fun `failed write does not invalidate or publish a successful change`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val delegate = MutableContactsRepository(listOf(Contact(id = "before")))
        val repository: IContactsRepository = CachingContactsRepository(context, delegate)
        repository.getContacts()
        var events = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.changes.collect { events++ } }
        delegate.contacts = listOf(Contact(id = "after"))
        delegate.failSave = true

        val failure = runCatching { repository.saveContact(Contact(id = "id")) }.exceptionOrNull()

        assertTrue(failure is SecurityException)
        assertEquals(0, events)
        assertEquals("before", repository.getContacts().single().id)
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
        override suspend fun getRawContactsForContact(contactId: String) = emptyList<RawContactInfo>()
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
        var failSave = false
        override suspend fun getContacts(includePrivate: Boolean, includeHidden: Boolean) = contacts
        override suspend fun getContactsFull(includePrivate: Boolean, includeHidden: Boolean) = contacts
        override suspend fun saveContact(contact: Contact) {
            if (failSave) throw SecurityException("Write refused")
        }
        override suspend fun saveContactAndRead(contact: Contact): Contact = contact
    }
}
