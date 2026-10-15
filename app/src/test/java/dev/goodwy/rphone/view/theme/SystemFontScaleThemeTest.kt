package dev.goodwy.rphone.view.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SystemFontScaleThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `theme preserves changing Android font scale alongside interface preference`() {
        var androidFontScale by mutableFloatStateOf(1.15f)
        var observedFontScale = 0f
        var observedBodySize = 0f

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, androidFontScale)) {
                DotDialerTheme(systemDark = false, interfaceScale = 1.2f) {
                    observedFontScale = LocalDensity.current.fontScale
                    observedBodySize = MaterialTheme.typography.bodyLarge.fontSize.value
                    Text("Accessible text")
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(1.15f, observedFontScale, 0.001f)
            assertEquals(19.2f, observedBodySize, 0.001f)
        }

        composeRule.runOnUiThread { androidFontScale = 1.6f }
        composeRule.runOnIdle {
            assertEquals(1.6f, observedFontScale, 0.001f)
            assertEquals(19.2f, observedBodySize, 0.001f)
        }
    }
}
