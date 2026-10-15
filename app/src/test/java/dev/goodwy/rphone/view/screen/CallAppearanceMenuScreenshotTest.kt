package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
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
    qualifiers = "ro-rRO-w360dp-h240dp",
    application = Application::class,
)
class CallAppearanceMenuScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `light menu selects style and toggles droplets`() = verifyMenu(false, 1f)
    @Test fun `dark menu selects style and toggles droplets`() = verifyMenu(true, 1f)
    @Test fun `large text menu wraps labels and keeps switch accessible`() = verifyMenu(false, 2f)
    @Test @Config(qualifiers = "ro-rRO-w640dp-h240dp")
    fun `landscape menu keeps all actions accessible`() = verifyMenu(true, 2f)

    private fun verifyMenu(dark: Boolean, fontScale: Float) {
        var appearance by mutableStateOf(PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR)
        var droplets by mutableStateOf(true)
        var toggleCount = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            DotDialerTheme(systemDark = dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    CallAppearanceMenuContent(
                        appearance = appearance,
                        dropletsEnabled = droplets,
                        onDropletsChanged = { droplets = it; toggleCount++ },
                        onSelect = { appearance = it },
                    )
                }
            }
            }
        }
        val circular = composeRule.activity.getString(R.string.call_appearance_circular_avatar)
        val full = composeRule.activity.getString(R.string.call_appearance_full_photo)
        val drops = composeRule.activity.getString(R.string.ambient_droplets)
        composeRule.onNodeWithText(circular).assertIsSelected()
        composeRule.onNodeWithText(drops).performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithText(drops).assertIsOff().performClick()
        composeRule.onNodeWithText(drops).assertIsOn()
        composeRule.runOnIdle { org.junit.Assert.assertEquals("One callback per tap", 2, toggleCount) }
        composeRule.onNodeWithText(full).performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText(drops).performScrollTo()
        composeRule.mainClock.advanceTimeBy(500)

        val output = File(
            requireNotNull(System.getProperty("user.dir")),
            "build/reports/visual/call-appearance-menu-${if (dark) "dark" else "light"}-font$fontScale.png",
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
