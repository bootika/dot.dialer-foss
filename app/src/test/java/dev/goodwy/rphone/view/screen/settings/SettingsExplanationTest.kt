package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.DotDialerTheme
import dev.goodwy.rphone.view.screen.recordings.CaptureSetupWizard
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class SettingsExplanationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun bootstrapWithoutWifiReportsPrerequisiteInsteadOfStartingDiscovery() {
        val backend=dev.goodwy.rphone.recordings.IntegratedCallCapture.get(rule.activity)
        backend.enable()
        android.provider.Settings.Global.putInt(rule.activity.contentResolver,"adb_wifi_enabled",0)
        try {
            kotlinx.coroutines.runBlocking {backend.activate()}
            Assert.assertEquals("network",backend.state.value.error)
            Assert.assertEquals("idle",backend.state.value.phase)
            Assert.assertFalse(java.io.File(rule.activity.noBackupFilesDir,"call-capture/session.json").exists())
        } finally {kotlinx.coroutines.runBlocking {backend.disable()}}
    }

    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(350)
        rule.waitForIdle()
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val folder=java.io.File("build/reports/visual/settings-guidance").apply {mkdirs()}
            java.io.File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

    @Test fun leavingSetupWithWirelessDebuggingOnRequiresCleanupRatherThanSilentExit() {
        val backend=dev.goodwy.rphone.recordings.IntegratedCallCapture.get(rule.activity)
        backend.enable()
        android.provider.Settings.Global.putInt(rule.activity.contentResolver,"adb_wifi_enabled",1)
        var closed=0
        try {
            rule.setContent {DotDialerTheme {Surface {CaptureSetupWizard({closed++})}}}
            rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()}
            rule.onNodeWithText(rule.activity.getString(R.string.capture_wireless_pending)).assertIsDisplayed()
            rule.runOnIdle {Assert.assertEquals(0,closed)}
            rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
            android.provider.Settings.Global.putInt(rule.activity.contentResolver,"adb_wifi_enabled",0)
            rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()}
            rule.runOnIdle {Assert.assertEquals(1,closed)}
        } finally {
            android.provider.Settings.Global.putInt(rule.activity.contentResolver,"adb_wifi_enabled",0)
            kotlinx.coroutines.runBlocking {backend.disable()}
        }
    }

    @Test fun readingGoogleHelpDoesNotEnableSourceOrRequestAccess() {
        var changed=0
        val context=rule.activity
        rule.setContent { DotDialerTheme { Surface {
            DotSettingsSwitchRow(context.getString(R.string.high_res_call_photos_title),checked=false,
                onCheckedChange={changed++})
        } } }
        rule.onNodeWithText(context.getString(R.string.guide_more)).performClick()
        rule.onNodeWithText(context.getString(R.string.guide_google)).assertIsDisplayed()
        screenshot("google-help")
        rule.runOnIdle { Assert.assertEquals(0,changed) }
        rule.onNodeWithText(context.getString(R.string.guide_more)).performClick()
        rule.onNodeWithText(context.getString(R.string.guide_google)).assertDoesNotExist()
    }

    @Test fun helpAndThreeStepNavigationRemainAvailableInEveryLanguage() {
        var language by mutableStateOf("en")
        var page by mutableIntStateOf(1)
        fun localized()=rule.activity.createConfigurationContext(Configuration(rule.activity.resources.configuration)
            .apply { setLocales(LocaleList.forLanguageTags(language)) })
        rule.setContent {
            val context=localized()
            CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides rule.activity,
                LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
                DotDialerTheme { Surface(Modifier.fillMaxSize()) {
                    key(language,page) { CaptureSetupWizard({},previewStep=page) }
                } }
            }
        }
        for(locale in listOf("en","ro","es","pt-BR","fr","de","it","id","tr","pl","vi")) {
            for(step in listOf(1,2,3,4)) {
                rule.runOnIdle { language=locale;page=step }
                val context=localized()
                val stage=if(step==4)3 else if(step<=1)1 else 2
                rule.onNodeWithText(context.getString(R.string.guide_stage,stage)).assertIsDisplayed()
                when(step) {
                    4->rule.onNodeWithText(context.getString(R.string.capture_wizard_done)).assertIsDisplayed()
                    3->rule.onNodeWithText(context.getString(R.string.capture_activate)).assertIsDisplayed()
                    else->rule.onNodeWithContentDescription(context.getString(R.string.capture_next)).assertIsDisplayed()
                }
                if(step==1) {
                    screenshot("wizard-$locale")
                    rule.onNodeWithContentDescription(context.getString(R.string.capture_next)).performClick()
                    rule.onNodeWithText(context.getString(R.string.guide_stage,2)).assertIsDisplayed()
                    rule.onNodeWithText(context.getString(R.string.capture_wizard_pair_title)).assertIsDisplayed()
                }
            }
        }
    }
}
