package io.github.bootika.dotdialer.diagnostics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticLevelControlTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @org.junit.Before fun freshApplicationFixture() {
        org.robolectric.util.ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(AppDiagnostics::class.java, "initialized").set(false)
    }
    @Test fun testControlSelectsAllLevelsAndPersists() {
        AppDiagnostics.initialize(rule.activity)
        runBlocking { AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL) }
        content(false, 1f, true)
        for (level in listOf(DiagnosticLevel.DEBUG, DiagnosticLevel.VERBOSE, DiagnosticLevel.NORMAL)) {
            rule.onNodeWithText(level.name.lowercase().replaceFirstChar(Char::uppercaseChar)).performClick()
            rule.waitUntil(5000) { AppDiagnostics.detailSettings.current() == level }
            assertEquals(level, DiagnosticDetailSettings(rule.activity).also { it.load() }.current())
        }
        capture("light-normal")
    }
    @Test fun darkLargeFontAndReleaseControlAbsent() {
        AppDiagnostics.initialize(rule.activity)
        content(true, 2f, false)
        rule.onNodeWithText("Verbose").assertDoesNotExist()
        capture("release-no-control")
    }
    @Test fun darkLargeFontTestControlRemainsUsable() {
        AppDiagnostics.initialize(rule.activity)
        runBlocking { AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL) }
        content(true, 2f, true)
        rule.onNodeWithText("Debug").performClick()
        rule.waitUntil(5000) { AppDiagnostics.detailSettings.current() == DiagnosticLevel.DEBUG }
        capture("dark-font200-debug")
        runBlocking { AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL) }
    }
    private fun content(dark: Boolean, font: Float, testBuild: Boolean) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) { DiagnosticLevelControl(testBuild) }
                    }
                }
            }
        }
    }
    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/diagnostic-level/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
