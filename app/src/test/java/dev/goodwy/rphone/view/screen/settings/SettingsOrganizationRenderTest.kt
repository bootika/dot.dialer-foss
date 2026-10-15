package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.OngoingCallBanner
import dev.goodwy.rphone.view.components.rillAvatarAppearance
import dev.goodwy.rphone.view.screen.settings.components.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
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
class SettingsOrganizationRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }

    @Test fun `production categories and independent banner targets at both font scales`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        kotlinx.coroutines.runBlocking {
            prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_LG_DIALPAD to true,
                PreferenceManager.KEY_BLUR_DIALPAD to false, PreferenceManager.KEY_MODERN_PASTEL_AVATARS to false,
                PreferenceManager.KEY_PRIMARY_COLOR_AVATARS to true))
        }
        assertTrue(prefs.rillAvatarAppearance().modernPastelAvatars)
        startKoin { modules(module { single { prefs } }) }
        val navigation = mutableListOf<String>()
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader, arrayOf(DestinationsNavigator::class.java)) { _, method, args ->
            if (method.name == "navigate") navigation.add(args!!.first().toString())
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        var scene by mutableStateOf("home")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var expanded = 0
        var ended = 0
        // Robolectric ComponentActivity lacks a display association; production Activities have one.
        val visualContext = object : android.content.ContextWrapper(rule.activity) {
            override fun getDisplay(): android.view.Display =
                rule.activity.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }
        // These snapshots use Canvas(Bitmap), not the hardware window advertised by Robolectric.
        // Exercise the real software fallback here; RuntimeShader output is covered on emulator.
        val softwareView = object : android.view.View(visualContext) {
            override fun isHardwareAccelerated() = false
        }
        (rule.activity.window.decorView as android.view.ViewGroup).addView(softwareView,
            android.view.ViewGroup.LayoutParams(0, 0))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font), LocalContext provides visualContext,
                androidx.compose.ui.platform.LocalView provides softwareView) {
                DotDialerTheme(systemDark = dark) {
                    key(scene, dark, font) {
                        when (scene) {
                            "home" -> SettingsHomeContent(rememberLazyListState(), PaddingValues(), "", {}, emptyList(), navigator, "1.8.8-test", "Local", {}, {}, {})
                            "advanced" -> AdvancedSettingsScreen(navigator)
                            "interface" -> InterfaceScreen(navigator)
                            "interface-advanced" -> InterfaceScreen(navigator, advanced = true)
                            "avatars" -> AvatarsPreferenceScreen(navigator)
                            "sound" -> SoundVibrationScreen(navigator)
                            "home-gestures" -> HomeGesturesScreen(navigator)
                            "banner" -> OngoingCallBanner({ expanded++ }, { ended++ })
                            else -> SettingsCategoryScreen(navigator, scene)
                        }
                    }
                }
            }
        }
        val output = File(System.getProperty("user.dir"), "build/reports/visual/settings-organization")
        fun capture(name: String) {
            rule.mainClock.advanceTimeBy(1200)
            rule.waitForIdle()
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                output.mkdirs()
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            File(output, "$name.txt").writeText(rule.onNode(isRoot()).printToString())
        }
        listOf(false, true).forEach { mode ->
            listOf(1f, 1.5f).forEach { scale ->
                listOf("home", "advanced", "privacy", "effects", "compatibility", "keypad", "interface", "interface-advanced", "avatars", "sound", "home-gestures", "banner").forEach { page ->
                    rule.runOnUiThread { scene = page; dark = mode; font = scale }
                    capture("$page-${if (mode) "dark" else "light"}-font$scale")
                    if (page == "avatars") {
                        rule.onNodeWithText(context.getString(R.string.avatar_colors)).assertDoesNotExist()
                    }
                    if (page == "privacy") {
                        rule.onNodeWithText(context.getString(R.string.private_contacts)).assertDoesNotExist()
                        rule.onNodeWithText(context.getString(R.string.authentication)).assertIsDisplayed()
                    }
                    if (page == "advanced") {
                        rule.onAllNodesWithText(context.getString(R.string.contact_tools)).assertCountEquals(0)
                    }
                    if (page == "effects" || page == "interface-advanced") {
                        rule.onAllNodesWithText(context.getString(R.string.liquid_glass_dialpad)).assertCountEquals(0)
                        rule.onAllNodesWithText(context.getString(R.string.keypad)).assertCountEquals(0)
                    }
                    if (page == "interface" || page == "keypad") {
                        rule.onNodeWithText(context.getString(R.string.liquid_glass_dialpad)).performScrollTo().assertIsDisplayed()
                        rule.onNodeWithText(context.getString(R.string.keypad_effect_strength)).performScrollTo().assertIsDisplayed()
                        capture("$page-keypad-${if (mode) "dark" else "light"}-font$scale")
                    }
                    if (page == "banner") {
                        val end = rule.onNodeWithContentDescription(context.getString(R.string.end_call))
                        val open = rule.onNodeWithContentDescription(context.getString(R.string.open_call))
                        val before = expanded
                        end.performClick()
                        assertEquals(before, expanded)
                        open.performClick()
                        assertEquals(before + 1, expanded)
                        assertTrue(end.fetchSemanticsNode().boundsInRoot.width >= 48f)
                    }
                }
            }
        }
        rule.runOnUiThread { scene = "interface"; font = 2.5f; dark = false }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(context.getString(R.string.liquid_glass_dialpad)))
        rule.onNodeWithText(context.getString(R.string.liquid_glass_dialpad)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.keypad_effect_strength)).performScrollTo().assertIsDisplayed()
        capture("interface-keypad-light-font2.5")
        rule.onNodeWithContentDescription(context.getString(R.string.keypad_effect_preview)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.keypad_effect_preview)).assertDoesNotExist()
        rule.runOnUiThread { scene = "compatibility"; font = 1f }
        rule.onNodeWithText(context.getString(R.string.ignore_system_font_scale)).performScrollTo().assertIsDisplayed()
        rule.runOnUiThread { scene = "effects" }
        rule.onNodeWithText(context.getString(R.string.customize_width)).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(navigation.last().contains("true"))
        assertTrue(prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD, false))
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_BLUR_DIALPAD, true))
        assertEquals(4, ended)
    }

    @Test fun `all contracted options are unique and platform conditions stay explicit`() {
        val contractedIds = File("../config/quality/settings-option-ids.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertEquals(contractedIds.size, contractedIds.toSet().size)
        assertEquals(contractedIds.toSet(), settingsOptions.map { it.id }.toSet())
        assertEquals(settingsOptions.size, settingsOptions.map { it.id }.toSet().size)
        assertFalse(settingsOptions.single { it.id == "T18" }.isAvailable("gplay", true))
        assertTrue(settingsOptions.single { it.id == "T19" }.isAvailable("gplay", true))
        assertEquals(SettingsEditor.HOME, settingsOptions.single { it.id == "H10" }.editor)
        assertFalse(settingsOptions.single { it.id == "H10" }.advanced)
        val logLevel = settingsOptions.single { it.id == "O02" }
        assertTrue(logLevel.isAvailable("gplay", true, testBuild = true))
        assertFalse(logLevel.isAvailable("gplay", true, testBuild = false))
        listOf("A06", "A09").forEach { id ->
            val option = settingsOptions.single { it.id == id }
            assertEquals(SettingsEditor.INTERFACE, option.editor)
            assertFalse(option.advanced)
            val routes = mutableListOf<String>()
            val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
                arrayOf(DestinationsNavigator::class.java)) { _, method, args ->
                if (method.name == "navigate") routes += (args!!.first() as com.ramcosta.composedestinations.spec.Direction).route
                if (method.returnType == Boolean::class.javaPrimitiveType) true else null
            } as DestinationsNavigator
            navigator.openSettingsEditor(option.editor)
            assertTrue(routes.single().startsWith("interface_screen?advanced=false"))
        }
        assertEquals(SettingsEditor.CALLS, settingsOptions.single { it.id == "K07" }.editor)
        assertFalse(settingsOptions.single { it.id == "I04" }.isAvailable("foss", true))
        val reporting = settingsOptions.single { it.id == "O01" }
        assertFalse(reporting.advanced)
        assertEquals(SettingsEditor.REPORTING, reporting.editor)
        val auto = settingsOptions.single { it.id == "K04" }
        assertFalse(auto.isAvailable("foss", false))
        assertTrue(auto.isAvailable("gplay", true))
        listOf("T09", "T10").forEach { id ->
            val option = settingsOptions.single { it.id == id }
            assertFalse(option.isAvailable("foss", false))
            assertTrue(option.isAvailable("gplay", true))
        }
        assertTrue(settingsOptions.filter { it.editor == SettingsEditor.CONTEXT || it.editor == SettingsEditor.LEGACY }.none { it.isAvailable("gplay", true) })
    }

    @Test fun `contact tool search uses normal breadcrumb and real existing destinations`() {
        val context = RuntimeEnvironment.getApplication()
        val navigation = mutableListOf<String>()
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader, arrayOf(DestinationsNavigator::class.java)) { _, method, args ->
            if (method.name == "navigate") navigation.add(args!!.first().toString())
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        var entries = emptyList<SettingsSearchEntry>()
        rule.setContent {
            DotDialerTheme {
                entries = createSettingsSearchEntries(context, navigator, "1.9.0-test", "Local", {}, {}, {})
            }
        }
        rule.runOnIdle {
            assertTrue(entries.none { it.headline == context.getString(R.string.private_contacts) })
            val expected = listOf(R.string.settings, R.string.contacts, R.string.manage_contacts, R.string.contact_tools)
                .joinToString(" › ") { context.getString(it) }
            listOf(R.string.standardize_phone_numbers, R.string.merging_contacts, R.string.unmerging_contacts).forEach { label ->
                val entry = entries.single { it.headline == context.getString(label) }
                assertEquals(expected, entry.supporting)
                entry.onClick()
            }
            assertEquals(3, navigation.size)
            assertTrue(navigation[0].contains("true"))
            assertTrue(navigation[1].contains("ContactMergeDuplicatesScreen"))
            assertTrue(navigation[2].contains("ContactUnmergeDuplicatesScreen"))
            assertTrue(entries.single { it.headline == context.getString(R.string.diagnostics) }
                .supporting?.startsWith(context.getString(R.string.advanced_settings)) == true)
        }
    }
}
