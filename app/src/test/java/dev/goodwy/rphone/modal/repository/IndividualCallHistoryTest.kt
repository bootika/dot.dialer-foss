package dev.goodwy.rphone.modal.repository

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class IndividualCallHistoryTest {
    private class HistoryProvider(val now: Long) : ContentProvider() {
        override fun onCreate()=true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val columns=projection ?: error("Projection missing")
            return MatrixCursor(columns).apply {
                repeat(105) { index ->
                    val date=now-index*1000L
                    if(args?.firstOrNull()?.toLongOrNull()?.let { date < it } == true) return@repeat
                    addRow(columns.map<String, Any?> { column -> when(column) {
                        CallLog.Calls._ID -> 105L-index
                        CallLog.Calls.NUMBER -> "0700000001"
                        CallLog.Calls.CACHED_NAME -> "Test Alexandru Butica"
                        CallLog.Calls.TYPE -> if(index%2==0) CallLog.Calls.MISSED_TYPE else CallLog.Calls.OUTGOING_TYPE
                        CallLog.Calls.DATE -> date
                        CallLog.Calls.DURATION -> index.toLong()
                        "phone_account_label" -> if(index%2==0) "SIM 1" else "SIM 2"
                        else -> null
                    } }.toTypedArray())
                }
            }
        }
        override fun getType(uri: Uri)="vnd.android.cursor.dir/calls"
        override fun insert(uri: Uri, values: ContentValues?): Uri?=error("Read-only verification")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int=error("Read-only verification")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int=error("Read-only verification")
    }
    @Test fun `toggle retains individual dates types ids SIM and raw ceiling without changing full history`() = runBlocking {
        val app=RuntimeEnvironment.getApplication(); val prefs=PreferenceManager(app)
        prefs.setInt(PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES,100)
        prefs.setInt(PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS,0)
        val contacts=Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,arrayOf(IContactsRepository::class.java)) { _, method, _ ->
            if (method.name == "getChanges") kotlinx.coroutines.flow.emptyFlow<Unit>()
            else if(method.name=="getContacts") emptyList<dev.goodwy.rphone.modal.data.Contact>() else null
        } as IContactsRepository
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY,HistoryProvider(System.currentTimeMillis()))
        val repository=CallLogRepository(app,app.contentResolver,contacts,prefs)
        prefs.setBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,false)
        prefs.getPersistedPreferences()
        assertEquals(105,repository.getCallLogs().single().ids.size)
        prefs.setBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,true)
        prefs.getPersistedPreferences()
        val rows=repository.getCallLogs()
        assertEquals(100,rows.size); assertEquals(100,rows.map { it.id }.distinct().size)
        assertTrue(rows.all { it.ids==listOf(it.id) && it.types==listOf(it.type) })
        assertTrue(rows.zipWithNext().all { (a,b) -> a.date>b.date })
        assertEquals(50,rows.count { it.type==CallLog.Calls.MISSED_TYPE })
        assertEquals(1L,rows[1].duration); assertEquals("SIM 2",rows[1].simLabel)
        assertEquals(105,repository.getCompleteCallLogs().single().ids.size)
        prefs.setBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,false)
        prefs.getPersistedPreferences()
        assertEquals(105,repository.getCallLogs().single().ids.size)
    }
}
