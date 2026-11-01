package dev.goodwy.rphone.view.components

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telecom.PhoneAccountHandle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29, 36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class SimChoiceRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { stopKoin() }

    @Test fun `selector keeps scopes exclusive and remains usable in dark and large text`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        startKoin { modules(module { single { prefs } }) }
        val accounts = listOf("A", "B").map { id ->
            val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), id)
            CallingAccountInfo(handle, handle.toPreferenceKey(), "Network $id", null, 0, null, true)
        }
        val dark = mutableStateOf(false)
        val font = mutableStateOf(1f)
        var selected: PhoneAccountHandle? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font.value)) {
                DotDialerTheme(systemDark = dark.value, interfaceScale = font.value) {
                    SimPickerDialog({}, { selected = it }, "+40700000011", accounts)
                }
            }
        }
        rule.waitForIdle()
        capture("light")
        rule.runOnIdle { dark.value = true; font.value = 1.5f }
        rule.waitForIdle()
        capture("dark-large")
        rule.onNodeWithText("Remember for this number").performClick()
        rule.onAllNodesWithText("Remember for this number").onLast().performClick()
        rule.onNodeWithText("Network B").performClick()
        rule.runOnIdle { assertEquals(accounts[1].handle, selected) }
    }

    @Test fun `ultracompact selector defaults to number and orders number all once without global overwrite`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        kotlinx.coroutines.runBlocking { prefs.restoreAllPreferences(
            mapOf(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT to "existing-account"), replacePortable = true) }
        startKoin { modules(module { single { prefs } }) }
        val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), "A")
        val account = CallingAccountInfo(handle, handle.toPreferenceKey(), "Network A", null, 0, null, true)
        var selected: PhoneAccountHandle? = null
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                SimPickerDialog({}, { selected = it }, "+40700000011", listOf(account))
            }
        }
        rule.onNodeWithText("Use for all numbers").assertDoesNotExist()
        rule.onNodeWithText("This call only").assertDoesNotExist()
        rule.onNodeWithText("Remember for this number").performClick()
        val all = rule.onNodeWithText("Use for all numbers").fetchSemanticsNode().boundsInRoot
        val number = rule.onAllNodesWithText("Remember for this number").onLast().fetchSemanticsNode().boundsInRoot
        val once = rule.onNodeWithText("This call only").fetchSemanticsNode().boundsInRoot
        assertTrue(number.bottom <= all.top && all.bottom <= once.top)
        rule.onAllNodesWithText("Remember for this number").onLast().performClick()
        rule.runOnIdle {
            assertEquals("existing-account", prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null))
            assertNull(prefs.getFavoriteSimForNumber("+40700000011"))
            assertNull(selected)
        }
        rule.onNodeWithText("Network A").performClick()
        kotlinx.coroutines.runBlocking { prefs.getPersistedPreferences() }
        val saved = PreferenceManager(RuntimeEnvironment.getApplication())
        rule.runOnIdle {
            assertEquals(handle, selected)
            assertEquals("existing-account", saved.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null))
            assertEquals(account.key, saved.getFavoriteSimForNumber("+40700000011"))
        }
    }

    @Test fun `explicit once never saves a default and cancel does not call`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        kotlinx.coroutines.runBlocking { prefs.restoreAllPreferences(emptyMap(), replacePortable = true) }
        startKoin { modules(module { single { prefs } }) }
        val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), "A")
        val account = CallingAccountInfo(handle, handle.toPreferenceKey(), "Network A", null, 0, null, true)
        var calls = 0
        var cancelled = 0
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                SimPickerDialog({ cancelled++ }, { calls++ }, "+40700000011", listOf(account))
            }
        }
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertEquals(1, cancelled); assertEquals(0, calls) }
        rule.onNodeWithText("Remember for this number").performClick()
        rule.onNodeWithText("This call only").performClick()
        rule.onNodeWithText("Network A").performClick()
        kotlinx.coroutines.runBlocking { prefs.getPersistedPreferences() }
        rule.runOnIdle {
            assertEquals(1, calls)
            assertNull(prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null))
            assertNull(prefs.getFavoriteSimForNumber("+40700000011"))
        }
    }

    @Test fun `explicit all numbers from dropdown persists global without a number override`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        kotlinx.coroutines.runBlocking { prefs.restoreAllPreferences(emptyMap(), replacePortable = true) }
        startKoin { modules(module { single { prefs } }) }
        val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), "A")
        val account = CallingAccountInfo(handle, handle.toPreferenceKey(), "Network A", null, 0, null, true)
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                SimPickerDialog({}, {}, "+40700000011", listOf(account))
            }
        }
        rule.onNodeWithText("Remember for this number").performClick()
        rule.onNodeWithText("Use for all numbers").performClick()
        rule.onNodeWithText("Network A").performClick()
        kotlinx.coroutines.runBlocking { prefs.getPersistedPreferences() }
        val saved = PreferenceManager(RuntimeEnvironment.getApplication())
        assertEquals(account.key, saved.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null))
        assertNull(saved.getFavoriteSimForNumber("+40700000011"))
    }

    @Test fun `number badge hides for one active SIM and sits before number for two`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        kotlinx.coroutines.runBlocking {
            prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_DEFAULT_SIM to 0), replacePortable = true)
        }
        startKoin { modules(module { single { prefs } }) }
        val accounts = listOf("A", "B").map { id ->
            val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), id)
            CallingAccountInfo(handle, handle.toPreferenceKey(), "Network $id", null, 0, null, true)
        }
        val active = mutableStateOf(1)
        val unknown = mutableStateOf(false)
        assertNull(preferredOutgoingAccount(RuntimeEnvironment.getApplication(), prefs, "+40700000011", null, accounts))
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                CompositionLocalProvider(LocalOutgoingSimSnapshot provides OutgoingSimSnapshot(prefs, 0, accounts, active.value)) {
                    dev.goodwy.rphone.view.screen.HomeResultText(
                        if (unknown.value) "+40700000011" else "Test Alexandru Butica",
                        if (unknown.value) null else "+40700000011",
                        if (unknown.value) resolveCallLogVisual(android.provider.CallLog.Calls.OUTGOING_TYPE, 12L, false) else null,
                        "13:42", 18, null,
                        expandedDetails = false, simNumber = "+40700000011",
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Ask each time").assertDoesNotExist()
        rule.runOnIdle { active.value = 2 }
        rule.onNodeWithContentDescription("Ask each time").assertExists()
        val badge = rule.onNodeWithContentDescription("Ask each time").fetchSemanticsNode().boundsInRoot
        val number = rule.onNodeWithText("+40 700 000 011").fetchSemanticsNode().boundsInRoot
        assertTrue("SIM identity belongs before the number", badge.right <= number.left)
        capture("number-badge", dialog = false)
        rule.runOnIdle { unknown.value = true }
        val unknownBadge = rule.onNodeWithContentDescription("Ask each time").fetchSemanticsNode().boundsInRoot
        val unknownNumber = rule.onNodeWithText("+40 700 000 011").fetchSemanticsNode().boundsInRoot
        assertTrue("Unknown recent also shows SIM before its number", unknownBadge.right <= unknownNumber.left)
        rule.runOnIdle { active.value = 1 }
        rule.onNodeWithContentDescription("Ask each time").assertDoesNotExist()
    }

    private fun capture(name: String, dialog: Boolean = true) = rule.runOnIdle {
        val view = if (dialog) checkNotNull(ShadowDialog.getLatestDialog()?.window).decorView
            else rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File("build/reports/visual/sim-choice").apply { mkdirs() }
        File(folder, "$name-${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
