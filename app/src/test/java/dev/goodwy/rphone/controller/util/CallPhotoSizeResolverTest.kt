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
import android.provider.ContactsContract
import androidx.core.graphics.createBitmap
import dev.goodwy.rphone.view.screen.useFullCallPhotoAppearance
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallPhotoSizeResolverTest {
    @Test fun thresholdAndMandatoryExceptionsPreserveTheSavedStyle() {
        val full = PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO
        for (size in listOf(96, 191, 192, 256)) assertFalse(useFullCallPhotoAppearance(full, true, CallPhotoDimensions(size, size)))
        for (size in listOf(257, 384, 512)) assertTrue(useFullCallPhotoAppearance(full, true, CallPhotoDimensions(size, size)))
        assertFalse(useFullCallPhotoAppearance(full, true, CallPhotoDimensions(512, 200)))
        assertTrue(useFullCallPhotoAppearance(full, false, CallPhotoDimensions(192, 192)))
        assertFalse(useFullCallPhotoAppearance(full, false, CallPhotoDimensions(512, 512, fromWhatsApp = true)))
        assertTrue(useFullCallPhotoAppearance(full, false, CallPhotoDimensions(192, 256)))
        assertTrue(useFullCallPhotoAppearance(full, false, CallPhotoDimensions(256, 256)))
        assertTrue(useFullCallPhotoAppearance(full, true, null))
        assertTrue(useFullCallPhotoAppearance(full, true, CallPhotoDimensions(0, 512)))
        assertFalse(useFullCallPhotoAppearance(PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR, false, CallPhotoDimensions(512, 512)))
    }

    @Test fun readsFullBoundsWithoutEditingTheSource() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val resolver = fileResolver(context)
        for (size in listOf(192, 256, 512, 2048)) for (cutCorners in listOf(false, true)) {
            val file = photo(size, size, cutCorners)
            val bytes = file.readBytes()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.inputStream().use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            assertEquals("fixture ${file.length()} bytes; native bounds", size, bounds.outWidth)
            val actual = resolver.resolve(Uri.fromFile(file).toString())
            assertEquals(org.robolectric.shadows.ShadowLog.getLogsForTag("CallPhotoSize").joinToString { it.msg }, CallPhotoDimensions(size, size), actual)
            assertArrayEquals(bytes, file.readBytes())
        }
        val rect = photo(512, 200, false)
        assertEquals(CallPhotoDimensions(512, 200), resolver.resolve(Uri.fromFile(rect).toString()))
        assertNull(resolver.resolve("https://example.test/avatar.png"))
        assertNull(resolver.resolve("file:///missing-avatar.png"))
        val invalid = File(context.cacheDir, "invalid-image").apply { writeText("unreadable image") }
        assertNull(resolver.resolve(Uri.fromFile(invalid).toString()))
    }

    @Test fun thumbnail96IsMeasuredUsingFull512AndUnknownFullDoesNotReadThumbnail() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val controller = ContentProviderController.of(FullPhotoProvider()).create(ContactsContract.AUTHORITY)
        shadowOf(context.contentResolver).registerInputStream(Uri.parse("content://com.android.contacts/contacts/42/photo"), photo(96, 96, false).inputStream())
        FullPhotoProvider.full = Uri.fromFile(photo(512, 512, false)).toString()
        val resolver = fileResolver(context)
        assertEquals(CallPhotoDimensions(512, 512), resolver.resolve("content://com.android.contacts/contacts/42/photo"))
        assertEquals(CallPhotoDimensions(512, 512), resolver.resolve("content://com.android.contacts/data/42"))
        FullPhotoProvider.full = null
        assertNull(resolver.resolve("content://com.android.contacts/contacts/42/photo"))
        controller.shutdown()
        Unit
    }

    @Test fun opaqueWhiteCornersMixedCornersAndEmptyImagesAreNotGuessedAsMasks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val resolver = fileResolver(context)
        for (kind in listOf("white", "mixed", "empty")) {
            val bitmap = createBitmap(512, 512)
            if (kind != "empty") bitmap.eraseColor(Color.WHITE)
            if (kind == "mixed") bitmap.setPixel(0, 0, Color.TRANSPARENT)
            val file = File(context.cacheDir, "$kind.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            fixtureFiles[Uri.fromFile(file).toString()] = file
            assertEquals(CallPhotoDimensions(512, 512), resolver.resolve(Uri.fromFile(file).toString()))
        }
    }

    private val fixtureFiles = java.util.concurrent.ConcurrentHashMap<String, File>()
    // Keep Android URI identity separate from Windows host fixture paths.
    private fun fileResolver(context: android.content.Context) = CallPhotoSizeResolver(context) { uri ->
        fixtureFiles[uri.toString()]?.inputStream() ?: context.contentResolver.openInputStream(uri)
    }

    private fun photo(width: Int, height: Int, circle: Boolean): File {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = createBitmap(width, height)
        if (circle) Canvas(bitmap).drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, Paint().apply { color = Color.BLUE })
        else bitmap.eraseColor(Color.BLUE)
        val file = File(context.cacheDir, "avatar-$width-$height-$circle.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        fixtureFiles[Uri.fromFile(file).toString()] = file
        return file
    }

    class FullPhotoProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) =
            MatrixCursor(arrayOf(ContactsContract.Contacts.PHOTO_URI)).apply { addRow(arrayOf(full)) }
        override fun getType(uri: Uri) = "vnd.android.cursor.item/contact"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes allowed")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = error("No writes allowed")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = error("No writes allowed")
        companion object { var full: String? = null }
    }
}
