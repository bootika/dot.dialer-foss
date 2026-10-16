package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.*
import org.koin.core.module.dsl.viewModel
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.screen.settings.components.*
import dev.goodwy.rphone.controller.license.VerifiedPremiumTheme as DotDialerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsReorganizationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var prefs: PreferenceManager
    private lateinit var visual: Context
    private lateinit var navigator: DestinationsNavigator
    private val routes = mutableListOf<String>()

    @Before fun prepare() {
        val directory = File(rule.activity.cacheDir, "settings-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(rule.activity) {
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }
        }
        io.github.bootika.dotdialer.diagnostics.AppDiagnostics.initialize(isolated)
        prefs = PreferenceManager(isolated)
        runBlocking { prefs.restoreAllPreferences(mapOf(
            PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE to false,
            PreferenceManager.KEY_THEME_MODE to "auto", PreferenceManager.KEY_LG_DIALPAD to false,
            PreferenceManager.KEY_SCROLL_HAPTICS to false,
            AmbientDropletPreference.ENABLED_KEY to false,
            PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS to false,
            WhatsAppAvatarSyncPreferences.ENABLED to false,
            WhatsAppAvatarSyncPreferences.NORMALIZE_NUMBERS to false,
        )) }
        visual = object : ContextWrapper(rule.activity) {
            override fun getDisplay(): android.view.Display = rule.activity.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }
        navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, args ->
            if (method.name == "navigate") routes += args!!.first().toString()
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        val flow = MutableStateFlow(false)
        val purchase = Proxy.newProxyInstance(PurchaseHelper::class.java.classLoader,
            arrayOf(PurchaseHelper::class.java)) { _, method, _ ->
            when (method.name) { "getIsPro", "isPro" -> flow; "hashCode" -> 1; else -> null }
        } as PurchaseHelper
        val contacts = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
            arrayOf(IContactsRepository::class.java)) { _, contractMethod, _ ->
            if (contractMethod.name == "getChanges") kotlinx.coroutines.flow.emptyFlow<Unit>()
            else emptyList<Any>()
        } as IContactsRepository
        val authorization = object : GoogleContactsAuthorization {
            override val isAvailable = dev.goodwy.rphone.BuildConfig.FLAVOR == "gplay"
            override suspend fun authorize(accountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override fun completeAuthorization(data: android.content.Intent?, requestedAccountName: String?) =
                GoogleContactsAuthorizationResult.Unavailable
            override suspend fun revoke(accountName: String) = true
        }
        val people = object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult =
                error("No photo requests are allowed in settings navigation tests")
        }
        val hydration = CallPhotoHydrationManager(isolated, prefs, authorization, people)
        val reviews = WhatsAppAvatarReviewStore(isolated)
        val processor = WhatsAppAvatarSyncProcessor(isolated, prefs, reviews, WhatsAppAvatarContactStore(isolated), hydration)
        startKoin { modules(module {
            single { prefs }; single<PurchaseHelper> { purchase }; single<IContactsRepository> { contacts }
            single<GoogleContactsAuthorization> { authorization }
            single { reviews }; single { processor }
            single { GoogleContactPhotoScan(isolated, prefs, authorization, hydration) }
            viewModel { ContactsViewModel(rule.activity.application, contacts, prefs) }
        }) }
    }
    @After fun cleanup() = stopKoin()

    private fun show(content: @Composable () -> Unit) {
        val software = object : android.view.View(visual) { override fun isHardwareAccelerated() = false }
        rule.runOnIdle { (rule.activity.window.decorView as android.view.ViewGroup).addView(software,
            android.view.ViewGroup.LayoutParams(0, 0)) }
        rule.setContent { CompositionLocalProvider(LocalContext provides visual, LocalView provides software) {
            DotDialerTheme { content() }
        } }
    }
    private fun settled() { rule.mainClock.advanceTimeBy(1200); rule.waitForIdle() }
    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(System.getProperty("user.dir"), "build/reports/visual/settings-d106/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun focusedSearchBringsExactControlAndNeverEnablesDependentFeatures() {
        var id by mutableStateOf("H09")
        show { key(id) { SettingsOptionScreen(navigator, id) } }
        for ((requested, target) in listOf("H09" to "H09", "I12" to "I12", "A09" to "A06",
            "C04" to "C03", "K03" to "K03", "V08" to "V07", "I11" to "I11", "A02" to "A02")) {
            rule.runOnIdle { id = requested }
            settled()
            if (requested == "K03") {
                capture("search-K03-before-assert")
                File(System.getProperty("user.dir"), "build/reports/visual/settings-d106/search-K03-tree.txt")
                    .writeText(rule.onNode(isRoot()).printToString())
            }
            rule.onAllNodesWithTag("settingsFocus-$target", true).onFirst().assertIsDisplayed()
            capture("search-$requested")
        }
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD, true))
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, true))
        assertFalse(prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, true))
    }

    @Test fun mainMenuClassifiesByFunctionAndInterfaceOwnsInteractionAndCallPresentation() {
        var scene by mutableStateOf("root")
        val before = runBlocking { prefs.getPersistedPreferences() }
        show { key(scene) { when (scene) {
            "interface" -> InterfaceScreen(navigator)
            "calls" -> CallSettingScreen(navigator)
            else -> SettingsHomeContent(rememberLazyListState(), PaddingValues(), "", {}, emptyList(), navigator,
                "2.5.1-test", "Local", {}, {}, {})
        } } }
        settled()
        for (label in listOf(R.string.interface_settings, R.string.call_behavior, R.string.contacts_and_data,
            R.string.sound_and_vibration, R.string.privacy_and_blocking, R.string.help_and_diagnostics)) {
            rule.onNodeWithText(rule.activity.getString(label)).performScrollTo().performClick()
        }
        assertEquals(6, routes.size)
        for (oldRoot in listOf(R.string.calls, R.string.home_and_gestures, R.string.accessibility, R.string.advanced_settings))
            rule.onAllNodesWithText(rule.activity.getString(oldRoot)).assertCountEquals(0)
        rule.runOnIdle { scene = "interface" }; settled()
        for (label in listOf(R.string.call_screen, R.string.home_and_gestures, R.string.accessibility))
            rule.onNodeWithText(rule.activity.getString(label)).performScrollTo().performClick()
        capture("function-interface-links")
        assertTrue(routes.any { it.contains("accessibility=true") })
        rule.runOnIdle { scene = "calls" }; settled()
        for (label in listOf(R.string.call_screen, R.string.fullscreen_calls, R.string.floating_ongoing_call,
            R.string.incoming_call_ui, R.string.liquid_glass_call_screen))
            rule.onAllNodesWithText(rule.activity.getString(label)).assertCountEquals(0)
        rule.onNodeWithText(rule.activity.getString(R.string.add_country_code_automatically)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.proximity_sensor)).performScrollTo().assertIsDisplayed()
        assertEquals(before, runBlocking { prefs.getPersistedPreferences() })
    }

    @Test fun movedControlsRemainFocusedAndOnlyExplicitChangesWriteTheirOriginalKeys() {
        var id by mutableStateOf("K09")
        val before = runBlocking { prefs.getPersistedPreferences() }
        show { key(id) { SettingsOptionScreen(navigator, id) } }
        for (target in listOf("K09", "K08", "C05", "C06", "I10", "K02", "K03", "K06", "C02", "A07", "C08")) {
            rule.runOnIdle { id = target }; settled()
            capture("function-search-$target")
            File(System.getProperty("user.dir"), "build/reports/visual/settings-d106/function-search-$target.txt")
                .writeText(rule.onNode(isRoot()).printToString())
            rule.onAllNodesWithTag("settingsFocus-$target", true).onFirst().assertIsDisplayed()
        }
        assertEquals(before, runBlocking { prefs.getPersistedPreferences() })
        rule.runOnIdle { id = "K09" }; settled()
        val original = prefs.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS,
            io.github.bootika.dotdialer.core.call.CallPresentationPolicy.DEFAULT_ALWAYS_FULL_SCREEN)
        rule.onNodeWithText(rule.activity.getString(R.string.fullscreen_calls)).performClick()
        rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, original) != original }
        val after = runBlocking { prefs.getPersistedPreferences() }
        assertEquals(setOf(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS),
            (before.keys + after.keys).filter { before[it] != after[it] }.toSet())
    }

    @Test fun androidAutoKeepsImmediateStateWhenStoredPreferenceAlreadyMatches() {
        val auto = dev.goodwy.rphone.auto.AndroidAutoIntegration
        val originalComponent = auto.isEnabled(rule.activity)
        try {
            runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_ANDROID_AUTO_ENABLED to true)) }
            auto.setEnabled(rule.activity, false)
            show { CallSettingScreen(navigator) }; settled()
            val label = rule.activity.getString(R.string.android_auto_integration)
            rule.onNodeWithText(label).performScrollTo().performClick(); settled()
            assertTrue(auto.isEnabled(rule.activity))
            assertTrue(prefs.getBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, false))
            // A second tap must disable it even when the first write emitted no new preference value.
            rule.onNodeWithText(label).performClick(); settled()
            assertFalse(auto.isEnabled(rule.activity))
            rule.waitUntil(5000) { !prefs.getBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, true) }
        } finally { auto.setEnabled(rule.activity, originalComponent) }
    }

    @Test fun movingRecentsAndScrollSwitchesChangesOnlyTheirExistingKeys() {
        var scene by mutableStateOf("home")
        show { key(scene) { if (scene == "home") HomeGesturesScreen(navigator) else SoundVibrationScreen(navigator) } }
        settled()
        val before = runBlocking { prefs.getPersistedPreferences() }
        rule.onNodeWithText(rule.activity.getString(R.string.show_all_recent_calls)).performScrollTo().performClick()
        rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, false) }
        rule.runOnIdle { scene = "sound" }; settled()
        rule.onNodeWithText(rule.activity.getString(R.string.scroll_haptics)).performScrollTo().performClick()
        rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, false) }
        val after = runBlocking { prefs.getPersistedPreferences() }
        val changed = (before.keys + after.keys).filter { before[it] != after[it] }.toSet()
        assertEquals(setOf(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, PreferenceManager.KEY_SCROLL_HAPTICS), changed)
        rule.onNodeWithText(rule.activity.getString(R.string.settings_scroll_calibration)).assertIsDisplayed()
    }

    @Test fun everyOptionHasCanonicalBreadcrumbAndSearchKeepsOldNames() {
        var entries = emptyList<SettingsSearchEntry>()
        var breadcrumbs = emptyMap<String, String>()
        show {
            entries = createSettingsSearchEntries(visual, navigator, "2.2.6-test", "Local", {}, {}, {})
            breadcrumbs = settingsOptions.filter { it.isAvailable("gplay", true) }.associate { it.id to settingsBreadcrumb(it) }
        }
        settled()
        assertTrue(breadcrumbs.getValue("H10").contains(rule.activity.getString(R.string.home_and_gestures)))
        for (id in listOf("C01", "C05", "C06", "K08", "K09", "C02", "A07", "C08", "I10", "H01", "H10"))
            assertTrue(id, breadcrumbs.getValue(id).startsWith(rule.activity.getString(R.string.settings) + " › " + rule.activity.getString(R.string.interface_settings)))
        for (id in listOf("T02", "T03", "T19"))
            assertTrue(id, breadcrumbs.getValue(id).contains(rule.activity.getString(R.string.contacts_and_data)))
        for (id in listOf("K02", "K03", "K06"))
            assertTrue(id, breadcrumbs.getValue(id).contains(rule.activity.getString(R.string.call_behavior)))
        assertTrue(breadcrumbs.getValue("O02").contains(rule.activity.getString(R.string.help_and_diagnostics)))
        assertTrue(breadcrumbs.getValue("I11").contains(rule.activity.getString(R.string.accessibility)))
        assertTrue(breadcrumbs.getValue("B05").contains(rule.activity.getString(R.string.privacy_and_blocking)))
        assertTrue(breadcrumbs.getValue("O01").contains(rule.activity.getString(R.string.privacy_and_blocking)))
        assertTrue(breadcrumbs.getValue("V09").contains(rule.activity.getString(R.string.sound_and_vibration)))
        assertFalse(settingsOptions.single { it.id == "A04" }.isAvailable("gplay", true))
        val entry = entries.single { it.headline == rule.activity.getString(R.string.clear_search_after_call) }
        assertTrue(entry.options.contains(rule.activity.getString(R.string.settings_alias_clear_search_after_call)))
        entry.onClick()
        assertTrue(routes.last().contains("H05"))
    }

    @Test fun largeFontChoicesKeepFullLabelsAndMinimumTouchHeight() {
        var selected by mutableIntStateOf(0)
        show { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
            DotSettingsChoiceRow("Call appearance", "", listOf("Circular avatar" to 0, "Full photo" to 1),
                selected, { selected = it })
        } }
        settled()
        val choices = rule.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected), true)
            .fetchSemanticsNodes()
        assertEquals(2, choices.size)
        assertTrue(choices.all { it.boundsInRoot.height >= 48f })
        capture("large-font-choice-before-assert")
        for (label in listOf("Circular avatar", "Full photo")) {
            val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(label, true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertFalse(label + ": " + layout.single().size + ", width=" + layout.single().didOverflowWidth +
                ", height=" + layout.single().didOverflowHeight, layout.single().hasVisualOverflow)
        }
        rule.onNodeWithText("Full photo").performClick()
        assertEquals(1, selected)
        capture("large-font-choice-labels")
    }

    @Test fun realNavigationReturnsToTheSameSearchQueryWithoutChangingPreferences() {
        val before = runBlocking { prefs.getPersistedPreferences() }
        show {
            val controller = androidx.navigation.compose.rememberNavController()
            com.ramcosta.composedestinations.DestinationsNavHost(
                navGraph = com.ramcosta.composedestinations.generated.NavGraphs.root,
                navController = controller,
                start = com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination,
            )
        }
        settled()
        rule.onNodeWithText(rule.activity.getString(R.string.interface_settings)).performScrollTo().performClick()
        settled()
        rule.onNodeWithText(rule.activity.getString(R.string.call_screen)).performScrollTo().performClick()
        settled()
        rule.onNodeWithText(rule.activity.getString(R.string.incoming_call_ui)).assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(rule.activity.getString(R.string.fullscreen_calls)))
        rule.onNodeWithText(rule.activity.getString(R.string.fullscreen_calls)).performScrollTo().assertIsDisplayed()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; settled()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; settled()
        val query = rule.activity.getString(R.string.voice_search_title)
        rule.onNode(hasSetTextAction()).performTextInput(query)
        rule.onNode(hasText(query) and !hasSetTextAction()).performClick()
        settled()
        rule.onNodeWithTag("settingsFocus-I12", true).assertIsDisplayed()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settled()
        rule.onNode(hasSetTextAction()).assertTextContains(query)
        assertEquals(before, runBlocking { prefs.getPersistedPreferences() })
    }

    @Test fun contactAccountAndSecurityEditorsRetainTheirGatesAndFocusedParents() {
        var id by mutableStateOf("T11")
        show { key(id) { SettingsOptionScreen(navigator, id) } }
        for ((requested, target) in listOf("T11" to "T15", "T12" to "T15", "T14" to "T15", "T16" to "T15", "T17" to "T15", "T19" to "T15",
            "T02" to "T02", "T03" to "T03", "K01" to "K01", "K10" to "K10",
            "B01" to "B01", "B03" to "B02", "B05" to "B02", "O01" to "O01", "O02" to "O02")) {
            rule.runOnIdle { id = requested }; settled()
            rule.onAllNodesWithTag("settingsFocus-$target", true).onFirst().assertIsDisplayed()
            capture("editor-$requested")
        }
        assertFalse(prefs.getBoolean(WhatsAppAvatarSyncPreferences.ENABLED, false))
        assertFalse(prefs.isAuthenticationEnabled())
    }

    @Test fun pagesRemainReadableAtLargeFontsInBothThemes() {
        var scene by mutableStateOf("home")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
            DotDialerTheme(systemDark = dark) { key(scene, dark, font) {
                when (scene) {
                    "home" -> SettingsHomeContent(rememberLazyListState(), PaddingValues(), "", {}, emptyList(), navigator, "2.2.6-test", "Local", {}, {}, {})
                    "advanced" -> AdvancedSettingsScreen(navigator)
                    "interface" -> InterfaceScreen(navigator)
                    "accessibility" -> InterfaceScreen(navigator, accessibility = true)
                    "recents" -> HomeGesturesScreen(navigator)
                    "avatars" -> AvatarsPreferenceScreen(navigator)
                    "calls" -> CallSettingScreen(navigator)
                    "call-ui" -> CallerUIScreen(navigator)
                    "contacts" -> ContactManagementScreen(navigator)
                    "security" -> BiometricScreen(navigator)
                    "sound" -> SoundVibrationScreen(navigator)
                    "privacy" -> SettingsCategoryScreen(navigator, "privacy")
                    "reporting" -> ReportingSettingsScreen(navigator)
                    "help" -> SettingsCategoryScreen(navigator, "help")
                }
            } }
        } }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 1.5f, 2f))
            for (page in listOf("home", "advanced", "interface", "accessibility", "recents", "avatars", "calls", "call-ui", "contacts", "security", "sound", "privacy", "reporting", "help")) {
                rule.runOnIdle { scene = page; dark = theme; font = scale }
                settled()
                capture("$page-${if (theme) "dark" else "light"}-$scale")
            }
    }

    @Test fun legacyFlagsCannotActivateNewAccountPhotoSourcesOrSkipAccountChoice() {
        runBlocking { prefs.restoreAllPreferences(mapOf(WhatsAppAvatarSyncPreferences.ENABLED to true)) }
        var id by mutableStateOf("T12")
        show { key(id) { SettingsOptionScreen(navigator, id) } }
        for (target in listOf("T12", "T14", "T16", "T17", "T19")) {
            rule.runOnIdle { id = target }; settled()
            rule.onNodeWithTag("settingsFocus-T15", true).assertIsDisplayed()
        }
        assertNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null))
        assertFalse(settingsOptions.single { it.id == "T13" }.isAvailable("foss", true))
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun canonicalPagesStayReachableInLandscape() {
        var scene by mutableStateOf("advanced")
        show { key(scene) {
            when (scene) {
                "advanced" -> AdvancedSettingsScreen(navigator)
                "recents" -> HomeGesturesScreen(navigator)
                "accessibility" -> InterfaceScreen(navigator, accessibility = true)
                "calls" -> CallSettingScreen(navigator)
                "call-ui" -> CallerUIScreen(navigator)
            }
        } }
        for ((page, label) in listOf("advanced" to R.string.call_screen,
            "recents" to R.string.voice_search_title, "accessibility" to R.string.high_contrast_text,
            "calls" to R.string.add_country_code_automatically, "call-ui" to R.string.fullscreen_calls)) {
            rule.runOnIdle { scene = page }; settled()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(rule.activity.getString(label)))
            rule.onNodeWithText(rule.activity.getString(label)).performScrollTo().assertIsDisplayed()
            capture("landscape-$page")
        }
    }
}
