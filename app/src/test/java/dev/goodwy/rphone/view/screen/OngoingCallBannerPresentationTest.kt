package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.view.components.OngoingCallBanner
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OngoingCallBannerPresentationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun themeFontInsetsAndIndependentTargets() {
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var expanded = 0
        var ended = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Box(Modifier.fillMaxSize()) { OngoingCallBanner({ expanded++ }, { ended++ }) }
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = theme; font = scale }
            rule.waitForIdle()
            val card = rule.onNodeWithTag("ongoingCallCard", true).fetchSemanticsNode().boundsInRoot
            val end = rule.onNodeWithTag("ongoingCallEnd", true).fetchSemanticsNode().boundsInRoot
            val back = rule.onNodeWithTag("ongoingCallReturn", true).fetchSemanticsNode().boundsInRoot
            assertTrue(end.height >= 48f && end.width >= 48f)
            assertTrue(back.height >= 48f)
            assertTrue(card.bottom - end.bottom >= 12f)
            assertTrue(card.right - end.right >= 12f)
            assertTrue(end.left - back.right >= 12f)
            rule.onNodeWithTag("ongoingCallReturn").performClick()
            assertEquals(expanded, ended + 1)
            rule.onNodeWithTag("ongoingCallEnd").performClick()
            assertEquals(expanded, ended)
            rule.onNodeWithTag("ongoingCallEnd").performTouchInput { down(center); cancel() }
            assertEquals(expanded, ended)
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/visual/ongoing-banner-d81/${if (theme) "dark" else "light"}-${(scale * 100).toInt()}.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}

