package dev.goodwy.rphone.modal.repository

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.room.Room
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.db.PrivateContactEntity
import dev.goodwy.rphone.modal.db.RillDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class)
class PrivateContactsRetirementTest {
    private lateinit var db: RillDatabase
    private lateinit var repository: ContactsRepository
    private lateinit var before: List<PrivateContactEntity>
    private val provider = EmptyPublicProvider()

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        db = Room.inMemoryDatabaseBuilder(context, RillDatabase::class.java)
            .allowMainThreadQueries().build()
        db.privateContactDao().insert(PrivateContactEntity.fromContact(Contact(
            id="p1", givenName="Test Alexandru Butica", isPrivate=true, isHidden=true,
            isFavorite=true, phoneNumbers=listOf("+447700900123"), notes="Test note")))
        before = db.privateContactDao().getAll()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        repository = ContactsRepository(context, db.privateContactDao())
    }
    @After fun close() { db.close() }
    private fun unchanged() {
        assertEquals(before, db.privateContactDao().getAll())
        assertEquals(0, provider.writes)
    }
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Retired operation must not succeed") }
        catch (expected: IllegalArgumentException) { }
        catch (expected: UnsupportedOperationException) { }
        unchanged()
    }
    @Test fun `legacy flags cannot expose dormant contacts in either snapshot`() = runTest {
        for (include in listOf(false,true)) for (hidden in listOf(false,true)) {
            assertTrue(repository.getContacts(include,hidden).isEmpty())
            assertTrue(repository.getContactsFull(include,hidden).isEmpty())
        }
        unchanged()
    }
    @Test fun `private ids and numbers no longer identify callers`() = runTest {
        assertNull(repository.getContactById("p1"))
        assertNull(repository.getContactByNumber("+447700900123"))
        assertTrue(repository.getHiddenNumbers().isEmpty())
        assertFalse(repository.isNumberHidden("+447700900123"))
        unchanged()
    }
    @Test fun `private creation and stale saved drafts cannot write or become public`() = runTest {
        rejected { repository.saveContactAndRead(Contact(id="0", isPrivate=true)) }
        rejected { repository.saveContactAndRead(Contact(id="p1", isPrivate=true)) }
        rejected { repository.saveContactAndRead(Contact(id="p1", isPrivate=false)) }
    }
    @Test fun `ordinary mutation and deletion cannot touch dormant rows`() = runTest {
        rejected { repository.toggleFavorite("p1",false) }
        rejected { repository.setCustomRingtone("p1",null) }
        rejected { repository.setContactHidden("p1",false) }
        rejected { repository.deleteContact("p1") }
        rejected { repository.deleteContacts(listOf("101","p1")) }
    }
    @Test fun `private transfer and file entrypoints are retired before side effects`() = runTest {
        rejected { repository.makeContactPrivate("101") }
        rejected { repository.makeContactPublic("p1") }
        rejected { repository.importPrivateContacts(Uri.parse("content://example.test/input")) }
        rejected { repository.exportPrivateContacts(Uri.parse("content://example.test/output")) }
    }
    @Test fun `standardize and duplicate detection leave dormant data alone`() = runTest {
        repository.formatAllPhoneNumbers(null)
        assertTrue(repository.findDuplicates().isEmpty())
        unchanged()
    }
    class EmptyPublicProvider : ContentProvider() {
        var writes=0
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
            selectionArgs:Array<out String>?,sortOrder:String?):Cursor = MatrixCursor(projection?:emptyArray())
        override fun insert(uri:Uri,values:ContentValues?):Uri? { writes++; return null }
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int { writes++; return 0 }
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int { writes++; return 0 }
        override fun getType(uri:Uri):String?=null
    }
}
