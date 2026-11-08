package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.components.contactListRowPreferences
import dev.goodwy.rphone.view.screen.onboarding.CallStyleChoiceContent
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "ro-rRO-w360dp-h800dp-mdpi")
class CallStyleSetupTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var prefs: PreferenceManager
    @Before fun setup() {
        org.koin.core.context.stopKoin()
        prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_RECENT_TAP_ACTION to 1,
            PreferenceManager.KEY_SWIPE_RIGHT_ACTION to 2, PreferenceManager.KEY_SWIPE_LEFT_ACTION to 4)) }
        org.koin.core.context.startKoin { modules(org.koin.dsl.module { single { prefs } }) }
    }
    @After fun cleanup() { runBlocking { prefs.getPersistedPreferences() }; org.koin.core.context.stopKoin() }

    @Test fun bothPresetsAreDurableAndReachExistingDirectoryConsumers() = runBlocking {
        for (swipe in listOf(true, false)) {
            prefs.saveInitialCallStyle(swipe)
            val cold = PreferenceManager(RuntimeEnvironment.getApplication())
            val expectedTap = if (swipe) 7 else 1
            assertEquals(expectedTap, cold.getRecentTapAction())
            assertEquals(expectedTap, cold.contactListRowPreferences().tapAction)
            assertEquals(if (swipe) 1 else 2, cold.getSwipeRightAction())
            assertEquals(4, cold.getSwipeLeftAction())
            assertTrue(cold.getBoolean(PreferenceManager.KEY_SWIPE_TO_CALL, false))
            val map = cold.getPersistedPreferences()
            assertEquals(expectedTap, map[PreferenceManager.KEY_RECENT_TAP_ACTION])
            assertEquals(if (swipe) 1 else 2, map[PreferenceManager.KEY_SWIPE_RIGHT_ACTION])
            assertEquals(4, map[PreferenceManager.KEY_SWIPE_LEFT_ACTION])
        }
    }

    @Test fun exclusiveCardsKeepSelectionAcrossBackAndOnlyConfirmPersists() {
        var finished = 0
        rule.setContent { DotDialerTheme(systemDark = false) { MorphingOnboardingScreen { finished++ } } }
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("call-style-swipe").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("call-style-tap").assertIsNotSelected()
        rule.runOnIdle { assertEquals(1, prefs.getRecentTapAction()); assertEquals(0, finished) }
        rule.onNodeWithTag("plan-guide-back").performClick()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("call-style-swipe").assertIsSelected()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("call-style-swipe").assertIsSelected()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.waitUntil(5_000) { finished == 1 }
        assertEquals(7, prefs.getRecentTapAction())
    }

    @Test fun cardsRenderBothThemesAndLargeTextWithoutLosingNavigation() {
        var swipe by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
            DotDialerTheme(systemDark = dark) { CallStyleChoiceContent(swipe, onSelect = { swipe = it }, onBack = {}, onNext = {}) }
        } }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 2f)) for (choice in listOf(false, true)) {
            rule.runOnIdle { dark = theme; font = scale; swipe = choice }
            rule.onNodeWithTag("call-style-${if (choice) "swipe" else "tap"}").performScrollTo().assertIsSelected()
            rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
            capture("cards-$theme-$scale-$choice")
        }
    }

    @Test @Config(qualifiers = "ro-rRO-w800dp-h360dp-land-mdpi")
    fun landscapeErrorAndBusyStatesKeepChoiceAndPreventSubmission() {
        var busy by mutableStateOf(false)
        var next = 0
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
            DotDialerTheme(systemDark = true) { CallStyleChoiceContent(true, busy, true, {}, {}, { next++ }) }
        } }
        rule.onNodeWithTag("call-style-save-error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
        capture("landscape-error-200")
        rule.runOnIdle { busy = true }
        rule.onNodeWithTag("call-style-saving").assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-next").assertDoesNotExist()
        rule.onNodeWithTag("call-style-swipe").assertIsNotEnabled()
        assertEquals(0, next)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File(System.getProperty("user.dir"), "build/reports/visual/call-style-setup").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
