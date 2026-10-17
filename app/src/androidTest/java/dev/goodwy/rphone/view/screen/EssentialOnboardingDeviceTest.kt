package dev.goodwy.rphone.view.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Component simulation on hardware, not a fresh-install/role-permission test. No preference writes. */
@RunWith(AndroidJUnit4::class)
class EssentialOnboardingDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun essentialPageHasReachableStartInBothThemesAndLargeFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1.15f)
        var completed = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font)) {
                DotDialerTheme(systemDark = dark) {
                    MorphingOnboardingScreen { completed++ }
                }
            }
        }
        val directory = File(context.cacheDir, "publication-audit-onboarding").apply { mkdirs() }
        for (mode in listOf(false, true)) for (scale in listOf(1.15f, 1.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            rule.mainClock.advanceTimeBy(1200)
            rule.onNodeWithText(context.getString(R.string.onboarding_essential_title)).performScrollTo()
            rule.onNodeWithText(context.getString(R.string.plan_guide_begin))
                .assertIsDisplayed().performClick()
            rule.onNodeWithText(context.getString(R.string.onboarding_essential_title)).assertIsDisplayed()
            rule.waitForIdle()
            val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory, "intro-${if (mode) "dark" else "light"}-${(scale * 100).toInt()}.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            if (scale == 1.5f) {
                val scroller = rule.onNode(hasScrollAction())
                val range = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("Large-font body must be scrollable", range.maxValue() > 0f)
                scroller.performTouchInput { swipeUp() }
                rule.waitForIdle()
                assertTrue("Gesture must move the body", range.value() > 0f)
                rule.onNodeWithText(context.getString(R.string.plan_guide_begin)).assertIsDisplayed()
                val scrolled = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(directory, "intro-${if (mode) "dark" else "light"}-150-scrolled.png")
                    .outputStream().use { scrolled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                scrolled.recycle()
            }
        }
        assertEquals(4, completed)
    }
}
