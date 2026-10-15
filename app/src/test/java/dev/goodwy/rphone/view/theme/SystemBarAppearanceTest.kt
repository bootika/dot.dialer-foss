package dev.goodwy.rphone.view.theme

import android.app.Application
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SystemBarAppearanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `actual window glyph flags follow app theme and recover on focus`() {
        val dark = mutableStateOf(false)
        rule.setContent { DotDialerTheme(systemDark = dark.value) { Text("Test Alexandru Butica") } }
        fun assertBars(light: Boolean) {
            val bars = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertEquals(light, bars.isAppearanceLightStatusBars)
            assertEquals(light, bars.isAppearanceLightNavigationBars)
        }
        rule.runOnIdle {
            assertBars(true)
            assertSame(rule.activity, ContextWrapper(ContextWrapper(rule.activity)).systemBarActivity())
            assertNull(Application().systemBarActivity())
        }
        rule.runOnUiThread { dark.value = true }
        rule.runOnIdle { assertBars(false) }
        rule.runOnUiThread { dark.value = false }
        rule.runOnIdle {
            assertBars(true)
            applySystemBarIconAppearance(rule.activity.window, true)
            assertBars(false)
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(
                rule.activity.window.decorView.viewTreeObserver, "dispatchOnWindowFocusChange",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true),
            )
            assertBars(true)
        }
    }
}
