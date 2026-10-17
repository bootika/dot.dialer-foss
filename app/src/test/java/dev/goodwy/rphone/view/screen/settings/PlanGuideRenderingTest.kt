package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.AppPlan
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class PlanGuideRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var prefs: PreferenceManager
    @Before fun setup() {
        stopKoin()
        prefs=PreferenceManager(RuntimeEnvironment.getApplication())
        runBlocking {prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_LIQUID_GLASS to false,GoogleContactPhotoPreferences.ENABLED to false))}
        prefs.setString(GoogleContactPhotoPreferences.ACCOUNT,null)
        prefs.setInt(PLAN_GUIDE_ACK,0)
        runBlocking {prefs.getPersistedPreferences()}
        startKoin {modules(module {
            single {prefs}
            single<GoogleContactsAuthorization> { object : GoogleContactsAuthorization {
                override val isAvailable = true
                override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult = error("Guide must not authorize")
                override fun completeAuthorization(data: android.content.Intent?, requestedAccountName: String?): GoogleContactsAuthorizationResult = error("Guide must not authorize")
                override suspend fun revoke(accountName: String): Boolean = error("Guide must not revoke")
            } }
        })}
    }
    @After fun cleanup() {stopKoin()}
    @Test fun `both guides readable and actions reachable light dark and large font`() {
        var tier by mutableStateOf(AppPlan.PLUS);var dark by mutableStateOf(false);var scale by mutableFloatStateOf(1f)
        var more=0;var next=0
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {PlanGuideContent(tier,{next++},{more++})}
        }}
        for(value in listOf(AppPlan.PLUS,AppPlan.PREMIUM)) for(mode in listOf(false,true)) for(font in listOf(1f,1.5f,2f)) {
            rule.runOnIdle {tier=value;dark=mode;scale=font}
            rule.onNodeWithTag("plan-guide-${value.name}").assertExists()
            rule.onNodeWithText(value.displayName).assertExists()
            rule.onNodeWithTag("plan-guide-more").assertIsDisplayed().performClick()
            rule.onNodeWithTag("plan-guide-next").assertIsDisplayed().performClick()
            rule.runOnIdle {
                val view=rule.activity.window.decorView
                val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                val directory=File(System.getProperty("user.dir"),"build/reports/visual/plan-guides").apply {mkdirs()}
                File(directory,"${value.name}-${if(mode) "dark" else "light"}-${(font*100).toInt()}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
                image.recycle()
            }
        }
        assertEquals(12,more);assertEquals(12,next)
    }
    @Test fun `premium guide edits same persisted appearance settings without changing consent`() {
        rule.setContent {DotDialerTheme(systemDark=false) {PlanGuideContent(AppPlan.PREMIUM,{},{})}}
        val prior=ContactOptimizationProfiles(rule.activity).settings(null)
        rule.onNodeWithText(rule.activity.getString(R.string.liquid_glass_elements)).performClick()
        runBlocking {PreferenceWriteBarrier.awaitPending()}
        rule.waitUntil(5000) {prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS,false)}
        assertTrue(prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS,false))
        assertEquals(prior,ContactOptimizationProfiles(rule.activity).settings(null))
        assertFalse(prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED,false))
    }
    @Test fun `premium replay orders pages supports back and never changes automatic acknowledgement`() {
        prefs.setInt(PLAN_GUIDE_ACK, AppPlan.PREMIUM.ordinal)
        runBlocking { PreferenceWriteBarrier.awaitPending() }
        var closed = 0
        val editors = mutableListOf<AppPlan>()
        rule.setContent { DotDialerTheme(systemDark=false) {
            PlanGuideReplayContent(AppPlan.PREMIUM, { closed++ }, { editors.add(it) })
        } }
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.next)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertDoesNotExist()
        rule.onNodeWithTag("plan-guide-more").performClick()
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("plan-guide-PREMIUM").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-back").performClick()
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("plan-guide-more").performClick()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle {
            assertEquals(1, closed)
            assertEquals(listOf(AppPlan.PLUS, AppPlan.PREMIUM), editors)
            assertEquals(AppPlan.PREMIUM.ordinal, prefs.getInt(PLAN_GUIDE_ACK, 0))
        }
    }
    @Test fun `plus replay ends after optimizer without exposing premium`() {
        var closed = 0
        rule.setContent { DotDialerTheme(systemDark=false) {
            PlanGuideReplayContent(AppPlan.PLUS, { closed++ }, {})
        } }
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.onNodeWithTag("plan-guide-PREMIUM").assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun `sequence arrows and finish follow eligible pages at large font without acknowledging on next`() {
        var pages by mutableStateOf(listOf(AppPlan.PLUS, AppPlan.PREMIUM))
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var completed = 0
        var backAtStart = 0
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {
                PlanGuideSequenceContent(pages, {completed++}, {}, {backAtStart++})
            }
        } }
        for (mode in listOf(false,true)) for (font in listOf(1f,1.5f,2f)) {
            rule.runOnIdle { dark=mode; scale=font }
            rule.onNodeWithTag("plan-guide-PLUS").assertExists()
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.next)).assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-back").assertIsDisplayed()
            captureNavigation("arrows-${mode}-${font}")
            rule.onNodeWithTag("plan-guide-next").performClick()
            rule.runOnIdle {assertEquals(0,completed); assertEquals(0,prefs.getInt(PLAN_GUIDE_ACK,0))}
            rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed()
            captureNavigation("finish-${mode}-${font}")
            rule.onNodeWithTag("plan-guide-back").performClick()
        }
        rule.onNodeWithTag("plan-guide-back").performClick()
        rule.runOnIdle {assertEquals(1,backAtStart); pages=listOf(AppPlan.PREMIUM)}
        rule.onNodeWithTag("plan-guide-PREMIUM").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed().performClick()
        rule.runOnIdle {assertEquals(1,completed);pages=listOf(AppPlan.PLUS)}
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed()
    }
    @Test
    @Config(qualifiers="w800dp-h360dp-land-mdpi")
    fun `landscape large font keeps both arrows and final action reachable`() {
        var completed=0
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,2f)) {
            DotDialerTheme(systemDark=false) {PlanGuideSequenceContent(listOf(AppPlan.PLUS,AppPlan.PREMIUM), {completed++}, {}, {})}
        }}
        rule.onNodeWithTag("plan-guide-back").assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-more").assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-next").assertIsDisplayed().performClick()
        rule.onNodeWithTag("plan-guide-back").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed()
        captureNavigation("finish-landscape-200")
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.runOnIdle {assertEquals(1,completed)}
    }

    @Test fun `activation welcome is readable across plans themes fonts and has no settings side effects`() {
        var plan by mutableStateOf(AppPlan.PLUS)
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var next=0;var back=0
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {PlanActivationWelcomeContent(plan,{next++},{back++})}
        }}
        for(tier in listOf(AppPlan.PLUS,AppPlan.PREMIUM)) for(mode in listOf(false,true)) for(font in listOf(1f,2f)) {
            rule.runOnIdle {plan=tier;dark=mode;scale=font}
            rule.onNodeWithText(rule.activity.getString(R.string.plan_activation_active, if(tier==AppPlan.PLUS) "Power Up" else "Complete")).assertExists()
            rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-back").assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-more").assertDoesNotExist()
            captureNavigation("welcome-${tier.name}-${mode}-${font}")
        }
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("plan-guide-back").performClick()
        rule.runOnIdle {assertEquals(1,next);assertEquals(1,back);assertEquals(0,prefs.getInt(PLAN_GUIDE_ACK,0))}
    }
    @Test
    @Config(qualifiers="w800dp-h360dp-land-mdpi")
    fun `activation welcome scrolls at landscape large font with fixed navigation`() {
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,2f)) {
            DotDialerTheme(systemDark=false) {PlanActivationWelcomeContent(AppPlan.PREMIUM,{},{})}
        }}
        rule.onNodeWithText(rule.activity.getString(R.string.plan_activation_setup)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
        rule.onNodeWithTag("plan-guide-back").assertIsDisplayed()
        captureNavigation("welcome-landscape-200")
    }

    @Test fun `Complete guide exposes shared call message choices and preserves them across guide pages`() {
        var tier by mutableStateOf(AppPlan.PLUS)
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {PlanGuideContent(tier,{},{})}
        }}
        val title=rule.activity.getString(R.string.call_message_channel)
        rule.onNodeWithText(title).assertDoesNotExist()
        assertEquals(0,prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0))
        rule.runOnIdle {tier=AppPlan.PREMIUM}
        rule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        assertEquals(0,prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0))
        for(mode in listOf(false,true)) for(font in listOf(1f,2f)) {
            rule.runOnIdle {dark=mode;scale=font}
            rule.onNodeWithText(rule.activity.getString(R.string.call_message_channel_supporting)).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("WhatsApp",useUnmergedTree=true).performScrollTo().performClick()
            runBlocking {PreferenceWriteBarrier.awaitPending()}
            rule.waitUntil(5000) {prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0)==1}
            rule.onNodeWithText(rule.activity.getString(R.string.call_message_ask_each_time),useUnmergedTree=true).performScrollTo().performClick()
            runBlocking {PreferenceWriteBarrier.awaitPending()}
            rule.waitUntil(5000) {prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0)==2}
            captureNavigation("complete-messages-${mode}-${font}")
            rule.onNodeWithTag("plan-guide-more").assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
        }
        rule.runOnIdle {tier=AppPlan.PLUS}
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.runOnIdle {tier=AppPlan.PREMIUM}
        rule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        assertEquals(2,prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0))
        assertEquals(2,PreferenceManager(rule.activity).getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0))
    }
    @Test fun `Complete guide retains the shared plan guard for call messages`() {
        val requested=mutableListOf<io.github.bootika.dotdialer.core.license.PlanFeature>()
        val locked=PreferenceManager(rule.activity, featureAllowed={false}, onPlanRequired={requested+=it})
        stopKoin();startKoin {modules(module {single {locked}})}
        rule.setContent {DotDialerTheme(systemDark=false) {PlanGuideContent(AppPlan.PREMIUM,{},{})}}
        rule.onNodeWithText(rule.activity.getString(R.string.call_message_channel)).performScrollTo().performClick()
        rule.runOnIdle {assertEquals(listOf(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_MESSAGE_CHANNEL),requested)}
        assertEquals(0,locked.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL,0))
        rule.onNodeWithText("WhatsApp",useUnmergedTree=true).assertDoesNotExist()
    }
    private fun captureNavigation(name: String) {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val directory=File("build/reports/visual/plan-guide-navigation").apply {mkdirs()}
            File(directory,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
    @Test fun `source configuration requires consent actual access and Google enablement`() {
        val ready = ContactOptimizationSettings(disclosed=true,googleAuthorized=true,gravatarDisclosed=true,businessDisclosed=true)
        fun states(settings: ContactOptimizationSettings=ready, account: Boolean=true, contacts: Boolean=true,
            notification: Boolean=true, supported: Boolean=true, googleAvailable: Boolean=true, googleEnabled: Boolean=true) =
            guideSourceStates(settings, account, contacts, supported, notification, googleAvailable, googleEnabled)
        assertEquals(List(4) { GuideSourceState.Configured }, states())
        assertEquals(List(4) { GuideSourceState.Setup }, states(account=false))
        assertEquals(List(4) { GuideSourceState.Setup }, states(contacts=false))
        assertEquals(GuideSourceState.Setup, states(notification=false)[0])
        assertEquals(GuideSourceState.Unavailable, states(supported=false)[0])
        assertEquals(GuideSourceState.Unavailable, states(googleAvailable=false)[1])
        assertEquals(GuideSourceState.Setup, states(googleEnabled=false)[1])
        assertEquals(GuideSourceState.Setup, states(ready.copy(googleAuthorized=false))[1])
        assertEquals(List(4) { GuideSourceState.Setup }, states(ready.copy(disclosed=false,googleAuthorized=false,gravatarDisclosed=false,businessDisclosed=false)))
        assertEquals(List(4) { GuideSourceState.Off }, states(ready.copy(enabled=false,google=false,gravatar=false,business=false)))
    }
    @Test fun `account source editor normalization and resume preserve other profile and grants`() {
        val account = "test.alexandru.butica@example.test"
        val other = "second@example.test"
        val profiles = ContactOptimizationProfiles(rule.activity)
        profiles.update(account) { ContactOptimizationSettings(normalize=true,googleAuthorized=true,gravatarDisclosed=true,business=false) }
        profiles.update(other) { ContactOptimizationSettings() }
        val otherBefore = profiles.settings(other)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS)
        prefs.setString(GoogleContactPhotoPreferences.ACCOUNT,account)
        runBlocking { PreferenceWriteBarrier.awaitPending() }
        var opened = 0
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) { PlanGuideContent(AppPlan.PLUS,{}, {opened++}) }
        } }
        rule.onNodeWithTag("plan-guide-account").assertTextEquals(account)
        rule.onNodeWithTag("plan-source-google",useUnmergedTree=true).assertTextEquals(rule.activity.getString(R.string.plan_source_setup))
        rule.onNodeWithTag("plan-source-gravatar",useUnmergedTree=true).assertTextEquals(rule.activity.getString(R.string.plan_source_configured))
        rule.onNodeWithTag("plan-guide-sources").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,opened) }
        assertFalse(prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED,false))
        rule.onNodeWithText(rule.activity.getString(R.string.standardize_phone_numbers)).performScrollTo().performClick()
        rule.runOnIdle { assertFalse(profiles.settings(account).normalize); assertEquals(otherBefore,profiles.settings(other)) }
        for (theme in listOf(false,true)) for (font in listOf(1f,2f)) {
            rule.runOnIdle { dark=theme; scale=font }
            rule.onNodeWithTag("plan-guide-sources").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-more").assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
            rule.runOnIdle {
                val view=rule.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val dir=File("build/reports/visual/plan-guides").apply {mkdirs()}
                File(dir,"PLUS-account-${if(theme) "dark" else "light"}-${(font*100).toInt()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
        profiles.update(account) {it.copy(gravatar=false)}
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.onNodeWithTag("plan-source-gravatar",useUnmergedTree=true).assertTextEquals(rule.activity.getString(R.string.off))
        prefs.setString(GoogleContactPhotoPreferences.ACCOUNT,other)
        runBlocking { PreferenceWriteBarrier.awaitPending() }
        rule.waitUntil(5000) {rule.onAllNodesWithText(other).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(otherBefore,profiles.settings(other))
    }

}
