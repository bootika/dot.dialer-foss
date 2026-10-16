package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
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
class AuthenticationScreenRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @org.junit.Before fun reset() {
        kotlinx.coroutines.runBlocking { PreferenceManager(RuntimeEnvironment.getApplication()).disableAuthentication() }
    }
    @After fun cleanup() {
        kotlinx.coroutines.runBlocking { PreferenceManager(RuntimeEnvironment.getApplication()).disableAuthentication() }
        stopKoin()
    }
    @Test fun `master is off by default and expands options without enabling an incomplete lock`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        val repo = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
            arrayOf(IContactsRepository::class.java)) { _, method, _ ->
            if (method.name == "getChanges") kotlinx.coroutines.flow.emptyFlow<Unit>()
            else if (method.name == "getContacts") emptyList<Any>() else null
        } as IContactsRepository
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        startKoin { modules(module { single { prefs }; single { repo } }) }
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        val visualContext = object : android.content.ContextWrapper(rule.activity) {
            override fun getDisplay(): android.view.Display = rule.activity
                .getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides visualContext, LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { BiometricScreen(navigator) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithText(context.getString(R.string.authentication_method)).assertDoesNotExist()
            capture("off-${if (mode) "dark" else "light"}-${(scale * 100).toInt()}")
            rule.onNodeWithText(context.getString(R.string.authentication_enable_subtitle)).performClick()
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithText(context.getString(R.string.authentication_method)).assertIsDisplayed()
            rule.onNodeWithText(context.getString(R.string.lock_app_on_open)).assertDoesNotExist()
            assertFalse(prefs.isAuthenticationEnabled())
            assertFalse(prefs.isAppLockEnabled())
            capture("expanded-${if (mode) "dark" else "light"}-${(scale * 100).toInt()}")
            rule.onNodeWithText(context.getString(R.string.authentication_enable_subtitle)).performClick()
            rule.mainClock.advanceTimeBy(700)
            rule.waitUntil(5000) { rule.onAllNodesWithText(context.getString(R.string.authentication_method)).fetchSemanticsNodes().isEmpty() }
        }
    }
    @Test fun `configured lock requires the actual PIN before showing editable controls`() {
        val context = RuntimeEnvironment.getApplication()
        val key = javax.crypto.spec.SecretKeySpec(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, "AES")
        val prefs = PreferenceManager(context, dev.goodwy.rphone.controller.lock.AuthenticationSecrets(context) { key })
        kotlinx.coroutines.runBlocking {
            prefs.disableAuthentication()
            prefs.configureAuthentication("pin", "6482")
        }
        val repo = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
            arrayOf(IContactsRepository::class.java)) { _, method, _ ->
            if (method.name == "getChanges") kotlinx.coroutines.flow.emptyFlow<Unit>()
            else if (method.name == "getContacts") emptyList<Any>() else null
        } as IContactsRepository
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        startKoin { modules(module { single { prefs }; single { repo } }) }
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        val visualContext = object : android.content.ContextWrapper(rule.activity) {
            override fun getDisplay(): android.view.Display = rule.activity
                .getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides visualContext, LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { BiometricScreen(navigator) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText(context.getString(R.string.lock_app_on_open)).assertDoesNotExist()
        "1234".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.waitUntil(10000) { rule.onAllNodesWithText(context.getString(R.string.incorrect_pin)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(context.getString(R.string.lock_app_on_open)).assertDoesNotExist()
        "6482".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.waitUntil(10000) { rule.onAllNodesWithText(context.getString(R.string.lock_app_on_open)).fetchSemanticsNodes().isNotEmpty() }
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithText(context.getString(R.string.lock_app_on_open)).assertIsDisplayed()
            assertTrue(prefs.isAuthenticationEnabled())
            assertTrue(prefs.isAppLockEnabled())
            capture("configured-${if (mode) "dark" else "light"}-${(scale * 100).toInt()}")
        }
        kotlinx.coroutines.runBlocking { prefs.disableAuthentication() }
    }

    @Test fun `password content accepts only the encrypted credential - platform dialog separate`() {
        val context = RuntimeEnvironment.getApplication()
        val key = javax.crypto.spec.SecretKeySpec(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, "AES")
        val prefs = PreferenceManager(context, dev.goodwy.rphone.controller.lock.AuthenticationSecrets(context) { key })
        kotlinx.coroutines.runBlocking { prefs.configureAuthentication("password", "Test Alexandru Butica password") }
        var accepted = false
        rule.setContent {
            DotDialerTheme {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    PasswordDialogContent(onConfirm = { accepted = true }, onDismiss = {},
                        isVerify = true, verifySecret = { prefs.verifyAuthentication("password", it) })
                }
            }
        }
        rule.onNodeWithText(context.getString(R.string.password)).performTextInput("wrong password")
        rule.onNodeWithText(context.getString(R.string.confirm)).performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText(context.getString(R.string.incorrect_password)).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(accepted)
        rule.onNodeWithText(context.getString(R.string.password)).performTextClearance()
        rule.onNodeWithText(context.getString(R.string.password)).performTextInput("Test Alexandru Butica password")
        rule.onNodeWithText(context.getString(R.string.confirm)).performClick()
        rule.waitUntil(10000) { accepted }
        assertTrue(accepted)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File(System.getProperty("user.dir"), "build/reports/visual/authentication")
            directory.mkdirs()
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
