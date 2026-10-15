package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [36],
    qualifiers = "w412dp-h915dp",
    application = Application::class,
)
class VisualPaletteSelectorsScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `render accent selector in light theme`() = renderAccentMenu(dark = false)

    @Test
    fun `render accent selector in dark theme`() = renderAccentMenu(dark = true)

    @Test
    fun `render avatar selector in light theme`() = renderAvatarMenu(dark = false)

    @Test
    fun `render avatar selector in dark theme`() = renderAvatarMenu(dark = true)

    private fun renderAccentMenu(dark: Boolean) {
        composeRule.setContent {
            DotDialerTheme(systemDark = dark) {
                Surface(
                    modifier = Modifier.width(360.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    DotVisualChoiceMenuRows(
                        choices = listOf(
                            "Jade" to "jade",
                            "Blue" to "blue",
                            "Electric Iris" to "violet",
                            "Ruby" to "coral",
                        ),
                        selectedValue = "violet",
                        onValueChange = {},
                        preview = { key, selected -> AccentSwatch(accentPreviewColor(key), selected) },
                    )
                }
            }
        }
        captureMenu("accent", dark)
    }

    private fun renderAvatarMenu(dark: Boolean) {
        composeRule.setContent {
            DotDialerTheme(systemDark = dark) {
                val jadeColor = MaterialTheme.colorScheme.primaryContainer
                Surface(
                    modifier = Modifier.width(360.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    DotVisualChoiceMenuRows(
                        choices = listOf(
                            "Multicolor" to AVATAR_PALETTE_MULTICOLOR,
                            "App color" to AVATAR_PALETTE_APP_COLOR,
                        ),
                        selectedValue = AVATAR_PALETTE_MULTICOLOR,
                        onValueChange = {},
                        preview = { palette, _ ->
                            AvatarPalettePreview(
                                colors = avatarPalettePreviewColors(palette, jadeColor),
                            )
                        },
                    )
                }
            }
        }
        captureMenu("avatar", dark)
    }

    private fun captureMenu(name: String, dark: Boolean) {
        val output = File(
            requireNotNull(System.getProperty("user.dir")),
            "build/reports/visual/$name-${if (dark) "dark" else "light"}.png",
        )
        requireNotNull(output.parentFile).mkdirs()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val view = composeRule.activity.window.decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }
}
