package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.liquidglass.ShapeProvider
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.highlight.HighlightElement
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], application = Application::class, qualifiers = "w360dp-h360dp-mdpi")
class RoundButtonHighlightRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun darkRoundRimIsSofterWhileLightAndInteriorStayIdentical() {
        var dark by mutableStateOf(false)
        var original by mutableStateOf(true)
        var fill = 0
        rule.setContent {
            DotDialerTheme(systemDark = dark) {
                val color = MaterialTheme.colorScheme.surfaceContainerHigh
                fill = color.toArgb()
                val shape = CircleShape
                val highlight = if (original) Highlight.Default else roundButtonGlassHighlight()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(30.dp)) {
                    Box(Modifier.size(240.dp, 240.dp)
                        .then(HighlightElement(ShapeProvider { shape }) { highlight })
                        .background(color, shape))
                }
            }
        }
        for (theme in listOf(false, true)) {
            rule.runOnIdle { dark = theme; original = true }
            rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
            val before = capture()
            rule.runOnIdle { original = false }
            rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
            val after = capture()
            assertEquals("Panel fill must stay unchanged", fill, after.getPixel(150, 150))
            assertEquals(before.getPixel(150, 150), after.getPixel(150, 150))
            if (!theme) assertTrue("Light must be pixel-identical", before.sameAs(after))
            else {
                var oldEnergy = 0L; var newEnergy = 0L
                val base = android.graphics.Color.red(fill)
                for (y in 30 until 270) for (x in 30 until 270) {
                    oldEnergy += (android.graphics.Color.red(before.getPixel(x,y)) - base).coerceAtLeast(0)
                    newEnergy += (android.graphics.Color.red(after.getPixel(x,y)) - base).coerceAtLeast(0)
                }
                assertTrue("Real highlight must be drawn", oldEnergy > 50)
                assertTrue("Rim remains visible", newEnergy > 0)
                assertTrue("Dark highlight is substantially softer: $oldEnergy -> $newEnergy", newEnergy < oldEnergy * .55)
            }
            val directory = File("../evidence/local-verification/round-button-highlight-20261019").apply { mkdirs() }
            for ((name, bitmap) in listOf("before" to before, "after" to after))
                File(directory, "$name-dark$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }

    private fun capture(): Bitmap {
        lateinit var image: Bitmap
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            image = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        return image
    }
}
