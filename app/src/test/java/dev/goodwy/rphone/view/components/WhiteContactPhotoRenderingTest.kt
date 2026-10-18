package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.screen.CalmCallAvatar
import dev.goodwy.rphone.view.theme.DotDialerTheme
import coil.imageLoader
import coil.request.ImageRequest
import coil.memory.MemoryCache
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhiteContactPhotoRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun detailsCircularCallAndListCompositeTransparentPhotosAndKeepFallbacksAcrossThemes() {
        val source = File(rule.activity.cacheDir, "transparent-contact.png")
        val image = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        Canvas(image).drawRect(28f, 0f, 44f, 72f, android.graphics.Paint().apply { color = Color.BLACK })
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val original = source.readBytes()
        val uri = Uri.fromFile(source).toString()
        val opaque = File(rule.activity.cacheDir, "opaque-contact.png")
        val red = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        opaque.outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) }; red.recycle()
        var dark by mutableStateOf(false)
        var visible by mutableStateOf(true)
        // Warm the actual Coil cache with the uncomposited URI/revision used by ambient colors.
        val raw = ImageRequest.Builder(rule.activity).data(uri).callPhotoRevision(uri, 7).build()
        val decoded = android.graphics.BitmapFactory.decodeFile(source.absolutePath)
        val cached = Bitmap.createScaledBitmap(decoded, 180, 180, false)
        assertEquals(0, Color.alpha(cached.getPixel(36, 90)))
        requireNotNull(rule.activity.imageLoader.memoryCache)[requireNotNull(raw.memoryCacheKey)] = MemoryCache.Value(cached)
        rule.setContent {
            CompositionLocalProvider(
                LocalRillAvatarAppearance provides RillAvatarAppearance(visible, true, false, true, false, false),
                LocalCallPhotoRevision provides 7,
            ) {
                DotDialerTheme(systemDark = dark) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RillAvatar("Test Alexandru Butica", photoUri = uri, modifier = Modifier.size(180.dp).testTag("details"))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CalmCallAvatar(uri, "Test Alexandru Butica", "0700000001", 96.dp, Modifier.testTag("call"))
                            RillAvatar("Test Alexandru Butica", photoUri = uri, modifier = Modifier.size(48.dp).testTag("list"))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RillAvatar("Test Alexandru Butica", photoUri = Uri.fromFile(opaque).toString(), modifier = Modifier.size(48.dp).testTag("opaque"))
                            RillAvatar("Test Alexandru Butica", modifier = Modifier.size(48.dp).testTag("missing"))
                            RillAvatar("Test Alexandru Butica", photoUri = "file:///missing.png", modifier = Modifier.size(48.dp).testTag("error"))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val fallback = modernPastelAvatarColor("Test Alexandru Butica").first.toArgb()
        for (theme in listOf(false, true)) {
            rule.runOnIdle { dark = theme }
            rule.waitUntil(10000) {
                val bitmap = screenshot()
                val ready = listOf("details", "call", "list").all { sample(bitmap, it, .2f) == Color.WHITE }
                bitmap.recycle(); ready
            }
            rule.runOnIdle {
                val bitmap = screenshot()
                assertTrue("Rendered theme background", (Color.red(bitmap.getPixel(350, 580)) < 100) == theme)
                for (tag in listOf("details", "call", "list")) {
                    assertEquals("white $tag theme=$theme", Color.WHITE, sample(bitmap, tag, .2f))
                    assertEquals("artwork $tag theme=$theme", Color.BLACK, sample(bitmap, tag, .5f))
                }
                assertEquals(Color.RED, sample(bitmap, "opaque", .2f))
                assertEquals(fallback, sample(bitmap, "missing", .1f))
                assertEquals(fallback, sample(bitmap, "error", .1f))
                val output = File("build/reports/visual/photo-white-d131/${if (theme) "dark" else "light"}.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        rule.runOnIdle { visible = false }
        rule.waitForIdle()
        rule.runOnIdle {
            val bitmap = screenshot()
            for (tag in listOf("details", "call", "list", "opaque")) assertEquals(fallback, sample(bitmap, tag, .1f))
            bitmap.recycle()
        }
        assertArrayEquals("Display must preserve the original PNG bytes", original, source.readBytes())
    }

    private fun screenshot(): Bitmap {
        val view = rule.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun sample(bitmap: Bitmap, tag: String, fraction: Float): Int {
        val bounds = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        return bitmap.getPixel((bounds.left + bounds.width * fraction).toInt(), bounds.center.y.toInt())
    }
}
