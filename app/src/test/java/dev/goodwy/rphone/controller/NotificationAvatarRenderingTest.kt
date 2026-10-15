package dev.goodwy.rphone.controller

import android.app.Application
import android.graphics.Color
import androidx.core.graphics.createBitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationAvatarRenderingTest {
    @Test fun `fallback silhouettes initials and counts render with transparent corners and readable content`() {
        val output = File("build/reports/visual/notification-avatar-d86").apply { mkdirs() }
        for ((name, label, seed) in listOf(
            Triple("unknown", "?", "unknown_caller"),
            Triple("initial", "A", "Test Alexandru Butica"),
            Triple("count", "6", "missed_calls"),
            Triple("large-count", "125", "missed_calls"),
        )) {
            val bitmap = notificationAvatar(null, label, seed)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(255, Color.alpha(bitmap.getPixel(96, 150)))
            val pixels = IntArray(192 * 192)
            bitmap.getPixels(pixels, 0, 192, 0, 0, 192, 192)
            assertTrue(pixels.count { it == Color.WHITE } > 100)
            assertFalse(pixels.any { it == Color.rgb(15, 48, 39) })
            File(output, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        // A hollow head distinguishes the shared person glyph from the old question mark.
        val unknown = notificationAvatar(null, "?", "unknown_caller")
        assertNotEquals(Color.WHITE, unknown.getPixel(96, 79))
        assertEquals(Color.WHITE, unknown.getPixel(96, 66))
        assertEquals(notificationAvatar(null, "6", "missed_calls").getPixel(96, 150),
            notificationAvatar(null, "125", "missed_calls").getPixel(96, 150))
    }

    @Test fun transparentPhotoHasWhiteBackingWithoutMutatingInput() {
        val photo = createBitmap(192, 192).apply { setPixel(96, 96, Color.BLACK) }
        val rendered = notificationAvatar(photo, "A")
        assertEquals(Color.WHITE, rendered.getPixel(30, 96))
        assertEquals(Color.BLACK, rendered.getPixel(96, 96))
        assertEquals(0, Color.alpha(photo.getPixel(30, 96)))
        rendered.recycle(); photo.recycle()
    }

    @Test fun `valid photos retain center crop and recycled photos receive fallback`() {
        val photo = createBitmap(320, 160).apply { eraseColor(Color.RED) }
        val rendered = notificationAvatar(photo, "A")
        assertEquals(Color.RED, rendered.getPixel(0, 0))
        assertEquals(Color.RED, rendered.getPixel(96, 96))
        photo.recycle()
        assertEquals(0, Color.alpha(notificationAvatar(photo, "A").getPixel(0, 0)))
    }
}
