package dev.goodwy.rphone.view.screen.settings

import android.app.Application
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
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
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
class SupportContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }

    private fun prefs(): PreferenceManager {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        prefs.setBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        rule.waitUntil(5_000) { !prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, true) }
        startKoin { modules(module { single { prefs } }) }
        return prefs
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File(System.getProperty("user.dir"), "build/reports/visual/support/" + BuildConfig.FLAVOR)
            output.mkdirs()
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun `shared store presentation handles selection loading offline ownership and long prices`() {
        prefs()
        var selected by mutableStateOf("yearly")
        var mode by mutableStateOf("ready")
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var language by mutableStateOf("en")
        val purchased = mutableListOf<String>()
        var restores = 0
        var backs = 0
        rule.setContent {
            val config = Configuration(rule.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val context = rule.activity.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    key(language, dark, scale, mode) {
                        val plans = listOf(
                            SupportPlan("monthly", context.getString(R.string.monthly),
                                context.getString(R.string.plan_monthly_description), "9,99 RON", "", true),
                            SupportPlan("yearly", context.getString(R.string.yearly),
                                context.getString(R.string.plan_yearly_description), "999.999,99 Romanian lei", "", true),
                            SupportPlan("lifetime", context.getString(R.string.lifetime),
                                context.getString(R.string.plan_lifetime_description), "99,99 RON", "", false),
                        ).map { it.copy(owned = mode == "owned",
                            available = mode != "offline" && mode != "loading",
                            price = when (mode) {
                                "offline" -> context.getString(R.string.no_connection)
                                "loading" -> context.getString(R.string.loading_price)
                                else -> it.price
                            }) }
                        StoreSupportContent(plans, selected, mode == "loading",
                            { backs++ }, { selected = it }, { purchased.add(it.id) }, { restores++ })
                    }
                }
            }
        }
        rule.onNodeWithTag("support-continue").assertIsEnabled().performClick()
        assertEquals(listOf("yearly"), purchased)
        rule.onNodeWithTag("support-plan-monthly").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("support-continue").performClick()
        assertEquals(listOf("yearly", "monthly"), purchased)
        rule.onNodeWithTag("support-plan-lifetime").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("support-continue").performClick()
        assertEquals("lifetime", purchased.last())
        rule.onNodeWithTag("support-restore").performClick()
        assertEquals(1, restores)
        for (state in listOf("loading", "offline", "owned")) {
            rule.runOnUiThread { mode = state }
            rule.onNodeWithTag("support-continue").assertIsNotEnabled()
            rule.onNodeWithTag("support-plan-yearly").performScrollTo().assertIsNotEnabled()
            if (state == "loading") rule.onNodeWithTag("support-restore").assertIsNotEnabled()
            else rule.onNodeWithTag("support-restore").assertIsEnabled()
            capture(state)
        }
        assertEquals(3, purchased.size)
        for (lang in listOf("en", "ro")) for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f)) {
            rule.runOnUiThread { mode = "ready"; selected = "yearly"; language = lang; dark = theme; scale = font }
            rule.onNodeWithTag("support-plan-yearly").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("999.999,99 Romanian lei").assertIsDisplayed()
            val content = rule.onNodeWithTag("support-content").fetchSemanticsNode().boundsInRoot
            val footer = rule.onNodeWithTag("support-footer").fetchSemanticsNode().boundsInRoot
            assertTrue("Measured footer must not overlay the scroll viewport", content.bottom <= footer.top + 1f)
            rule.onNodeWithTag("support-continue").assertIsDisplayed()
            val id = "$lang-" + (if (theme) "dark" else "light") + "-font$font"
            capture("$id-yearly")
            rule.onNodeWithTag("support-heading").performScrollTo()
            capture("$id-top")
        }
        rule.runOnUiThread { language = "en" }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back)).performClick()
        assertEquals(1, backs)
    }

    @Test fun `production flavor screen retains donation or checkout restore and back callbacks`() {
        val prefs = prefs()
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val owned = mutableSetOf<String>()
        val pro = MutableStateFlow(false)
        val success = MutableStateFlow(false)
        val loading = MutableStateFlow(false)
        val ready = MutableStateFlow(true)
        val iapLoaded = MutableStateFlow(true)
        val subLoaded = MutableStateFlow(true)
        val error = MutableStateFlow<String?>(null)
        val helper = Proxy.newProxyInstance(PurchaseHelper::class.java.classLoader,
            arrayOf(PurchaseHelper::class.java)) { _, method, args ->
            when (method.name) {
                "getIsPro", "isPro" -> pro
                "getProCheckDone" -> MutableStateFlow(true)
                "getPurchaseSuccess" -> success
                "getIsLoading", "isLoading" -> loading
                "getErrorMessage" -> error
                "getIsBillingReady", "isBillingReady" -> ready
                "getIapSkuDetailsInitialized" -> iapLoaded
                "getSubSkuDetailsInitialized" -> subLoaded
                "getPriceSubscription", "getPriceDonation" -> "9,99 RON"
                "isIapPurchased", "isSubPurchased" -> args!!.first() in owned
                "clearPurchaseSuccess" -> { success.value = false; null }
                "clearErrors" -> { calls.add(method.name to emptyList()); error.value = null; null }
                else -> { calls.add(method.name to args.orEmpty().toList()); null }
            }
        } as PurchaseHelper
        org.koin.core.context.loadKoinModules(module { single<PurchaseHelper> { helper } })
        var backs = 0
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.name == "navigateUp") backs++
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        rule.setContent { DotDialerTheme { DonateScreen(navigator) } }
        if (BuildConfig.FLAVOR == "foss") {
            rule.onNodeWithText(rule.activity.getString(R.string.unlock_all_features)).performScrollTo().performClick()
            rule.waitUntil(5_000) { prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false) }
            rule.runOnIdle { assertTrue(prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)) }
            rule.onNodeWithText(rule.activity.getString(R.string.unlock_all_features)).performClick()
            rule.waitUntil(5_000) { !prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, true) }
            rule.runOnIdle { assertFalse(prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, true)) }
            rule.onNodeWithText(rule.activity.getString(R.string.support_all_options)).performClick()
            assertEquals(DONATE_URL, Shadows.shadowOf(rule.activity).nextStartedActivity.data.toString())
            rule.onAllNodesWithText(rule.activity.getString(R.string.support_development)).onLast().performClick()
            assertEquals(COFFEE_URL, Shadows.shadowOf(rule.activity).nextStartedActivity.data.toString())
        } else {
            if (BuildConfig.PRODUCT_ID_X1.isBlank() && BuildConfig.SUBSCRIPTION_ID_X1.isBlank()
                && BuildConfig.SUBSCRIPTION_YEAR_ID_X1.isBlank()) {
                rule.onNodeWithTag("support-continue").assertIsNotEnabled()
                rule.onNodeWithTag("support-plan-monthly").performScrollTo().assertIsNotSelected().assertIsNotEnabled()
                rule.onNodeWithTag("support-plan-yearly").performScrollTo().assertIsSelected().assertIsNotEnabled()
                rule.onNodeWithTag("support-plan-lifetime").performScrollTo().assertIsNotSelected().assertIsNotEnabled()
                rule.onNodeWithTag("support-restore").performClick()
                assertTrue(calls.any { it.first == if (BuildConfig.FLAVOR == "gplay") "refreshAllData" else "restorePurchases" })
                assertFalse(calls.any { it.first == "purchaseDonation" || it.first == "purchaseSubscription" })
                rule.runOnUiThread { loading.value = true }
                rule.onNodeWithTag("support-restore").assertIsNotEnabled()
                rule.runOnUiThread { loading.value = false; success.value = true }
                rule.waitForIdle()
                assertTrue(calls.any { it.first == "checkProStatus" })
                rule.runOnUiThread { error.value = "Payment temporarily unavailable" }
                rule.waitForIdle()
                assertTrue(calls.any { it.first == "clearErrors" })
                capture("production-unconfigured")
                rule.onNodeWithContentDescription(rule.activity.getString(R.string.back)).performClick()
                assertEquals(1, backs)
                return
            }
            rule.onNodeWithTag("support-continue").performClick()
            assertEquals(BuildConfig.SUBSCRIPTION_YEAR_ID_X1, calls.last { it.first == "purchaseSubscription" }.second.first())
            rule.onNodeWithTag("support-plan-lifetime").performScrollTo().performClick()
            rule.onNodeWithTag("support-continue").performClick()
            assertEquals(BuildConfig.PRODUCT_ID_X1, calls.last { it.first == "purchaseDonation" }.second.first())
            rule.onNodeWithTag("support-restore").performClick()
            assertTrue(calls.any { it.first == if (BuildConfig.FLAVOR == "gplay") "refreshAllData" else "restorePurchases" })
            rule.runOnUiThread { loading.value = true }
            rule.onNodeWithTag("support-continue").assertIsNotEnabled()
            rule.runOnUiThread { loading.value = false; iapLoaded.value = false; subLoaded.value = false }
            rule.onNodeWithTag("support-continue").assertIsNotEnabled()
            rule.runOnUiThread { ready.value = false }
            rule.onNodeWithTag("support-continue").assertIsNotEnabled()
            rule.runOnUiThread {
                ready.value = true; iapLoaded.value = true; subLoaded.value = true
                owned.add(BuildConfig.PRODUCT_ID_X1); pro.value = true
            }
            rule.onNodeWithTag("support-continue").assertIsNotEnabled()
            rule.onNodeWithTag("support-plan-lifetime").performScrollTo().assertIsNotEnabled()
            rule.runOnUiThread { success.value = true }
            rule.waitForIdle()
            assertTrue(calls.any { it.first == "checkProStatus" })
            rule.runOnUiThread { error.value = "Payment temporarily unavailable" }
            rule.waitForIdle()
            assertTrue(calls.any { it.first == "clearErrors" })
        }
        capture("production")
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back)).performClick()
        assertEquals(1, backs)
    }
}
