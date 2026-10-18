package dev.goodwy.rphone.view.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import dev.goodwy.rphone.view.screen.CalmCallAvatar
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android 17 hardware capture without Espresso hidden input APIs; no provider/Telecom/preference writes. */
@RunWith(AndroidJUnit4::class)
class WhiteContactPhotoDeviceTest {
    @Test fun transparentPhotosAndWarmCachePreserveArtworkAndFallbacksAcrossThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "dot.dialer.test")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val output = File(context.cacheDir, "photo-white-device-v$version").apply { mkdirs() }
        val source = File(output, "transparent-contact.png")
        val image = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        Canvas(image).drawRect(28f, 0f, 44f, 72f, Paint().apply { color = Color.BLACK })
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val original = source.readBytes()
        val uri = Uri.fromFile(source).toString()
        val opaque = File(output, "opaque-contact.png")
        val red = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        opaque.outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) }; red.recycle()
        val raw = ImageRequest.Builder(context).data(uri).callPhotoRevision(uri, 7).build()
        val pixels = (180 * context.resources.displayMetrics.density).toInt().coerceAtLeast(180)
        val cached = Bitmap.createScaledBitmap(BitmapFactory.decodeFile(source.absolutePath), pixels, pixels, false)
        assertEquals(0, Color.alpha(cached.getPixel(pixels / 5, pixels / 2)))
        requireNotNull(context.imageLoader.memoryCache)[requireNotNull(raw.memoryCacheKey)] = MemoryCache.Value(cached)
        val dark = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val positions = ConcurrentHashMap<String, Rect>()
        fun position(tag: String) = Modifier.onGloballyPositioned { positions[tag] = it.boundsInWindow() }
        fun screenshot() = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        fun sample(bitmap: Bitmap, tag: String, fraction: Float): Int {
            val bounds = requireNotNull(positions[tag])
            return bitmap.getPixel((bounds.left + bounds.width * fraction).toInt(), bounds.center.y.toInt())
        }
        fun background(bitmap: Bitmap): Int {
            val bounds = requireNotNull(positions["surface"])
            return bitmap.getPixel((bounds.left + bounds.width * .97f).toInt(), (bounds.top + bounds.height * .8f).toInt())
        }
        fun waitFor(check: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 10000
            while (!check()) {
                assertTrue("Hardware frame did not meet the expected condition", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(40)
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                CompositionLocalProvider(
                    LocalRillAvatarAppearance provides RillAvatarAppearance(showPicture = visible.value, showFirstLetter = true,
                        colorfulAvatars = false, modernPastelAvatars = true,
                        secondaryColorAvatars = false, googleContactColorAvatars = false),
                    LocalCallPhotoRevision provides 7,
                ) {
                    DotDialerTheme(systemDark = dark.value) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).then(position("surface")).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RillAvatar("Test Alexandru Butica", photoUri = uri, modifier = Modifier.size(180.dp).then(position("details")))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CalmCallAvatar(uri, "Test Alexandru Butica", "0700000001", 96.dp, position("call"))
                                RillAvatar("Test Alexandru Butica", photoUri = uri, modifier = Modifier.size(48.dp).then(position("list")))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RillAvatar("Test Alexandru Butica", photoUri = Uri.fromFile(opaque).toString(), modifier = Modifier.size(48.dp).then(position("opaque")))
                                RillAvatar("Test Alexandru Butica", modifier = Modifier.size(48.dp).then(position("missing")))
                                RillAvatar("Test Alexandru Butica", photoUri = "file:///missing.png", modifier = Modifier.size(48.dp).then(position("error")))
                            }
                        }
                    }
                }
            } }
            waitFor { positions.size == 7 }
            val fallback = modernPastelAvatarColor("Test Alexandru Butica").first.toArgb()
            for (theme in listOf(false, true)) {
                instrumentation.runOnMainSync { dark.value = theme }
                waitFor {
                    val bitmap = screenshot()
                    val ready = (Color.red(background(bitmap)) < 100) == theme &&
                        listOf("details", "call", "list").all { sample(bitmap, it, .2f) == Color.WHITE } &&
                        sample(bitmap, "opaque", .2f) == Color.RED
                    bitmap.recycle(); ready
                }
                val bitmap = screenshot()
                assertEquals("Actual hardware theme background", theme, Color.red(background(bitmap)) < 100)
                for (tag in listOf("details", "call", "list")) {
                    assertEquals("White backing $tag theme=$theme", Color.WHITE, sample(bitmap, tag, .2f))
                    assertEquals("Opaque artwork $tag theme=$theme", Color.BLACK, sample(bitmap, tag, .5f))
                }
                assertEquals(Color.RED, sample(bitmap, "opaque", .2f))
                assertEquals(fallback, sample(bitmap, "missing", .1f))
                assertEquals(fallback, sample(bitmap, "error", .1f))
                File(output, "${if (theme) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            instrumentation.runOnMainSync { visible.value = false }
            waitFor {
                val bitmap = screenshot()
                val hidden = listOf("details", "call", "list", "opaque").all { sample(bitmap, it, .1f) == fallback }
                bitmap.recycle(); hidden
            }
        }
        assertArrayEquals("Original PNG bytes", original, source.readBytes())
        File(output, "verification.json").writeText(JSONObject().put("package", context.packageName).put("versionCode", version)
            .put("themes", "light,dark").put("transparentWhite", true).put("rawCacheIsolated", true)
            .put("opaqueAndFallbackPreserved", true).put("sourceBytesPreserved", true)
            .put("contactWrites", 0).put("carrierCalls", 0).put("preferenceWrites", 0).toString())
    }
}
