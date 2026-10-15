package dev.goodwy.rphone.modal.repository

import android.app.Application
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import dev.goodwy.rphone.controller.util.PreferenceManager
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
class ContactAggregationRegressionTest {
    private val provider=AggregationProvider()
    private lateinit var repo:ContactsRepository
    private lateinit var prefs:PreferenceManager
    @Before fun setup() {
        val context=RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        repo=ContactsRepository(context,ContactEditPersistenceAuditTest.MemoryPrivateDao())
        prefs=PreferenceManager(context)
        kotlinx.coroutines.runBlocking { prefs.restoreAllPreferences(mapOf(
            "favorite_number_101" to "+442079460018",
            "favorite_sim_101" to "test-line",
            PreferenceManager.KEY_FAVORITES_ORDER to "101,202,303")) }
    }
    @Test fun `merge commits all raw sources and follows changed identity for favorite choices`()=runTest {
        repo.mergeContacts("101",listOf("202"))
        assertEquals(setOf("900"),provider.rows.values.toSet())
        assertEquals("+442079460018",PreferenceManager(RuntimeEnvironment.getApplication()).getFavoriteNumber("900"))
        val fresh=PreferenceManager(RuntimeEnvironment.getApplication())
        assertEquals("test-line",fresh.getFavoriteSim("900"))
        assertEquals(listOf("900","303"),fresh.getFavoritesOrder())
    }
    @Test fun `merge failure propagates without rewriting preferences`()=runTest {
        provider.fail=true
        var failed=false
        try { repo.mergeContacts("101",listOf("202")) } catch (_:SecurityException) {failed=true}
        assertTrue(failed)
        assertEquals(listOf("101","202","303"),prefs.getFavoritesOrder())
        assertEquals(setOf("101","202"),provider.rows.values.toSet())
    }
    @Test fun `provider acceptance without actual aggregation is not success`()=runTest {
        provider.ignore=true
        var failed=false
        try { repo.mergeContacts("101",listOf("202")) } catch (_:IllegalStateException) {failed=true}
        assertTrue(failed)
        assertEquals(listOf("101","202","303"),prefs.getFavoritesOrder())
    }
    @Test fun `unmerge verifies every raw source has its own contact id`()=runTest {
        provider.rows.keys.toList().forEach { provider.rows[it]="101" }
        repo.unmergeAllSources("101")
        assertEquals(3,provider.rows.values.toSet().size)
    }
    @Test fun `stale or private merge input fails before provider operations`()=runTest {
        for (ids in listOf(listOf("101"),listOf("101","p1"),listOf("101","999"))) {
            var failed=false
            try {repo.mergeContacts(ids.first(),ids.drop(1))} catch (_:IllegalArgumentException) {failed=true}
            assertTrue(failed)
        }
        assertEquals(0,provider.updates)
    }
    class AggregationProvider:ContentProvider() {
        val rows=linkedMapOf("11" to "101","12" to "101","21" to "202")
        var fail=false
        var ignore=false
        var updates=0
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
            selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
            val columns=projection?:arrayOf("_id","contact_id")
            val cursor=MatrixCursor(columns)
            if(uri.pathSegments.firstOrNull()!="raw_contacts") return cursor
            val args=selectionArgs.orEmpty().toSet()
            val filtered=rows.filter { (id,contactId) -> when {
                selection?.startsWith("contact_id")==true -> contactId in args
                selection?.startsWith("_id")==true -> id in args
                else -> true
            }}
            filtered.forEach { (id,contactId) -> cursor.addRow(columns.map { column -> when(column) {
                "_id" -> id; "contact_id" -> contactId
                "account_name" -> if(id=="21") "test.alexandru.butica@example.com" else null
                "account_type" -> if(id=="21") "com.google" else null
                else -> null
            } }.toTypedArray()) }
            return cursor
        }
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int {
            if(fail) throw SecurityException("Provider denied the test operation")
            updates++
            if(ignore) return 1
            check(uri.pathSegments.first()=="aggregation_exceptions")
            val a=requireNotNull(values).getAsString("raw_contact_id1")
            val b=values.getAsString("raw_contact_id2")
            if(values.getAsInteger("type")==ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER) {
                val old=setOf(rows.getValue(a),rows.getValue(b))
                rows.keys.toList().filter {rows[it] in old}.forEach {rows[it]="900"}
            } else { rows[a]="${a}00"; rows[b]="${b}00" }
            return 1
        }
        override fun insert(uri:Uri,values:ContentValues?):Uri?=error("Unexpected insert")
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=error("No deletion in merge")
        override fun getType(uri:Uri):String?=null
    }
}
