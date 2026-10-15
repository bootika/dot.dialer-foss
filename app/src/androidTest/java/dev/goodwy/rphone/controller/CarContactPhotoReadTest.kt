package dev.goodwy.rphone.controller

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only reads the two existing contacts requested by the user; exports no IDs/numbers/pixels. */
@RunWith(AndroidJUnit4::class)
class CarContactPhotoReadTest {
    @Test fun compareRequestedLocalPhotos() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val results = JSONArray()
        for (name in listOf("Ayan", "Loredana")) {
            val result = JSONObject().put("label", name)
            resolver.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY}=?", arrayOf(name), null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                val id = cursor.getLong(0)
                for (column in 1..2) {
                    val start = SystemClock.elapsedRealtime()
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(requireNotNull(cursor.getString(column)).toUri())!!.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                    assertTrue(options.outWidth > 0 && options.outHeight > 0)
                    result.put(if (column == 1) "full" else "thumbnail", JSONObject()
                        .put("width", options.outWidth).put("height", options.outHeight)
                        .put("mime", options.outMimeType)
                        .put("localBoundsReadMs", SystemClock.elapsedRealtime() - start))
                }
                val owners = JSONArray()
                resolver.query(ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data.RAW_CONTACT_ID, ContactsContract.Data.IS_SUPER_PRIMARY),
                    "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                    arrayOf(id.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null)!!.use { photos ->
                    while (photos.moveToNext()) {
                        resolver.query(ContactsContract.RawContacts.CONTENT_URI,
                            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
                            "${ContactsContract.RawContacts._ID}=?", arrayOf(photos.getLong(0).toString()), null)!!.use { raw ->
                            assertTrue(raw.moveToFirst())
                            owners.put(JSONObject().put("sourceType", raw.getString(0))
                                .put("superPrimary", photos.getInt(1) != 0))
                        }
                    }
                }
                result.put("photoOwners", owners)
            }
            results.put(result)
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("photo_comparison", results.toString()) })
    }
}
