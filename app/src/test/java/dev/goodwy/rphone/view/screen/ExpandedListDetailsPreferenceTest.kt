package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.expandedListDetails
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.CallLogListItemSimple
import dev.goodwy.rphone.view.components.LocalRillAvatarAppearance
import dev.goodwy.rphone.view.components.RillAvatarAppearance
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.SingleTile
import dev.goodwy.rphone.view.components.rememberExpandedListDetails
import dev.goodwy.rphone.view.screen.settings.ExpandedListDetailsSetting
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
@Config(sdk = [36], application = Application::class, qualifiers = "w420dp-h900dp-mdpi")
class ExpandedListDetailsPreferenceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun stopDependencies() { stopKoin() }

    @Test fun `real setting defaults off updates all contact list row types and persists`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        assertFalse(PreferenceManager.DEFAULT_EXPANDED_LIST_DETAILS)
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_EXPANDED_LIST_DETAILS, true))
        startKoin { modules(module { single { prefs } }) }
        val rowKind = mutableStateOf("history")
        var expanded = false
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalRillAvatarAppearance provides RillAvatarAppearance(false, true, false, true, false, false),
            ) {
                DotDialerTheme(systemDark = false) {
                    expanded = rememberExpandedListDetails(prefs)
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        Box(Modifier.width(200.dp)) {
                            when (rowKind.value) {
                                "history" -> CallLogListItemSimple(
                                    headline = NAME, supporting = PHONE, trailing = "01:54", onClick = {},
                                )
                                "phone" -> RillListItem(headline = PHONE, onClick = {}, expandText = expanded)
                                "search" -> SingleTile(title = NAME, subtitle = PHONE, onClick = {})
                            }
                        }
                        ExpandedListDetailsSetting(prefs)
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(isToggleable()).assertIsOff()
        assertEquals(1, layout(NAME).lineCount)
        assertEquals(1, layout(PHONE).lineCount)
        capture("list-details-setting-off.png")
        rule.onNode(isToggleable()).performClick()
        rule.waitUntil(5_000) { expanded }
        rule.onNode(isToggleable()).assertIsOn()
        assertTrue(PreferenceManager(context).expandedListDetails())
        assertComplete(NAME)
        assertComplete(PHONE)
        val phoneBounds = rule.onNodeWithText(PHONE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val timeBounds = rule.onNodeWithText("01:54", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(timeBounds.top >= phoneBounds.bottom)
        capture("list-details-history-on.png")
        listOf("phone", "search").forEach { kind ->
            rule.runOnUiThread { rowKind.value = kind }
            rule.waitForIdle()
            assertComplete(PHONE)
            assertTrue(layout(PHONE).lineCount > 1)
            if (kind != "phone") assertComplete(NAME)
            capture("list-details-$kind-on.png")
        }
        rule.onNode(isToggleable()).performClick()
        rule.waitUntil(5_000) { !expanded }
        rule.onNode(isToggleable()).assertIsOff()
        assertFalse(PreferenceManager(context).expandedListDetails())
        assertEquals(1, layout(NAME).lineCount)
        assertEquals(1, layout(PHONE).lineCount)
        capture("list-details-search-off.png")
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
        return results.single()
    }

    private fun assertComplete(text: String) {
        val result = layout(text)
        assertFalse((0 until result.lineCount).any(result::isLineEllipsized))
        val glyph = result.getBoundingBox(text.lastIndex)
        assertTrue(glyph.right <= result.size.width + 1f)
        assertTrue(glyph.bottom <= result.size.height + 1f)
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/$name")
        output.parentFile!!.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    private companion object {
        const val NAME = "Test Alexandru Butica"
        const val PHONE = "+40 700 000 000"
    }
}
