package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.CallMessageSettings
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class CallMessageRoutingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val prefs get() = PreferenceManager(RuntimeEnvironment.getApplication())
    @After fun clear() = runBlocking<Unit> { prefs.restoreAllPreferences(emptyMap(), replacePortable = true) }

    private fun scene(mode: Int, installed: Boolean = false, failLaunch: Boolean = false, allowed: () -> Boolean = { true }): Triple<MutableList<String>, MutableList<Intent>, MutableList<String>> {
        val manager = PreferenceManager(RuntimeEnvironment.getApplication(), featureAllowed = { allowed() })
        runBlocking { manager.restoreAllPreferences(mapOf(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL to mode), replacePortable = true) }
        if (installed) installMessageApp(rule.activity, "com.whatsapp")
        val sms = mutableListOf<String>(); val intents = mutableListOf<Intent>(); val declines = mutableListOf<String>()
        val context = object : ContextWrapper(rule.activity) {
            override fun startActivity(intent: Intent) {
                if (failLaunch && intent.`package` == "com.whatsapp") throw ActivityNotFoundException()
                intents += intent
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                DotDialerTheme {
                    val request = rememberCallMessageAction(manager, "+40720123456", { sms += it }, { declines += "decline" })
                    Column {
                        Button(onClick = { request("Revin mai târziu", true) }) { Text("Reply") }
                        Button(onClick = { request(null, false) }) { Text("Message") }
                    }
                }
            }
        }
        return Triple(sms, intents, declines)
    }

    @Test fun `SMS default delegates quick reply and active message separately`() {
        val (sms, intents, declines) = scene(0)
        rule.onNodeWithText("Reply").performClick()
        rule.runOnIdle { assertEquals(listOf("Revin mai târziu"), sms); assertTrue(intents.isEmpty()); assertTrue(declines.isEmpty()) }
        rule.onNodeWithText("Message").performClick()
        rule.runOnIdle { assertEquals("smsto", intents.single().data!!.scheme) }
    }

    @Test fun `Ask cancel leaves ringing call untouched and selecting WhatsApp preserves draft`() {
        val (sms, intents, declines) = scene(2, installed = true)
        rule.onNodeWithText("Reply").performClick()
        rule.onNodeWithText("Tap Send in WhatsApp to send your message.").assertIsDisplayed()
        capture("chooser-light")
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertTrue(sms.isEmpty()); assertTrue(intents.isEmpty()); assertTrue(declines.isEmpty()) }
        rule.onNodeWithText("Reply").performClick()
        rule.onNodeWithText("WhatsApp", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertEquals(1, declines.size); assertTrue(sms.isEmpty()); assertEquals("Revin mai târziu", intents.single().data!!.getQueryParameter("text")) }
    }

    @Test fun `missing WhatsApp offers SMS only without sending until explicitly selected`() {
        val (sms, intents, declines) = scene(1)
        rule.onNodeWithText("Reply").performClick()
        rule.onNodeWithText("WhatsApp", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertTrue(sms.isEmpty()); assertTrue(intents.isEmpty()); assertTrue(declines.isEmpty()) }
        rule.onNodeWithText("SMS").performClick()
        rule.runOnIdle { assertEquals(listOf("Revin mai târziu"), sms); assertTrue(intents.isEmpty()) }
    }

    @Test fun `failed WhatsApp launch offers composer and never declines twice or sends native SMS`() {
        val (sms, intents, declines) = scene(1, installed = true, failLaunch = true)
        rule.onNodeWithText("Reply").performClick()
        rule.onNodeWithText("SMS").performClick()
        rule.runOnIdle { assertEquals(1, declines.size); assertTrue(sms.isEmpty()); assertEquals("Revin mai târziu", intents.single().getStringExtra("sms_body")) }
    }

    @Test fun `settings persist channel and show the Send requirement`() {
        val manager = prefs
        runBlocking { manager.restoreAllPreferences(emptyMap(), replacePortable = true) }
        rule.setContent { DotDialerTheme { Column { CallMessageSettings(manager) } } }
        rule.onNodeWithText("WhatsApp", useUnmergedTree = true).performClick()
        rule.waitUntil { manager.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0) == 1 }
        rule.onNodeWithText("Choose SMS or WhatsApp for call-screen messages and quick replies. In WhatsApp, tap Send to send the message.").assertIsDisplayed()
        capture("settings-light")
    }
    @Test fun `Free and Power use SMS despite a retained WhatsApp preference`() {
        val (sms, intents, declines) = scene(1, installed = true, allowed = { false })
        rule.onNodeWithText("Reply").performClick()
        rule.runOnIdle { assertEquals(listOf("Revin mai târziu"), sms); assertTrue(intents.isEmpty()); assertTrue(declines.isEmpty()) }
    }

    @Test fun `expiry after chooser opens requires explicit SMS selection and never opens WhatsApp`() {
        var allowed = true
        val (sms, intents, declines) = scene(2, installed = true, allowed = { allowed })
        rule.onNodeWithText("Reply").performClick()
        rule.runOnIdle { allowed = false }
        rule.onNodeWithText("WhatsApp", useUnmergedTree = true).performClick()
        rule.onNodeWithText("WhatsApp", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertTrue(sms.isEmpty()); assertTrue(intents.isEmpty()); assertTrue(declines.isEmpty()) }
        rule.onNodeWithText("SMS").performClick()
        rule.runOnIdle { assertEquals(listOf("Revin mai târziu"), sms); assertTrue(intents.isEmpty()) }
    }

    @Test fun `locked channel setting requests the Complete plan instead of writing preferences`() {
        val requested = mutableListOf<io.github.bootika.dotdialer.core.license.PlanFeature>()
        val manager = PreferenceManager(RuntimeEnvironment.getApplication(), featureAllowed = { false }, onPlanRequired = { requested += it })
        rule.setContent { DotDialerTheme { Column { CallMessageSettings(manager) } } }
        rule.onNodeWithText("Upgrade to Complete").assertIsDisplayed()
        capture("settings-locked-light")
        rule.onNodeWithText("Call-screen messages").assertHasClickAction().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_MESSAGE_CHANNEL), requested); assertEquals(0, manager.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0)) }
    }

    @Test fun `locked appearance control opens plans without changing saved state`() {
        val requested = mutableListOf<io.github.bootika.dotdialer.core.license.PlanFeature>()
        val feature = io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE
        val manager = PreferenceManager(RuntimeEnvironment.getApplication(), featureAllowed = { false }, onPlanRequired = { requested += it })
        val before = runBlocking { manager.getPersistedPreferences() }
        rule.setContent { DotDialerTheme { Column {
            dev.goodwy.rphone.view.screen.settings.SettingsPreferenceSwitch(manager,
                PreferenceManager.KEY_LG_CALL_SCREEN, false, dev.goodwy.rphone.R.string.liquid_glass_call_screen)
        } } }
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.liquid_glass_call_screen))
            .assertHasClickAction().assertIsEnabled().performClick()
        capture("appearance-locked-light")
        rule.runOnIdle { assertEquals(listOf(feature), requested) }
        assertEquals(before, runBlocking { manager.getPersistedPreferences() })
    }

    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(360), view.height.coerceAtLeast(800), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
        val windows = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
        windows.forEach { window ->
            val location = IntArray(2); window.getLocationOnScreen(location)
            canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat()); window.draw(canvas); canvas.restore()
        }
        val file = File("../evidence/local-verification/call-message-channel-20261017/visual/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

}
