package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.contactAvatarSizeDp
import dev.goodwy.rphone.view.components.rillAvatarAppearance
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class AvatarSettingsPreviewRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }

    @Test fun `production avatar page preview reacts to saved controls and keeps example actions inert`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var advanced by mutableStateOf(false)
        var navigation = 0
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.name == "navigate" || method.name == "navigateUp") navigation++
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        fun defaults() {
            runBlocking { prefs.restoreAllPreferences(mapOf(
                PreferenceManager.KEY_SHOW_PICTURE to true,
                PreferenceManager.KEY_SHOW_FIRST_LETTER to true,
                PreferenceManager.KEY_AVATAR_FRAME to false,
                PreferenceManager.KEY_CONTACT_AVATAR_SIZE to PreferenceManager.CONTACT_AVATAR_SIZE_MEDIUM,
                PreferenceManager.KEY_CONTACT_NAME_SIZE_SP to 18,
                PreferenceManager.KEY_EXPANDED_LIST_DETAILS to false,
            )) }
            rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, false) &&
                prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, false) && prefs.contactAvatarSizeDp() == 46 &&
                !prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, true) }
        }
        defaults()
        startKoin { modules(module { single { prefs } }) }
        val visualContext = object : ContextWrapper(rule.activity) {
            override fun getDisplay(): Display = rule.activity.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
        }
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font), LocalContext provides visualContext) {
                DotDialerTheme(systemDark = dark) { AvatarsPreferenceScreen(navigator, advanced) }
            }
        }
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            defaults()
            rule.runOnIdle { dark = mode; font = scale; advanced = false }
            val prefix = "${if(mode) "dark" else "light"}-font${(scale*100).toInt()}"
            val initial = capture("$prefix-medium-photo")
            rule.onNodeWithText("John Doe").assertDoesNotExist()
            rule.onNodeWithText("Jane Doe").assertDoesNotExist()
            rule.onNodeWithText(context.getString(R.string.recents)).assertExists()
            rule.onNodeWithText(context.getString(R.string.avatar_colors)).assertDoesNotExist()
            rule.onNodeWithText(context.getString(R.string.show_picture_in_avatar)).performScrollTo().performClick()
            rule.waitUntil(5000) { !prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true) }
            val letters = capture("$prefix-medium-letters")
            assertFalse("Photo Off must change real preview pixels", initial.sameAs(letters))
            rule.onNodeWithText(context.getString(R.string.show_first_letter_in_avatar)).performScrollTo().performClick()
            rule.waitUntil(5000) { !prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true) }
            val icons = capture("$prefix-medium-icons")
            assertFalse("Initial Off must change fallback preview pixels", letters.sameAs(icons))
            for ((label, value, size) in listOf(
                Triple(R.string.avatar_size_small, PreferenceManager.CONTACT_AVATAR_SIZE_SMALL, 40),
                Triple(R.string.avatar_size_large, PreferenceManager.CONTACT_AVATAR_SIZE_LARGE, 52),
            )) {
                rule.onNodeWithText(context.getString(label)).performScrollTo().performClick()
                rule.waitUntil(5000) { prefs.getString(PreferenceManager.KEY_CONTACT_AVATAR_SIZE, "") == value }
                assertEquals(size, prefs.contactAvatarSizeDp())
                assertEquals(value, PreferenceManager(context).getString(PreferenceManager.KEY_CONTACT_AVATAR_SIZE, ""))
                val sized = capture("$prefix-$value-icons")
                assertFalse("List size must affect real preview", icons.sameAs(sized))
            }
            val before = capture("$prefix-inert-before")
            rule.onAllNodesWithTag("avatar-preview-row-0").assertCountEquals(1)
            rule.onNodeWithTag("avatar-preview-row-1").assertDoesNotExist()
            val row = rule.onNodeWithTag("avatar-preview-row-0")
            row.assert(hasNoClickAction())
            row.performTouchInput { click(); longClick(); swipeLeft(); swipeRight() }
            assertEquals(0, navigation)
            assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
            assertFalse(prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true))
            assertFalse(prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true))
            assertTrue("Inert gestures must not alter preview", before.sameAs(capture("$prefix-inert-after")))
            runBlocking { prefs.restoreAllPreferences(mapOf(
                PreferenceManager.KEY_CONTACT_NAME_SIZE_SP to 22,
                PreferenceManager.KEY_EXPANDED_LIST_DETAILS to true,
            )) }
            rule.waitUntil(5000) { prefs.getInt(PreferenceManager.KEY_CONTACT_NAME_SIZE_SP, 18) == 22 &&
                prefs.getBoolean(PreferenceManager.KEY_EXPANDED_LIST_DETAILS, false) }
            assertFalse("Shared typography must react without remount", before.sameAs(capture("$prefix-expanded-name22")))
            defaults()
            rule.runOnIdle { advanced = true }
            val noFrame = capture("$prefix-frame-off")
            rule.onNodeWithText(context.getString(R.string.show_picture_in_avatar)).assertExists()
            rule.onNodeWithText(context.getString(R.string.avatar_frame)).performScrollTo().performClick()
            rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false) }
            capture("$prefix-frame-on")
            assertTrue(prefs.rillAvatarAppearance().avatarFrame)
            // The upstream frame is white and can blend into a light white card; preserve its real appearance.
        }
    }

    private fun capture(name: String): Bitmap {
        rule.onNodeWithTag("avatar-recents-preview").performScrollTo()
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        var preview: Bitmap? = null
        val bounds = rule.onNodeWithTag("avatar-recents-preview").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/avatar-settings-preview/" + BuildConfig.FLAVOR)
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            preview = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt(), bounds.height.toInt())
        }
        return requireNotNull(preview)
    }
}
