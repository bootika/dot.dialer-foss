package io.github.bootika.dotdialer.feature.diagnostics

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsLargeJournalTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { stopKoin() }

    @Test fun largeJournalIsVirtualizedCopiedInFullAndClearedOnlyAfterConfirmation() = verify(false, 1f)
    @Test fun darkFont200JournalAndControlsRemainUsable() = verify(true, 2f)

    private fun verify(dark: Boolean, fontScale: Float) {
        val context = rule.activity
        ReflectionHelpers.getStaticField<AtomicBoolean>(AppDiagnostics::class.java, "initialized").set(false)
        AppDiagnostics.initialize(context)
        runBlocking { AppDiagnostics.clear(context) }
        val journal = (1..2100).joinToString("\n") {
            "2026-10-06T08:00:00Z | call_audio | route=1 | bluetooth_available=false | bluetooth_permission=true | event=$it"
        }
        File(context.filesDir, "dot_dialer_diagnostics.log").writeText(journal)
        val prefs = PreferenceManager(context)
        startKoin { modules(module { single { prefs } }) }
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, _, _ -> true } as DestinationsNavigator
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                DotDialerTheme(systemDark = dark) { DiagnosticsScreen(navigator) }
            }
        }
        val copy = context.getString(dev.goodwy.rphone.R.string.copy_diagnostics)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(copy).fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        }
        rule.onNodeWithText(copy).performScrollTo().performClick()
        capture("${if (dark) "dark-font200" else "light-font100"}-controls")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
        Assert.assertTrue(copied.startsWith("DOT DIALER DIAGNOSTICS"))
        Assert.assertTrue(copied.endsWith(journal))
        Assert.assertTrue("Only viewport blocks laid out", rule.onAllNodesWithTag("diagnosticReportBlock").fetchSemanticsNodes().size < 8)
        val count = prepareDiagnosticReport(copied).blocks.size
        rule.onNodeWithTag("diagnosticsList").performScrollToIndex(count + 1)
        rule.onAllNodesWithText("event=2100", substring = true).assertCountEquals(1)
        capture("${if (dark) "dark-font200" else "light-font100"}-journal-end")
        Assert.assertTrue(rule.onAllNodesWithTag("diagnosticReportBlock").fetchSemanticsNodes().size < 8)
        rule.onNodeWithTag("diagnosticsList").performScrollToIndex(0)
        val clear = context.getString(dev.goodwy.rphone.R.string.clear_diagnostics)
        rule.onNodeWithText(clear).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.cancel)).performClick()
        Assert.assertEquals(journal, File(context.filesDir, "dot_dialer_diagnostics.log").readText())
        rule.onNodeWithText(clear).performClick()
        rule.onAllNodesWithText(clear).onLast().performClick()
        rule.waitUntil(5_000) { File(context.filesDir, "dot_dialer_diagnostics.log").readText().isEmpty() }
        rule.waitForIdle()
    }

    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File(System.getProperty("user.dir"), "build/reports/visual/diagnostics-lazy/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
