package dev.goodwy.rphone.view.screen

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.telecom.Call
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.controller.util.CallSimIdentityPreference as SimPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.CallPhotoAppearanceSettings
import dev.goodwy.rphone.view.screen.settings.settingsFocusIndex
import dev.goodwy.rphone.view.screen.settings.components.SettingsEditor
import dev.goodwy.rphone.view.screen.settings.components.settingsOptions
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29, 36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class CallSimIdentitySettingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = RuntimeEnvironment.getApplication()
    @Volatile private var observed: PhoneAccount? = null
    private fun handle(id: String) = PhoneAccountHandle(ComponentName("example.phone", "Calls"), id)
    private fun prefs() = PreferenceManager(context).also {
        runBlocking { it.restoreAllPreferences(emptyMap(), replacePortable = true) }
    }
    private fun oneSim(): PhoneAccountHandle {
        shadowOf(context).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        shadowOf(context.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfoList(listOf(
            SubscriptionInfoBuilder.newBuilder().setId(1).setSimSlotIndex(0).buildSubscriptionInfo()))
        return handle("A").also {
            context.getSystemService(TelecomManager::class.java).registerPhoneAccount(
                PhoneAccount.builder(it, "Network A").setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION).build())
        }
    }

    @Test fun defaultOffSwitchUpdatesKnownSingleSimLiveAndPersistsWithoutChangingCallStyle() {
        val prefs = prefs()
        val priorStyle = prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE,
            PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE)
        val handle = oneSim()
        var pending by mutableStateOf<PhoneAccountHandle?>(handle)
        var liveCall by mutableStateOf<Call?>(null)
        assertFalse(prefs.getBoolean(SimPreference.KEY, SimPreference.DEFAULT))
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                Column {
                    CallPhotoAppearanceSettings(prefs, "full_photo", listOf("Full photo" to "full_photo")) {}
                    val account = rememberCallScreenPhoneAccount(liveCall, pending, prefs)
                    SideEffect { observed = account }
                    Text(account?.label?.toString().orEmpty())
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Network A").assertDoesNotExist()
        val title = context.getString(dev.goodwy.rphone.R.string.call_screen_always_show_sim)
        val toggle = rule.onNode(isToggleable() and hasAnyAncestor(hasText(title)))
        toggle.assertIsOff()
        rule.onNodeWithText(title).performClick()
        rule.waitUntil(5_000) { observed?.accountHandle == handle }
        toggle.assertIsOn()
        assertEquals(true, runBlocking { prefs.getPersistedPreferences() }[SimPreference.KEY])
        assertTrue(PreferenceManager(context).getBoolean(SimPreference.KEY, SimPreference.DEFAULT))
        for (state in listOf(Call.STATE_RINGING, Call.STATE_DIALING, Call.STATE_ACTIVE, Call.STATE_HOLDING)) {
            rule.runOnIdle {
                val call = Shadow.newInstanceOf(Call::class.java)
                val details = Shadow.newInstanceOf(Call.Details::class.java)
                ReflectionHelpers.setField(details, "mAccountHandle", handle)
                if (android.os.Build.VERSION.SDK_INT >= 31) ReflectionHelpers.setField(details, "mState", state)
                ReflectionHelpers.setField(call, "mDetails", details)
                ReflectionHelpers.setField(call, "mState", state)
                liveCall = call; pending = null
            }
            rule.waitForIdle()
            assertEquals("Known account retained in state $state", handle, observed?.accountHandle)
        }
        rule.onNodeWithText(title).performClick()
        rule.waitUntil(5_000) { observed == null }
        toggle.assertIsOff()
        assertEquals(false, runBlocking { prefs.getPersistedPreferences() }[SimPreference.KEY])
        assertEquals(1, settingsFocusIndex("C10", SettingsEditor.CALL_UI))
        assertTrue(settingsOptions.any { it.id == "C10" && !it.advanced && it.editor == SettingsEditor.CALL_UI })
        assertEquals(priorStyle,
            prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE))
    }

    @Test fun missingAccountNeverGetsInventedAndNonSimIdentityKeepsItsExistingBehavior() {
        val prefs = prefs()
        oneSim()
        val voip = handle("voip")
        context.getSystemService(TelecomManager::class.java).registerPhoneAccount(
            PhoneAccount.builder(voip, "Internet call").setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER).build())
        assertEquals(voip, dev.goodwy.rphone.controller.util.loadPhoneAccount(rule.activity, voip)?.accountHandle)
        var pending by mutableStateOf<PhoneAccountHandle?>(null)
        rule.setContent {
            val account = rememberCallScreenPhoneAccount(null, pending, prefs)
            SideEffect { observed = account }
            Text(account?.label?.toString().orEmpty())
        }
        rule.waitForIdle()
        assertNull(observed)
        prefs.setBoolean(SimPreference.KEY, true)
        runBlocking { prefs.getPersistedPreferences() }
        rule.waitForIdle()
        assertNull(observed)
        rule.runOnIdle { pending = handle("removed") }
        rule.waitForIdle()
        assertNull(observed)
        prefs.setBoolean(SimPreference.KEY, false)
        runBlocking { prefs.getPersistedPreferences() }
        rule.runOnIdle { pending = voip }
        rule.waitForIdle()
        rule.waitUntil(5_000) { observed?.accountHandle == voip }
        rule.onNodeWithText("Internet call").assertExists()
    }
}
