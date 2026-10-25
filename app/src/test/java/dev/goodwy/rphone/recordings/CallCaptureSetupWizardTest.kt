package dev.goodwy.rphone.recordings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.os.LocaleList
import dev.goodwy.rphone.R
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.view.screen.recordings.CaptureSetupWizard
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class CallCaptureSetupWizardTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun wizardKeepsNavigationReachableAcrossThemesStepsAndLargeFonts() {
        var step by mutableIntStateOf(0);var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f)
        var language by mutableStateOf("en")
        fun localized()=rule.activity.createConfigurationContext(Configuration(rule.activity.resources.configuration).apply {setLocales(LocaleList.forLanguageTags(language))})
        rule.setContent {val context=localized();CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides rule.activity,LocalDensity provides Density(1f,font),LocalContext provides context,LocalConfiguration provides context.resources.configuration){DotDialerTheme(systemDark=dark){Surface(Modifier.fillMaxSize()){
            key(step,dark,font,language){CaptureSetupWizard({},previewStep=step)}
        }}}}
        val output=File("build/reports/visual/capture-wizard").apply{mkdirs()}
        for(locale in listOf("en","ro"))for(theme in listOf(false,true))for(scale in listOf(1f,1.5f))for(page in 0..4) {
            rule.runOnIdle{language=locale;dark=theme;font=scale;step=page}
            val context=localized()
            when(page){4->rule.onNodeWithText(context.getString(R.string.capture_wizard_done)).assertIsDisplayed();3->rule.onNodeWithText(context.getString(R.string.capture_activate)).assertIsDisplayed();else->rule.onNodeWithContentDescription(context.getString(R.string.capture_next)).assertIsDisplayed()}
            rule.runOnIdle {
                val view=rule.activity.window.decorView
                val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(output,"wizard-$locale-$page-${if(theme)"dark"else"light"}-${(scale*100).toInt()}.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
        rule.runOnIdle {language="ro";step=3}
        rule.onNodeWithTag("capture-network-help").performScrollTo().performClick()
        val recovery=localized().getString(R.string.capture_network_recovery).split("\n\n")
        for(instruction in recovery)rule.onNodeWithText(instruction).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(localized().getString(R.string.capture_activate)).assertIsDisplayed()
    }
}
