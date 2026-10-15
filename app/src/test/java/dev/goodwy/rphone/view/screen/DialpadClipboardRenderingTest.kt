package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class DialpadClipboardRenderingTest {
    @org.junit.Before fun initPreferences() {
        org.koin.core.context.startKoin { modules(org.koin.dsl.module {
            single { dev.goodwy.rphone.controller.util.PreferenceManager(org.robolectric.RuntimeEnvironment.getApplication()) }
        }) }
    }
    @org.junit.After fun clearPreferences() { org.koin.core.context.stopKoin() }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `clipboard focus late value change dismiss reopen and Use render without launching anything`() {
        val manager = rule.activity.getSystemService(ClipboardManager::class.java)
        val draft = mutableStateOf("")
        val session = mutableStateOf(0)
        val profile = mutableStateOf(false to 1f)
        val gate = object : View(rule.activity) {
            var focused = false
            override fun hasWindowFocus() = focused
        }
        fun focus(value: Boolean) {
            gate.focused = value
            ReflectionHelpers.callInstanceMethod<Void>(gate.viewTreeObserver, "dispatchOnWindowFocusChange",
                ClassParameter.from(Boolean::class.javaPrimitiveType, value))
        }
        (rule.activity.window.decorView as ViewGroup).addView(gate, ViewGroup.LayoutParams(0, 0))
        manager.setPrimaryClip(ClipData.newPlainText("phone number", NUMBER))
        rule.setContent {
            DotDialerTheme(systemDark = profile.value.first) {
                CompositionLocalProvider(LocalView provides gate, LocalDensity provides Density(1f, profile.value.second)) {
                    key(session.value) {
                        val suggestion = rememberDialpadClipboardSuggestion(draft.value)
                        Column {
                            Text("Test Alexandru Butica")
                            Text(draft.value.ifEmpty { "Enter number" })
                            dev.goodwy.rphone.view.components.ContactDirectoryHeader("", {}, suggestion.number != null && draft.value.isEmpty(),
                                suggestion.number.orEmpty(), { suggestion.use(draft.value)?.let { draft.value = it } }, suggestion::dismiss)
                        }
                    }
                }
            }
        }
        rule.onNodeWithText(NUMBER).assertDoesNotExist()
        rule.runOnIdle { focus(true) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(NUMBER).assertExists()
        capture("light-clipboard")
        rule.runOnIdle { profile.value = true to 1.5f }
        rule.mainClock.advanceTimeBy(700)
        capture("dark-font150-clipboard")
        rule.onNodeWithContentDescription(rule.activity.getString(dev.goodwy.rphone.R.string.close)).performClick()
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(NUMBER).assertDoesNotExist()
        rule.runOnIdle { focus(false); focus(true) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(NUMBER).assertDoesNotExist()
        rule.runOnIdle { session.value++ }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(NUMBER).assertExists()
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.use)).performClick()
        rule.runOnIdle { assertEquals(NUMBER, draft.value); assertNull(shadowOf(rule.activity).nextStartedActivity) }
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.use)).assertDoesNotExist()
        rule.runOnIdle { draft.value = ""; session.value++ }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(NUMBER).assertExists()
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.use)).assertExists()
        rule.runOnIdle { focus(false) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(NUMBER).assertDoesNotExist()
        rule.runOnIdle {
            manager.setPrimaryClip(ClipData.newPlainText("phone number", "0700000001"))
            focus(true)
        }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText("0700000001").assertExists()
        rule.runOnIdle { manager.setPrimaryClip(ClipData.newPlainText("message", "Tomorrow at 14:30")) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText("0700000001").assertDoesNotExist()
        rule.runOnIdle { assertEquals("", draft.value); assertNull(shadowOf(rule.activity).nextStartedActivity) }
    }

    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File(System.getProperty("user.dir"), "build/reports/visual/clipboard/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private companion object { const val NUMBER = "+40700000000" }
}
