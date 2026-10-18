package dev.goodwy.rphone.view.screen

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,36],application=Application::class)
class LastOutgoingCallTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var provider: HistoryProvider
    private data class Entry(val number:String,val date:Long,val type:Int=2,val presentation:Int=1)
    private class HistoryProvider:ContentProvider() {
        var rows=listOf<Entry>();var queries=0;var mainThread=false;var denied=false
        override fun onCreate()=true
        override fun getType(uri:Uri)="vnd.android.cursor.dir/calls"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
            queries++;mainThread=Looper.myLooper()==Looper.getMainLooper()
            if(denied)throw SecurityException("Permission revoked")
            assertEquals("type = ? AND presentation = ?",selection)
            assertArrayEquals(arrayOf("2","1"),selectionArgs)
            assertEquals("date DESC",sortOrder)
            return MatrixCursor(requireNotNull(projection)).apply {
                rows.filter{it.type==2&&it.presentation==1}.sortedByDescending{it.date}.forEach{addRow(arrayOf(it.number))}
            }
        }
        override fun insert(uri:Uri,values:ContentValues?):Uri=error("Unexpected write")
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=error("Unexpected write")
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=error("Unexpected write")
    }
    @Before fun setup() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CALL_LOG)
        provider=HistoryProvider();ShadowContentResolver.registerProviderInternal("call_log",provider)
    }
    @Test fun outgoingQueryIgnoresNewerIncomingHiddenRowsAndRecentsGrouping()=runBlocking {
        provider.rows=listOf(Entry("0700000101",1),Entry("0700000102",2),Entry("0700000102",100,type=1),
            Entry("0700000103",101,presentation=2),Entry("private",102),Entry("*123#",103))
        assertEquals("0700000102",withContext(Dispatchers.IO){readLastOutgoingNumber(context)})
        assertFalse(provider.mainThread)
    }
    @Test fun emptyRevokedOrMissingPermissionsDoNotFillAnything()=runBlocking {
        assertNull(withContext(Dispatchers.IO){readLastOutgoingNumber(context)})
        provider.denied=true
        assertNull(withContext(Dispatchers.IO){readLastOutgoingNumber(context)})
        val count=provider.queries;Shadows.shadowOf(context).denyPermissions(Manifest.permission.READ_CALL_LOG)
        assertNull(withContext(Dispatchers.IO){readLastOutgoingNumber(context)})
        assertEquals(count,provider.queries)
    }
}
