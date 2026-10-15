package dev.goodwy.rphone.view.screen.settings

import android.accounts.Account
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
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
class ContactSourceProviderRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }

    @Test fun `same address rows show providers separately and switches stay independent`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        startKoin { modules(module { single { prefs } }) }
        val email = "test.alexandru.butica@example.com"
        val accounts = listOf(Account(email, "com.google"), Account(email, "org.example.second"),
            Account(email, "org.example.archive"))
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var enabled by mutableStateOf(accounts.map { "${it.type}|${it.name}" }.toSet())
        val calls = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp)) {
                            SettingsSectionLabel("Contact sources")
                            RillExpressiveCard {
                                for (account in accounts) {
                                    val key = "${account.type}|${account.name}"
                                    ContactSourceVisibilityRow(account,
                                        if (account == accounts[1]) "Profile sharing" else null,
                                        key in enabled) { checked ->
                                        enabled = if (checked) enabled + key else enabled - key
                                        calls += key
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            rule.onNodeWithText("Google").assertIsDisplayed()
            rule.onNodeWithText("Profile sharing").assertIsDisplayed()
            rule.onNodeWithText("org.example.archive").assertIsDisplayed()
            rule.onAllNodesWithText(email).assertCountEquals(3)
            val first = "${accounts[0].type}|$email"
            val second = "${accounts[1].type}|$email"
            rule.onNodeWithText("Profile sharing").performClick()
            rule.runOnIdle { assertTrue(first in enabled); assertFalse(second in enabled); assertEquals(second, calls.last()) }
            rule.onAllNodes(isToggleable())[1].assertIsOff().performClick()
            rule.runOnIdle { assertTrue(first in enabled); assertTrue(second in enabled) }
            capture("${if(mode) "dark" else "light"}-font${(scale*100).toInt()}")
        }
        assertEquals(8, calls.size)
    }

    @Test fun `app named sources render readable provider titles and independent switches`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        startKoin { modules(module { single { prefs } }) }
        val accounts = listOf(Account("Meet", "com.google.android.apps.tachyon"),
            Account("WhatsApp", "com.whatsapp"))
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var enabled by mutableStateOf(accounts.map { "${it.type}|${it.name}" }.toSet())
        val calls = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp)) {
                            SettingsSectionLabel("Contact sources")
                            RillExpressiveCard {
                                for (account in accounts) {
                                    val key = "${account.type}|${account.name}"
                                    // Meet uses metadata matching the account name; WhatsApp exercises known fallback.
                                    ContactSourceVisibilityRow(account,
                                        if (account == accounts[0]) "Meet" else null,
                                        key in enabled) { checked ->
                                        enabled = if (checked) enabled + key else enabled - key
                                        calls += key
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            rule.onAllNodesWithText("Meet", useUnmergedTree = true).assertCountEquals(2)
            rule.onAllNodesWithText("WhatsApp", useUnmergedTree = true).assertCountEquals(2)
            accounts.forEach { rule.onNodeWithText(it.type).assertDoesNotExist() }
            val first = "${accounts[0].type}|${accounts[0].name}"
            val second = "${accounts[1].type}|${accounts[1].name}"
            rule.onAllNodes(isToggleable())[0].performClick().assertIsOff()
            rule.runOnIdle { assertFalse(first in enabled); assertTrue(second in enabled); assertEquals(first, calls.last()) }
            rule.onAllNodes(isToggleable())[0].performClick().assertIsOn()
            rule.runOnIdle { assertTrue(first in enabled); assertTrue(second in enabled) }
            capture("named-providers-${if(mode) "dark" else "light"}-font${(scale*100).toInt()}")
        }
        assertEquals(8, calls.size)
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/account-provider/" + BuildConfig.FLAVOR)
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
