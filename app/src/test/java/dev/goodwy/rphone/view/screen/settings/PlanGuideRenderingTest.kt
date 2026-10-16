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
        runBlocking {PreferenceWriteBarrier.awaitPending()}
        startKoin {modules(module {single {prefs}})}
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
        rule.onNodeWithTag("plan-guide-more").performClick()
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("plan-guide-PREMIUM").assertExists()
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
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.runOnIdle { assertEquals(1, closed) }
    }

}
