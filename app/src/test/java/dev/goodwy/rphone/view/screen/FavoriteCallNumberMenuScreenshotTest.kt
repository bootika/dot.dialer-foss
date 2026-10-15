package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
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
    qualifiers = "ro-w360dp-h180dp",
    application = Application::class,
)
class FavoriteCallNumberMenuScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `render favorite call number as selection state`() {
        composeRule.setContent {
            DotDialerTheme(systemDark = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                ) {
                    HomeMenuAction(
                        label = "Selectează numărul pentru apel",
                        icon = Icons.Default.CheckCircle,
                        subtitle = "Selectat: 0722 222 222",
                        onClick = {},
                    )
                }
            }
        }

        val output = File(
            requireNotNull(System.getProperty("user.dir")),
            "build/reports/visual/favorite-call-number-selection-ro.png",
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
