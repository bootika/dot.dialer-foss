package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import dev.goodwy.rphone.view.theme.MyColors.cardColor
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
@Config(sdk = [36], qualifiers = "w360dp-h760dp", application = Application::class)
class PhotoScanScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun rendersRunningCompletedAndInterruptedStatesWithLargeFonts() {
        val state = mutableStateOf(PhotoScanState(PhotoScanStatus.Running, total = 1164, checked = 506, updated = 196, skipped = 304, failed = 6,
            failureCounts = mapOf(PhotoScanFailureKind.InvalidImage to 6)))
        val scale = mutableStateOf(1.15f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                DotDialerTheme(systemDark = false) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Surface(color = cardColor, shape = MaterialTheme.shapes.extraLarge) {
                            Column {
                                DotSettingsSwitchRow("High-resolution Google photos", "Connected to photos@example.test", true, {})
                                PhotoScanContent(state.value, true, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        capture("photo-scan-running.png")
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(status = PhotoScanStatus.Completed, checked = 1164, updated = 181, skipped = 977) }
        rule.waitForIdle()
        rule.onNodeWithText("Retry failed photos").assertIsDisplayed()
        capture("photo-scan-complete.png")
        rule.runOnIdle { state.value = state.value.copy(status = PhotoScanStatus.Failed, checked = 609, updated = 2, skipped = 606, failed = 1,
            failureCounts = mapOf(PhotoScanFailureKind.Network to 1), lastFailure = PhotoScanFailureKind.Network) }
        rule.waitForIdle()
        rule.onNodeWithText("Resume scan").assertIsDisplayed()
        capture("photo-scan-interrupted.png")
        rule.runOnIdle { scale.value = 1.5f }
        rule.waitForIdle()
        rule.onNodeWithText("Resume scan").assertIsDisplayed()
        capture("photo-scan-font150.png")
    }

    private fun capture(name: String) {
        val file = File(System.getProperty("user.dir"), "build/reports/visual/$name")
        file.parentFile!!.mkdirs()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            FileOutputStream(file).use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
