package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.screen.settings.SelectedTextCallSettings
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.PlanFeature
import java.io.File
import kotlinx.coroutines.*
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class QuickCallToolsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun clear() { scope.cancel(); stopKoin() }

    @Test fun selectedTextSwitchStartsOffAndControlsTheActualPackageComponent() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_SELECTED_TEXT_DIALER to false)) }
        rule.setContent {
            val revision by prefs.settingsChanged.collectAsStateWithLifecycle()
            key(revision) { DotDialerTheme { Surface { SelectedTextCallSettings(prefs) } } }
        }
        rule.onNode(isToggleable()).assertIsOff()
        assertFalse(SelectedTextDialerIntegration.isEnabled(context))
        rule.onNodeWithText(context.getString(R.string.open_in_dot)).performClick()
        rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_SELECTED_TEXT_DIALER, false) && SelectedTextDialerIntegration.isEnabled(context) }
        rule.onNode(isToggleable()).assertIsOn()
        save(frame(), "selected-text-enabled")
        rule.onNodeWithText(context.getString(R.string.open_in_dot)).performClick()
        rule.waitUntil(5000) { !prefs.getBoolean(PreferenceManager.KEY_SELECTED_TEXT_DIALER, true) && !SelectedTextDialerIntegration.isEnabled(context) }
        rule.onNode(isToggleable()).assertIsOff()
    }

    @Test fun actualTimerOpensChoicesAndAnOpenSheetTracksNonComposePlanChanges() {
        val context = RuntimeEnvironment.getApplication()
        var allowed = false
        var upgrades = 0
        val prefs = PreferenceManager(context, featureAllowed = { allowed }, onPlanRequired = { upgrades++ })
        val commands = mutableListOf<String>()
        val call = QuickCallFixture.call(commands = commands)
        val repository = CallRepositoryImpl().apply { updateAllCalls(listOf(call)) }
        val reminders = CallSessionReminders(context, repository, prefs, scope)
        startKoin { modules(module { single { prefs }; single { reminders } }) }
        var open by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        CallConnectionStatusRow(CallConnectionInfo(CallTransportKind.Cellular, null, null, null, null),
                            "01:00", {}, onTimerClick = { if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY)) open = true })
                        if (open) CallTimerReminderSheet(call) { open = false }
                    }
                }
            }
        }
        rule.onNodeWithText("01:00").performClick()
        assertEquals(1, upgrades)
        rule.onNodeWithText(context.getString(R.string.call_reminder_title)).assertDoesNotExist()
        allowed = true; prefs.notifyPlanChanged()
        rule.onNodeWithText("01:00").performClick()
        rule.onNodeWithText(context.getString(R.string.call_reminder_title)).assertExists()
        val five = context.resources.getQuantityString(R.plurals.call_reminder_minutes, 5, 5)
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
        val bounds = rule.onNodeWithText(five).fetchSemanticsNode().boundsInRoot
        val paid = frame(); save(paid, "reminder-power")
        allowed = false; prefs.notifyPlanChanged()
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
        val free = frame(); save(free, "reminder-downgraded")
        var changed = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) for (x in bounds.left.toInt() until bounds.right.toInt())
            if (paid.getPixel(x, y) != free.getPixel(x, y)) changed++
        paid.recycle(); free.recycle()
        assertTrue("Plan downgrade must visibly dim the already open choices", changed > 10)
        rule.onNodeWithText(five).performClick()
        assertEquals(2, upgrades); assertTrue(reminders.state.value.isEmpty())
        allowed = true; prefs.notifyPlanChanged()
        rule.runOnIdle { dark = true; font = 1.5f }
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
        save(frame(), "reminder-dark-150")
        rule.onNodeWithText(five).performClick()
        rule.waitUntil(5000) { reminders.state.value.containsKey(call) }
        rule.onNodeWithText("01:00").performClick()
        rule.onNodeWithText(context.getString(R.string.cancel_call_reminder)).performClick()
        assertTrue(reminders.state.value.isEmpty())
        assertEquals(Call.STATE_ACTIVE, call.currentTelecomState())
        assertTrue("Timer tools must not issue Telecom commands", commands.isEmpty())
    }

    private fun frame(): Bitmap {
        rule.waitForIdle()
        lateinit var bitmap: Bitmap
        rule.runOnIdle { val view = rule.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
            val windows = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
            windows.forEach { window ->
                val location = IntArray(2); window.getLocationOnScreen(location)
                canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat())
                window.draw(canvas); canvas.restore()
            } }
        return bitmap
    }
    private fun save(bitmap: Bitmap, name: String) {
        val file = File(System.getProperty("user.dir"), "build/reports/visual/quick-call-tools/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
