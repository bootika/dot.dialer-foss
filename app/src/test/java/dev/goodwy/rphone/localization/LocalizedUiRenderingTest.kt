package dev.goodwy.rphone.localization

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.settings.RatingDialog
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.feature.diagnostics.DiagnosticsLogActions
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = Application::class)
class LocalizedUiRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val locales = listOf("en", "ro", "es", "pt-BR", "fr", "de", "it", "id", "tr", "pl", "vi")

    private fun localized(tag: String): Context = rule.activity.createConfigurationContext(
        Configuration(rule.activity.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        },
    )

    @Test
    fun `compiled resources localize numbers plural counts unknown and setup path in every language`() {
        for (tag in locales) {
            val context = localized(tag)
            assertEquals("HD Voice", context.getString(R.string.call_voice_quality_hd))
            assertEquals("HD+ Voice", context.getString(R.string.call_voice_quality_hd_plus))
            assertEquals("Voice call", context.getString(R.string.call_voice_quality_unreported_short))
            assertTrue(context.getString(R.string.standardize_progress, 42).contains("42%"))
            assertTrue(context.getString(R.string.rating_star, 3).contains("3"))
            val advanced = context.getString(R.string.advanced_settings)
            val diagnostics = context.getString(R.string.diagnostics)
            val intro = context.getString(R.string.onboarding_essential_body, advanced, diagnostics)
            assertTrue(intro.contains("$advanced → $diagnostics"))
            assertFalse(intro.contains("%1\$s"))
            if (tag != "en") assertNotEquals("Unknown", context.getString(R.string.label_unknown))
            for (count in listOf(1, 2, 5, 20, 22, 101, 1_000_000)) {
                val participants = context.resources.getQuantityString(R.plurals.conference_participant_count, count, count)
                val missed = context.resources.getQuantityString(R.plurals.notification_missed_calls_count, count, count)
                assertTrue(participants.contains(count.toString()))
                assertTrue(missed.contains(count.toString()))
                if (tag !in listOf("en", "fr")) assertNotEquals("$count participants", participants)
                if (tag != "en") assertNotEquals("$count missed calls", missed)
            }
        }
        assertEquals("20 de contacte", localized("ro").resources.getQuantityString(R.plurals.contacts_count, 20, 20))
        assertEquals("22 uczestników", localized("pl").resources.getQuantityString(R.plurals.conference_participant_count, 22, 22))
        mapOf("es" to "Borrar registro", "it" to "Cancella registro",
            "vi" to "Xóa nhật ký", "pt-BR" to "Limpar registro").forEach { (tag, expected) ->
            assertEquals(expected, localized(tag).getString(R.string.clear_diagnostics))
        }
    }

    @Test
    fun `diagnostic buttons stay readable and retain callbacks in all locales themes and font scales`() {
        val locale = mutableStateOf("en")
        val scale = mutableStateOf(1f)
        val dark = mutableStateOf(false)
        var refreshes = 0
        var clears = 0
        rule.setContent {
            val context = localized(locale.value)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(density.density, scale.value)) {
                DotDialerTheme(systemDark = dark.value) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp)) {
                            DiagnosticsLogActions({ refreshes++ }, { clears++ })
                        }
                    }
                }
            }
        }
        for (tag in locales) for (fontScale in listOf(1f, 1.5f)) for (isDark in listOf(false, true)) {
            rule.runOnIdle { locale.value = tag; scale.value = fontScale; dark.value = isDark }
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            val context = localized(tag)
            capture("diagnostics-$tag-$fontScale-$isDark", dialog = false)
            for (label in listOf(context.getString(R.string.refresh), context.getString(R.string.clear_diagnostics))) {
                rule.onNodeWithText(label).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val results = mutableListOf<TextLayoutResult>()
                    assertTrue(action(results))
                    assertEquals(1, results.single().lineCount)
                    val layout = results.single()
                    // Native text width is fractional; Compose's overflow flag can differ
                    // from the rounded layout width (49.86px of text in a 50px box).
                    assertTrue("Clipped $tag/$fontScale/$label", layout.getLineRight(0) <= layout.size.width + 0.5f)
                    assertFalse(layout.didOverflowHeight)
                }
            }
            capture("diagnostics-$tag-$fontScale-$isDark", dialog = false)
        }
        rule.onNodeWithText(localized("vi").getString(R.string.refresh)).performClick()
        rule.onNodeWithText(localized("vi").getString(R.string.clear_diagnostics)).performClick()
        rule.runOnIdle { assertEquals(1, refreshes); assertEquals(1, clears) }
    }

    @Test
    @Config(qualifiers = "ro-w412dp-h915dp")
    fun `rating dialog and star accessibility use Romanian without submissions`() {
        // A fresh dialog: the host recreates its Activity on locale changes.
        // Swapping CompositionLocals on an already mounted Robolectric Dialog
        // does not recreate that Android window and leaves the old decor attached.
        val context = localized("ro")
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context,
                LocalConfiguration provides context.resources.configuration) {
                DotDialerTheme(systemDark = false) {
                    RatingDialog(onDismiss = {}, onRate = { fail("No rating may be submitted") })
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.rate_app)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.rating_later)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.rating_star, 3)).assertIsDisplayed()
        capture("rating-ro-fresh", dialog = true)
    }

    private fun capture(name: String, dialog: Boolean) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/localization-d57/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        rule.runOnIdle {
            val view = if (dialog) requireNotNull(ShadowDialog.getLatestDialog()).window!!.decorView
                else rule.activity.window.decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            FileOutputStream(output).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
