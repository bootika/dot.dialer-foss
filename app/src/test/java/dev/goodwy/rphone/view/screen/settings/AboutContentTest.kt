package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.*
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import dev.goodwy.rphone.view.screen.settings.components.createSettingsSearchEntries
import dev.goodwy.rphone.view.screen.settings.components.SettingsSearchEntry
import java.io.File
import java.lang.reflect.Proxy
import java.util.Calendar
import java.util.Locale
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class AboutContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }

    @Test fun `credits retain upstream and inspirations with developer first`() {
        assertEquals("Alexandru Butica", appContributors.first().name)
        assertEquals(R.string.contributor_developer, appContributors.first().roleResource)
        assertEquals("https://github.com/bootika", appContributors.first().githubUrl)
        assertEquals(listOf("Alexandru Butica"), appContributors.map { it.name })
        assertTrue((appContributors + otherContributors).none { it.name == "UranophileLab" })
        assertEquals("https://github.com/Goodwy/RPhone", otherContributors.first().githubUrl)
        assertEquals(R.string.contributor_upstream, otherContributors.first().roleResource)
        assertEquals(listOf("Goodwy — Rill Phone", "RivoPhoneApp", "Ever-Dialer", "Phone by Google", "ExDialer", "Samsung Phone", "Thunderbird", "Jira"), otherContributors.map { it.name })
        assertEquals(privacyPolicyUrl("ro"), privacyPolicyUrl("ro-RO"))
        assertEquals(PRIVACY_POLICY, privacyPolicyUrl("fr"))
        assertEquals(TERMS_AND_CONDITIONS, termsAndConditionsUrl("en"))
        assertEquals("https://dotdesign.ro/aplicatii/dot-dialer/confidentialitate/", privacyPolicyUrl("ro"))
        assertEquals("https://dotdesign.ro/aplicatii/dot-dialer/termeni/", termsAndConditionsUrl("ro"))
    }

    @Test fun `production About and credits render with localized real link intents`() {
        val app = RuntimeEnvironment.getApplication()
        startKoin { modules(module { single { PreferenceManager(app) } }) }
        val navigation = mutableListOf<String>()
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, args ->
            if (method.name == "navigate") navigation.add(args!!.first().toString())
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        var language by mutableStateOf("en")
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var page by mutableStateOf("about")
        var renderedContext: android.content.Context = app
        var searchEntries = emptyList<SettingsSearchEntry>()
        rule.setContent {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val base = rule.activity.createConfigurationContext(config)
            val context = object : ContextWrapper(base) {
                override fun getDisplay(): android.view.Display = rule.activity
                    .getSystemService(android.hardware.display.DisplayManager::class.java)
                    .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            }
            renderedContext = context
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    searchEntries = createSettingsSearchEntries(context, navigator, "1.9.0-test", "Local", {}, {}, {})
                    key(language, dark, scale, page) {
                        if (page == "about") AboutAppScreen(navigator) else ContributorsScreen(navigator)
                    }
                }
            }
        }
        val output = File(System.getProperty("user.dir"), "build/reports/visual/about")
        fun capture(name: String) {
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                output.mkdirs()
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        fun open(label: Int, url: String) {
            val shadow = Shadows.shadowOf(app)
            while (shadow.nextStartedActivity != null) { /* drain earlier intents */ }
            rule.onNodeWithText(renderedContext.getString(label)).performScrollTo().performClick()
            val intent = requireNotNull(shadow.nextStartedActivity)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(url, intent.data.toString())
        }
        for ((lang, font) in listOf("en" to 1f, "ro" to 1.5f)) for (mode in listOf(false, true)) {
            rule.runOnUiThread { language = lang; scale = font; dark = mode; page = "about" }
            val id = "$lang-${if (mode) "dark" else "light"}-font$font"
            capture("$id-top")
            open(R.string.source_code, GITHUB_URL)
            open(R.string.privacy_policy, privacyPolicyUrl(lang))
            open(R.string.terms_and_conditions, termsAndConditionsUrl(lang))
            val shadow = Shadows.shadowOf(app)
            for ((label, url) in listOf(R.string.privacy_policy to privacyPolicyUrl(lang),
                R.string.terms_and_conditions to termsAndConditionsUrl(lang))) {
                searchEntries.single { it.headline == renderedContext.getString(label) }.onClick()
                assertEquals(url, requireNotNull(shadow.nextStartedActivity).data.toString())
            }
            open(R.string.dark_edge_radio, DARK_EDGE_RADIO_URL)
            open(R.string.dot_dictate, dotDictateUrl(lang))
            val copyright = renderedContext.getString(R.string.about_copyright, Calendar.getInstance().get(Calendar.YEAR))
            assertTrue(copyright.startsWith("© Dot Design"))
            rule.onNodeWithText(copyright).performScrollTo().assertIsDisplayed()
            capture("$id-footer")
            rule.onNodeWithText(renderedContext.getString(R.string.contributors)).performScrollTo().performClick()
            assertTrue(navigation.last().contains("contributors", ignoreCase = true))
            rule.runOnUiThread { page = "credits" }
            capture("$id-credits-top")
            rule.onNodeWithText("Alexandru Butica").assertIsDisplayed()
            rule.onNodeWithText("UranophileLab").assertDoesNotExist()
            openGoodwy(app)
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("ExDialer"))
            rule.onNodeWithText("ExDialer").assertIsDisplayed()
            capture("$id-credits-inspiration")
        }
    }

    private fun openGoodwy(app: Application) {
        val shadow = Shadows.shadowOf(app)
        while (shadow.nextStartedActivity != null) { /* drain earlier intents */ }
        rule.onNodeWithText("Goodwy — Rill Phone").performScrollTo().performClick()
        val intent = requireNotNull(shadow.nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://github.com/Goodwy/RPhone", intent.data.toString())
    }
}
