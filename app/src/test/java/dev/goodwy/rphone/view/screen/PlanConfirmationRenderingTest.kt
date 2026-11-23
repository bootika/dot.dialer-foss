package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.PlanConfirmation
import dev.goodwy.rphone.controller.license.PlanConfirmationSource
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.PlanConfirmationContent
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.AppPlan
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "ro-rRO-w382dp-h670dp-mdpi")
class PlanConfirmationRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun dependencies() {
        stopKoin()
        startKoin {
            modules(module { single { PreferenceManager(RuntimeEnvironment.getApplication()) } })
        }
    }

    @After fun releaseDependencies() = stopKoin()

    @Test fun referenceSizeKeepsBothConfirmationsOnScreen() = verifyScreens(1f, "382x670")

    @Test
    @Config(qualifiers = "ro-rRO-w320dp-h568dp-mdpi")
    fun compactFont150KeepsBothConfirmationsOnScreen() = verifyScreens(1.5f, "320x568-font150")

    @Test
    @Config(qualifiers = "ro-rRO-w320dp-h520dp-mdpi")
    fun compactFont150LeavesRoomForSystemBars() = verifyScreens(1.5f, "320x520-content-font150")

    private fun verifyScreens(scale: Float, size: String) {
        val term = Instant.parse("2026-12-23T12:00:00Z").epochSecond
        val source = mutableStateOf(PlanConfirmationSource.TRIAL)
        var done = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = false) {
                    PlanConfirmationContent(PlanConfirmation(AppPlan.PREMIUM, source.value, term)) { done++ }
                }
            }
        }
        for (kind in listOf(PlanConfirmationSource.TRIAL, PlanConfirmationSource.PLAY_SUBSCRIPTION)) {
            val trial = kind == PlanConfirmationSource.TRIAL
            rule.runOnIdle { source.value = kind }
            capture("${if (trial) "trial" else "paid"}-$size")
            insideScreen(rule.onNodeWithTag("confirmation-title"))
            insideScreen(rule.onNodeWithTag("confirmation-details"))
            val resource = when {
                trial && scale > 1f -> R.string.confirmation_after_free_compact
                trial -> R.string.confirmation_after_free
                else -> R.string.confirmation_manage_subscription
            }
            insideScreen(rule.onNodeWithText(rule.activity.getString(resource)))
            if (trial) insideScreen(rule.onNodeWithText(rule.activity.getString(R.string.confirmation_trial_charge)))
            val doneButton = rule.onNodeWithTag("confirmation-done")
            insideScreen(doneButton)
            doneButton.assertHeightIsAtLeast(48.dp)
            val recovery = rule.onNodeWithTag("confirmation-recovery")
            insideScreen(recovery)
            recovery.assertHeightIsAtLeast(48.dp)
            rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
            recovery.performClick()
            val help = if (trial) R.string.confirmation_recover_trial else R.string.confirmation_recover_play
            insideScreen(rule.onNodeWithText(rule.activity.getString(help)))
            rule.onNodeWithTag("confirmation-done").performClick()
            rule.onNodeWithTag("confirmation-details").assertIsDisplayed()
            rule.onNodeWithTag("confirmation-done").performClick()
        }
        rule.runOnIdle { assertEquals(2, done) }
    }

    private fun insideScreen(node: SemanticsNodeInteraction) {
        val screen = rule.onNodeWithTag("plan-confirmation").getUnclippedBoundsInRoot()
        val bounds = node.assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Element bounds $bounds must be inside $screen", bounds.left >= screen.left &&
            bounds.top >= screen.top && bounds.right <= screen.right && bounds.bottom <= screen.bottom)
    }

    private fun capture(name: String) {
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File(System.getProperty("user.dir"), "build/reports/visual/license-confirmation")
            directory.mkdirs()
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
