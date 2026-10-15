package dev.goodwy.rphone.controller.util

import android.app.Application
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadableCallPhotoResolverTest {
    @Test fun unreadableOrInvalidPrimaryUsesReadableLocalFallbackWithoutNetwork() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val image = File(context.cacheDir, "contact-photo.png")
        image.outputStream().use { stream ->
            createBitmap(16, 16).apply { eraseColor(Color.BLUE) }.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        val fallback = "content://com.android.contacts/display_photo/42"
        val invalid = "content://com.android.contacts/display_photo/43"
        fun prepareStreams() {
            org.robolectric.Shadows.shadowOf(context.contentResolver)
                .registerInputStream(Uri.parse(fallback), ByteArrayInputStream(image.readBytes()))
            org.robolectric.Shadows.shadowOf(context.contentResolver)
                .registerInputStream(Uri.parse(invalid), ByteArrayInputStream("not an image".toByteArray()))
        }
        val resolver = ReadableCallPhotoResolver(context)
        prepareStreams()
        assertEquals(fallback, resolver.resolve("content://com.android.contacts/missing/7", fallback))
        prepareStreams()
        assertEquals(fallback, resolver.resolve(invalid, fallback))
        prepareStreams()
        assertEquals(fallback, resolver.resolve("https://example.test/contact-photo.png", fallback))
        prepareStreams()
        assertEquals(fallback, resolver.resolve(fallback, "content://com.android.contacts/missing/7"))
        assertNull(resolver.resolve(null, null))
        assertNull(resolver.resolve(null, "https://example.test/contact-photo.png"))
    }
}
