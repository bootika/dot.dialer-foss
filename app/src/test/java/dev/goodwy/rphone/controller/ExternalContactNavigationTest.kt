package dev.goodwy.rphone.controller

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class ExternalContactNavigationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val model = MainViewModel(PreferenceManager(context))
    private lateinit var provider: IdentityProvider

    private class IdentityProvider : ContentProvider() {
        val rows = mutableMapOf<String, Map<String, String?>>()
        val queried = mutableListOf<Uri>()
        var failure: RuntimeException? = null
        var queryOnMain = false
        var duplicates = false
        var nullCursor = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queried += uri
            queryOnMain = queryOnMain || Looper.myLooper() == Looper.getMainLooper()
            failure?.let { throw it }
            if (nullCursor) return null
            val columns = projection ?: error("Projection required")
            return MatrixCursor(columns).apply {
                rows[uri.toString()]?.let { row ->
                    val values = columns.map { row[it] }.toTypedArray()
                    addRow(values)
                    if (duplicates) addRow(values)
                }
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("Unexpected contact write")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Unexpected contact write")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Unexpected contact write")
    }

    @Before fun setup() {
        provider = IdentityProvider()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        ShadowContentResolver.registerProviderInternal("contacts", provider)
    }

    private fun expectContact(uri: String, column: String, id: String = "73") = runBlocking {
        provider.rows[uri] = mapOf(column to id)
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
            val target = model.resolveNavigationTarget(Intent(action, Uri.parse(uri)), context)
            assertEquals(if (action == Intent.ACTION_VIEW) NavigationTarget.ContactDetails(id)
                else NavigationTarget.ContactEdit(contactId = id), target)
        }
        assertFalse("Provider lookup must run off main", provider.queryOnMain)
        assertTrue(provider.queried.all { it.toString() == uri })
    }

    @Test fun emailAndPhoneDataRowsResolveTheirAggregateOwnerRatherThanTheirOwnId() {
        for (path in listOf("data/901", "data/phones/902", "data/emails/903")) {
            provider.queried.clear()
            expectContact("content://com.android.contacts/$path", ContactsContract.Data.CONTACT_ID)
        }
    }

    @Test fun completeLookupWithStaleIdUsesTheProviderCurrentIdAndPreservesEncoding() {
        expectContact("content://com.android.contacts/contacts/lookup/p0nTest%2FAlexandru/901?directory=0",
            ContactsContract.Contacts._ID)
    }

    @Test fun lookupWithoutNumericIdUsesTheFullKey() {
        expectContact("content://com.android.contacts/contacts/lookup/p0nTest%252FAlexandru", ContactsContract.Contacts._ID)
    }

    @Test fun directContactRetainsItsProviderIdentity() {
        expectContact("content://com.android.contacts/contacts/73", ContactsContract.Contacts._ID)
    }

    @Test fun legacyPersonIdIsARawContactAndResolvesItsAggregateOwner() = runBlocking {
        val canonicalRaw = "content://com.android.contacts/raw_contacts/905"
        provider.rows[canonicalRaw] = mapOf(ContactsContract.RawContacts.CONTACT_ID to "73")
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
            assertEquals(if (action == Intent.ACTION_VIEW) NavigationTarget.ContactDetails("73")
                else NavigationTarget.ContactEdit(contactId="73"), model.resolveNavigationTarget(
                Intent(action, Uri.parse("content://contacts/people/905")), context))
        }
        assertEquals(listOf(canonicalRaw, canonicalRaw), provider.queried.map { it.toString() })
        assertFalse(provider.queryOnMain)
    }

    @Test fun rawContactEditAlsoTargetsTheAggregateOwner() {
        expectContact("content://com.android.contacts/raw_contacts/904", ContactsContract.RawContacts.CONTACT_ID)
    }

    @Test fun deniedUnavailableAndDeletedRowsNeverReuseTheNumericTail() = runBlocking {
        val uri = Uri.parse("content://com.android.contacts/data/901")
        for (failure in listOf(null, SecurityException("Denied"), IllegalArgumentException("Unavailable"))) {
            provider.failure = failure
            for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
                assertEquals(NavigationTarget.ContactUnavailable,
                    model.resolveNavigationTarget(Intent(action, uri).putExtra("contact_id", "901"), context))
            }
        }
        provider.failure = null
        provider.nullCursor = true
        assertEquals(NavigationTarget.ContactUnavailable, model.resolveNavigationTarget(Intent(Intent.ACTION_VIEW, uri), context))
    }

    @Test fun invalidAmbiguousAndNullOwnerRowsNeverOpenAnotherContact() = runBlocking {
        val uri = "content://com.android.contacts/data/901"
        for (value in listOf(null, "0", "-1", "bad")) {
            provider.rows[uri] = mapOf(ContactsContract.Data.CONTACT_ID to value)
            assertEquals(NavigationTarget.ContactUnavailable,
                model.resolveNavigationTarget(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), context))
        }
        provider.rows[uri] = mapOf(ContactsContract.Data.CONTACT_ID to "73")
        provider.duplicates = true
        assertEquals(NavigationTarget.ContactUnavailable,
            model.resolveNavigationTarget(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), context))
    }

    @Test fun unknownAuthoritiesCollectionsMalformedAndWebLinksAreNotQueriedOrRouted() = runBlocking {
        for (uri in listOf("https://example.test/contacts/73", "content://other.contacts/contacts/73",
            "content://com.android.contacts/data", "content://com.android.contacts/contacts/filter/73",
            "content://com.android.contacts/contacts/lookup/key/not-an-id", "content://com.android.contacts/data/0")) {
            for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
                assertNull(model.resolveNavigationTarget(Intent(action, Uri.parse(uri)).putExtra("contact_id", "73"), context))
            }
        }
        assertTrue(provider.queried.isEmpty())
    }

    @Test fun emailRowsAreAdvertisedToAndroidForViewAndEdit() {
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
            val intent = Intent(action).setDataAndType(Uri.parse("content://com.android.contacts/data/901"),
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .addCategory(Intent.CATEGORY_DEFAULT)
            val matches = context.packageManager.queryIntentActivities(intent, 0)
            assertTrue("MainActivity must accept an external email row for $action",
                matches.any { it.activityInfo.name == "dev.goodwy.rphone.MainActivity" })
        }
    }

    @Test fun internalExtraAndExistingNonContactNavigationRemainAvailable() = runBlocking {
        assertEquals(NavigationTarget.ContactDetails("73"), model.resolveNavigationTarget(
            Intent(Intent.ACTION_VIEW).putExtra("contact_id", "73"), context))
        assertEquals(NavigationTarget.ContactDetails("73"), model.resolveNavigationTarget(
            Intent(ACTION_VIEW_CONTACT).putExtra("contact_id", "73"), context))
        assertEquals(NavigationTarget.ContactEdit(contactId="73"), model.resolveNavigationTarget(
            Intent(Intent.ACTION_EDIT).putExtra("contact_id", "73"), context))
        assertEquals(NavigationTarget.Dialpad("+40700000235"), model.resolveNavigationTarget(
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:+40700000235")), context))
        assertEquals(NavigationTarget.Recents, model.resolveNavigationTarget(
            Intent(Intent.ACTION_VIEW, Uri.parse("content://call_log/calls/901")), context))
        assertEquals(NavigationTarget.ContactEdit(initialName="Test Alexandru Butica"), model.resolveNavigationTarget(
            Intent(Intent.ACTION_INSERT).putExtra(ContactsContract.Intents.Insert.NAME, "Test Alexandru Butica"), context))
        assertTrue(provider.queried.isEmpty())
    }
}
