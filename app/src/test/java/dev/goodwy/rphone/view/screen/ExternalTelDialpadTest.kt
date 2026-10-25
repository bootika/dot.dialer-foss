package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.NavigationTarget
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class ExternalTelDialpadTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun incomingGermanLocalLinkReplacesMountedDraftWithInternationalNumberWhileRoaming() {
        val context=rule.activity.application
        val telephony=shadowOf(context.getSystemService(TelephonyManager::class.java))
        telephony.setSimCountryIso("ro");telephony.setNetworkCountryIso("de")
        val vm=MainViewModel(PreferenceManager(context))
        val incoming=mutableStateOf<String?>(null)
        rule.setContent { val state=rememberDialpadNumber(incoming.value) { "03614283555" }; Text(state.value) }
        rule.onNodeWithText("03614283555").assertExists()
        rule.runOnIdle {
            incoming.value=(vm.getNavigationTarget(Intent(Intent.ACTION_DIAL,Uri.parse("tel:030266424242")),context) as NavigationTarget.Dialpad).number
        }
        rule.onNodeWithText("+4930266424242").assertExists()
        rule.runOnIdle {
            incoming.value=(vm.getNavigationTarget(Intent(Intent.ACTION_DIAL,Uri.parse("tel:+40722123456")),context) as NavigationTarget.Dialpad).number
        }
        rule.onNodeWithText("+40722123456").assertExists()
    }
}
