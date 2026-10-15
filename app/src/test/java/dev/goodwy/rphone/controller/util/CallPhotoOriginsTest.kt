package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.ContactsContract as C
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallPhotoOriginsTest {
    @Test fun savedSourceFollowsTheActualPhotoVersionNotTheContactOrResolution() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        File(context.noBackupFilesDir, "call-photo-origins.json").delete()
        val provider = OriginProvider()
        val controller = ContentProviderController.of(provider).create(C.AUTHORITY)
        val store = CallPhotoOrigins(context)
        val photo = Uri.parse("content://com.android.contacts/display_photo/77")
        val bytes = image(512, true)
        fun register() = shadowOf(context.contentResolver).registerInputStreamSupplier(photo) { ByteArrayInputStream(bytes) }
        assertFalse(store.isWhatsApp(photo))
        register(); store.rememberApplied(7, bytes, "Google")
        assertFalse(store.isWhatsApp(photo))
        register(); store.rememberApplied(7, bytes, "WhatsApp")
        assertTrue(CallPhotoOrigins(context).isWhatsApp(photo))
        assertEquals(CallPhotoDimensions(0, 0, true), CallPhotoSizeResolver(context).resolve(photo.toString()))
        val marker = org.json.JSONObject(File(context.noBackupFilesDir,"call-photo-origins.json").readText()).getJSONObject("9:0:77")
        assertEquals(1, marker.getInt("source")); assertTrue(marker.getBoolean("cut_corners"))
        provider.version++ // An external replacement, including the same photo URI, drops provenance.
        assertFalse(store.isWhatsApp(photo))
        register(); store.rememberApplied(7, image(512, false), "WhatsApp")
        assertFalse("A different stored photograph cannot acquire a marker", store.isWhatsApp(photo))
        register(); store.rememberApplied(7, bytes, "WhatsApp Business")
        assertTrue(store.isWhatsApp(photo))
        provider.photoId = 78
        assertFalse(store.isWhatsApp(photo))
        assertFalse(store.isWhatsApp(Uri.parse("content://another.provider/display_photo/77")))
        controller.shutdown()
        File(context.noBackupFilesDir,"call-photo-origins.json").delete()
        Unit
    }

    @Test fun unsavedImmutableWhatsAppPhotoCarriesSourceAndOneTimeMaskResult() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = UnsavedNumberAvatars(context, "avatar-test@example.test")
        val bytes = image(384, true)
        assertTrue(store.store("+40700000991", bytes, ContactPhotoAutomaticPolicy.Latest, valid = { true }))
        val uri = requireNotNull(store.resolve("+40700000991"))
        assertTrue("owned=${UnsavedNumberAvatars.ownedUri(context,uri)} exists=${File(requireNotNull(Uri.parse(uri).path)).isFile} path=${Uri.parse(uri).path} root=${context.noBackupFilesDir}", CallPhotoOrigins(context).isWhatsApp(Uri.parse(uri)))
        assertEquals(CallPhotoDimensions(0,0,true), CallPhotoSizeResolver(context).resolve(uri))
        assertArrayEquals(bytes, File(requireNotNull(Uri.parse(uri).path)).readBytes())
        val index = org.json.JSONObject(File(File(requireNotNull(Uri.parse(uri).path)).parentFile,"index.json").readText())
        val entry = index.getJSONObject(index.keys().next())
        assertEquals(1,entry.getInt("source"));assertTrue(entry.getBoolean("cut_corners"))
    }

    @Test fun maskDetectionIsOnlyAnObservationAndDoesNotGuessOpaqueWhiteCorners() {
        assertTrue(notificationAvatarHasCutCorners(image(192,true)))
        assertTrue(notificationAvatarHasCutCorners(image(1024,true)))
        assertFalse(notificationAvatarHasCutCorners(image(192,false)))
        assertFalse(notificationAvatarHasCutCorners("not an image".encodeToByteArray()))
    }
    private fun image(size: Int, circular: Boolean): ByteArray {
        val bitmap = createBitmap(size,size)
        if (circular) Canvas(bitmap).drawCircle(size/2f,size/2f,size/2f,Paint().apply { color=Color.BLUE })
        else bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray() }
    }
    class OriginProvider : ContentProvider() {
        var version = 0L; var photoId = 77L
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) =
            MatrixCursor(arrayOf(C.Data._ID,C.Data.DATA_VERSION,C.CommonDataKinds.Photo.PHOTO_FILE_ID)).apply {
                if ((selection?.contains(C.Data.RAW_CONTACT_ID)==true && args?.firstOrNull()=="7") ||
                    (selection?.contains(C.CommonDataKinds.Photo.PHOTO_FILE_ID)==true && args?.firstOrNull()==photoId.toString()))
                    addRow(arrayOf(9L,version,photoId))
            }
        override fun getType(uri: Uri) = "vnd.android.cursor.item/photo"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Provenance must not write contacts")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = error("Provenance must not write contacts")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = error("Provenance must not write contacts")
    }
}
