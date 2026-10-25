package dev.goodwy.rphone.recordings

import android.app.Application
import android.database.MatrixCursor
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CallCaptureIdentityTest {
    @Test fun ambiguousContactAndSuffixOnlyMatchRemainNumberOnly() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        var rows=listOf(arrayOf("+40720000001","person-a","Test Alexandru Butica"),arrayOf("+40720000001","person-b","Test Alexandru Butica"))
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,object:android.content.ContentProvider() {
            override fun onCreate()=true
            override fun getType(uri:android.net.Uri):String?=null
            override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=error("Read only")
            override fun delete(uri:android.net.Uri,selection:String?,selectionArgs:Array<out String>?)=error("Read only")
            override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?)=error("Read only")
            override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor {
                return MatrixCursor(arrayOf(ContactsContract.PhoneLookup.NUMBER,ContactsContract.PhoneLookup.LOOKUP_KEY,ContactsContract.PhoneLookup.DISPLAY_NAME)).apply {rows.forEach {addRow(it)}}
            }
        })
        assertEquals("",recordingContactIdentity(context,"+40720000001").key)
        rows=listOf(arrayOf("+40730000001","person-a","Test Alexandru Butica"))
        assertEquals("",recordingContactIdentity(context,"+40720000001").key)
        rows=listOf(arrayOf("+40720000001","person-a","Test Alexandru Butica"))
        assertEquals("person-a",recordingContactIdentity(context,"+40 720 000 001").key)
        assertEquals("",recordingContactIdentity(context,"person@example.com").number)
    }
}
