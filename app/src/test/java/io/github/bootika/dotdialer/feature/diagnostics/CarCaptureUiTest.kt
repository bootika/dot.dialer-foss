package io.github.bootika.dotdialer.feature.diagnostics

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarCaptureUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { stopKoin(); runBlocking { AppDiagnostics.selectLevel(io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel.NORMAL) } }
    @Test fun largeCaptureSharesTheFullFileAndGuideOpensSettingsInBothThemes() {
        val context=rule.activity
        ReflectionHelpers.getStaticField<AtomicBoolean>(AppDiagnostics::class.java,"initialized").set(false)
        AppDiagnostics.initialize(context); runBlocking { AppDiagnostics.clear(context) }
        File(context.filesDir,"dot_dialer_diagnostics.log").writeText("event | safe=true\n".repeat(40000))
        val prefs=PreferenceManager(context); startKoin { modules(module { single { prefs } }) }
        val navigator=Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,arrayOf(DestinationsNavigator::class.java)) { _,_,_ -> true } as DestinationsNavigator
        var dark by mutableStateOf(false); var font by mutableFloatStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,font)) { DotDialerTheme(systemDark=dark) { DiagnosticsScreen(navigator) } } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("diagnosticsShareFile").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        for(theme in listOf(false,true)) {
            rule.runOnIdle { dark=theme; font=if(theme) 2f else 1f }; rule.waitForIdle()
            rule.onNodeWithText(context.getString(R.string.copy_diagnostics)).performScrollTo().assertIsNotEnabled()
            rule.onNodeWithText(context.getString(R.string.diagnostics_use_file)).assertExists()
            rule.onNodeWithTag("diagnosticsShareFile").performScrollTo().assertIsEnabled()
            capture("car-capture-share-"+theme)
            rule.onNodeWithTag("carCaptureDeveloperOptions").performScrollTo().assertIsDisplayed()
            capture("car-capture-guide-"+theme)
        }
        rule.onNodeWithTag("carCaptureDeveloperOptions").performClick()
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,shadowOf(context).nextStartedActivity.action)
        rule.onNodeWithTag("diagnosticsShareFile").performScrollTo().performClick()
        if(File.separatorChar == '\\') {
            rule.waitUntil(10000) { org.robolectric.shadows.ShadowToast.getTextOfLatestToast() == context.getString(R.string.diagnostics_share_failed) }
            assertNull(shadowOf(context).peekNextStartedActivity())
            assertTrue(File(context.cacheDir,"diagnostic_reports").listFiles().orEmpty().isEmpty())
            rule.onNodeWithTag("diagnosticsShareFile").assertIsEnabled()
        } else {
            rule.waitUntil(10000) { shadowOf(context).peekNextStartedActivity()!=null }
            val chooser=shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER,chooser.action)
            @Suppress("DEPRECATION") val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND,send.action)
            assertTrue(File(context.cacheDir,"diagnostic_reports").listFiles()!!.single().readText().contains("bluetooth_packets=not_captured_by_app"))
        }
    }
    private fun capture(name: String)=rule.runOnIdle {
        val view=rule.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("build/reports/visual/car-logging-d125",name+".png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
}
