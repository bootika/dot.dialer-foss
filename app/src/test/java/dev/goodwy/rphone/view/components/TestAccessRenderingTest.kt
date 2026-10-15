package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.TestAccessState
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
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class TestAccessRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun `blocked and unavailable states remain readable and retry reachable`() {
        var state by mutableStateOf(TestAccessState.BLOCKED); var count=0
        rule.setContent { DotDialerTheme(systemDark=false) { TestAccessScreen(state) { count++ } } }
        rule.onNodeWithText(rule.activity.getString(R.string.test_access_blocked)).assertIsDisplayed()
        rule.onNodeWithText("diler@dotdesign.ro").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.test_access_retry)).performClick()
        assertEquals(1,count)
        capture("blocked")
        rule.runOnIdle { state=TestAccessState.UNAVAILABLE }
        rule.onNodeWithText(rule.activity.getString(R.string.test_access_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.test_access_blocked)).assertDoesNotExist()
    }
    @Test @Config(qualifiers="w800dp-h360dp-land-mdpi")
    fun `large font landscape can scroll to phone fallback settings`() {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
            DotDialerTheme(systemDark=true) { TestAccessScreen(TestAccessState.BLOCKED) {} }
        } }
        rule.onNodeWithText(rule.activity.getString(R.string.test_access_phone_settings)).performScrollTo().assertIsDisplayed()
        capture("landscape-150")
    }
    private fun capture(name: String) {
        rule.waitForIdle(); rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            val directory=File(System.getProperty("user.dir"),"build/reports/visual/test-access");directory.mkdirs()
            File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
