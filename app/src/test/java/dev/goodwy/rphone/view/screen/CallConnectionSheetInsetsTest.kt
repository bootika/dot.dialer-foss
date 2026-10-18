package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.After
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import dev.goodwy.rphone.controller.util.QuickCallFixture
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class CallConnectionSheetInsetsTest {
    @get:Rule(order = 0) val fontRule = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            RuntimeEnvironment.setFontScale(if (description.methodName.startsWith("opens")) 1.5f else 2f)
            try { base.evaluate() } finally { RuntimeEnvironment.setFontScale(1f) }
        }
    } }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { stopKoin() }
    private lateinit var sheetView: View

    @Test fun opensWithLastDetailAboveThreeButtonNavigationWithoutManualExpansion() {
        var dark by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides LocalDensity.current) {
                DotDialerTheme(systemDark = dark) {
                    key(dark) { Sheet() }
                }
            }
        }
        for (theme in listOf(false, true)) for (navigation in listOf(24, 48)) {
            rule.runOnIdle { dark = theme }
            settleAndApplyInsets(navigation)
            val last = rule.onNodeWithText("12.2 kbps")
            last.assertIsDisplayed()
            val bounds = last.fetchSemanticsNode().boundsInRoot
            val height = sheetView.rootView.height
            save("normal-$theme-font150-nav$navigation")
            assertTrue("Last detail $bounds must be above navigation at ${height - 48}", bounds.bottom <= height - navigation)
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun shortWindowLargeFontCanScrollToLastDetailAndRetainsExtraContent() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides LocalDensity.current) {
                DotDialerTheme(systemDark = true) { Sheet(extra = true) }
            }
        }
        settleAndApplyInsets()
        rule.onNodeWithText("SIM 1").assertExists()
        rule.onNodeWithText("12.2 kbps").performScrollTo().assertIsDisplayed()
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val bounds = rule.onNodeWithText("12.2 kbps").fetchSemanticsNode().boundsInRoot
        save("landscape-font200")
        assertTrue("Scrolled detail $bounds must be above navigation", bounds.bottom <= sheetView.rootView.height - 48)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun callbackAndUnknownCallerActionsRemainReachableAtLargeFont() {
        var unknown by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var actions = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides LocalDensity.current) {
                DotDialerTheme(systemDark = dark) {
                    key(unknown, dark) {
                        if (unknown) UnknownCallerActionsBottomSheet({}, {}, { actions++ })
                        else CallBackReminderBottomSheet({}, {}, { actions++ })
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (other in listOf(false, true)) {
            rule.runOnIdle { unknown = other; dark = theme }
            settleAndApplyInsets()
            val context = rule.activity
            val label = context.getString(if (other) dev.goodwy.rphone.R.string.block_and_report_spam
                else dev.goodwy.rphone.R.string.choose_time)
            val target = rule.onNodeWithText(label)
            target.performScrollTo().assertIsDisplayed()
            val bounds = target.fetchSemanticsNode().boundsInRoot
            assertTrue("Action $bounds must be above navigation", bounds.bottom <= sheetView.rootView.height - 48)
            save("actions-$other-$theme-font200")
            target.performClick()
        }
        assertEquals(4, actions)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun quickResponsesCanReachCustomMessageAboveNavigation() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        startKoin { modules(module { single { prefs } }) }
        rule.setContent { DotDialerTheme(systemDark = true) {
            QuickResponsesBottomSheet("+12025550123", "Test Alexandru Butica", {}, {}, {})
        } }
        settleAndApplyInsets()
        val target = rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.write_your_own))
        target.performScrollTo().assertIsDisplayed()
        assertTrue(target.fetchSemanticsNode().boundsInRoot.bottom <= sheetView.rootView.height - 48)
        save("quick-response-font200")
        target.performClick()
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.type_message)).assertExists()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun backgroundPreviewLeavesItsBottomActionAccessibleInShortWindow() {
        var changed = 0
        rule.setContent { DotDialerTheme(systemDark = true) {
            CallBackgroundBottomSheet(null, null, false, {}, { changed++ }, {}, {})
        } }
        settleAndApplyInsets()
        val target = rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.contact_call_background_choose))
        target.assertIsDisplayed()
        assertTrue(target.fetchSemanticsNode().boundsInRoot.bottom <= sheetView.rootView.height - 48)
        save("background-font200")
        target.performClick(); assertEquals(1, changed)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun timerReminderLastChoiceIsReachableAndStillSchedules() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context, featureAllowed = { true })
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
        val commands = mutableListOf<String>()
        val call = QuickCallFixture.call(commands = commands)
        val repository = dev.goodwy.rphone.modal.repository.CallRepositoryImpl().apply { updateAllCalls(listOf(call)) }
        val reminders = dev.goodwy.rphone.controller.util.CallSessionReminders(context, repository, prefs, scope)
        startKoin { modules(module { single { prefs }; single { reminders } }) }
        var dismissed = 0
        try {
            rule.setContent { DotDialerTheme(systemDark = true) { CallTimerReminderSheet(call) { dismissed++ } } }
            settleAndApplyInsets()
            val label = context.resources.getQuantityString(dev.goodwy.rphone.R.plurals.call_reminder_minutes, 15, 15)
            val target = rule.onNodeWithText(label)
            target.performScrollTo().assertIsDisplayed()
            assertTrue(target.fetchSemanticsNode().boundsInRoot.bottom <= sheetView.rootView.height - 48)
            save("timer-font200")
            target.performClick()
            rule.waitUntil(5000) { reminders.state.value.containsKey(call) }
            assertEquals(1, dismissed)
            assertTrue(commands.isEmpty())
        } finally { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }

    @Composable private fun Sheet(extra: Boolean = false) {
        CallConnectionDetailsSheet(
            CallConnectionInfo(CallTransportKind.Cellular, null, "AMR-NB", 3.4f, 12.2f),
            onDismiss = {},
            extraContent = {
                sheetView = LocalView.current
                if (extra) { Text("SIM 1"); Spacer(Modifier.height(32.dp)) }
            },
        )
    }

    private fun settleAndApplyInsets(navigation: Int = 48) {
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        rule.runOnIdle {
            val manager = ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
            sheetView = ReflectionHelpers.getField<List<View>>(manager, "mViews").last()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigation))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true).build()
            ViewCompat.dispatchApplyWindowInsets(sheetView.rootView, insets)
        }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    private fun save(name: String) {
        rule.runOnIdle {
            val decor = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val manager = ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
            val windows = ReflectionHelpers.getField<List<View>>(manager, "mViews")
            windows.forEach { window ->
                val position = IntArray(2); window.getLocationOnScreen(position)
                canvas.save(); canvas.translate(position[0].toFloat(), position[1].toFloat())
                window.draw(canvas); canvas.restore()
            }
            val directory = File("../evidence/local-verification/connection-sheet-20261018").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
