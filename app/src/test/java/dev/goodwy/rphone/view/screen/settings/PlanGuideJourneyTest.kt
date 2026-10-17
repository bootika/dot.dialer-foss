package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.*
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.PreferenceWriteBarrier
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.AppPlan
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class PlanGuideJourneyTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private lateinit var prefs: PreferenceManager
    @Before fun setup() {
        stopKoin()
        prefs=PreferenceManager(RuntimeEnvironment.getApplication())
        prefs.setInt(PLAN_GUIDE_ACK,0)
        runBlocking {prefs.getPersistedPreferences()}
        startKoin {modules(module {single {prefs}})}
    }
    @After fun cleanup() {worker.cancel();stopKoin()}
    private fun signedManager(tier: AppPlan): PlanAccessManager {
        val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val id="6c43ee4d-1111-4111-8111-010203040506"
        val audience=rule.activity.packageName
        val manager=PlanAccessManager(audience,{id},{null},{},PlanGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded)),worker,{request ->
            val body=JSONObject().put("v",1).put("aud",audience).put("installation",id).put("license","b".repeat(32)).put("entitlement","plan_access")
                .put("tier",tier.name.lowercase(java.util.Locale.ROOT)).put("trial_started",0).put("trial_ends",0)
                .put("iat",1000).put("exp",865000).put("nonce",request.getString("nonce"))
            val encoder=Base64.getUrlEncoder().withoutPadding()
            val payload=encoder.encodeToString(body.toString().encodeToByteArray())
            val signature=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(payload.encodeToByteArray())}.sign()
            JSONObject().put("grant",payload+"."+encoder.encodeToString(signature))
        },{1000},{1000})
        runBlocking {withTimeout(5000) {while(manager.state.value.revision<2 || manager.state.value.busy)delay(10)}}
        return manager
    }
    @Test fun `onboarding free plus premium final page and back preserve acknowledgement until completion`() {
        var manager by mutableStateOf(signedManager(AppPlan.FREE))
        var completed=0
        rule.setContent {key(manager) {CompositionLocalProvider(LocalPlanAccessManager provides manager) {
            DotDialerTheme(systemDark=false) {MorphingOnboardingScreen {completed++}}
        }}}
        rule.onNodeWithTag("plan-guide-back").assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed().performClick()
        for (tier in listOf(AppPlan.PLUS,AppPlan.PREMIUM)) {
            prefs.setInt(PLAN_GUIDE_ACK,0)
            runBlocking {prefs.getPersistedPreferences()}
            val next=signedManager(tier)
            rule.runOnIdle {manager=next}
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.next)).performClick()
            rule.onNodeWithTag("plan-activation-welcome").assertExists()
            rule.onNodeWithTag("plan-guide-back").performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.onboarding_essential_title)).assertExists()
            rule.onNodeWithTag("plan-guide-next").performClick()
            rule.onNodeWithTag("plan-guide-next").performClick()
            rule.onNodeWithTag("plan-guide-PLUS").assertExists()
            rule.onNodeWithTag("plan-guide-back").performClick()
            rule.onNodeWithTag("plan-activation-welcome").assertExists()
            rule.onNodeWithTag("plan-guide-back").performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.onboarding_essential_title)).assertExists()
            rule.onNodeWithTag("plan-guide-next").performClick()
            rule.onNodeWithTag("plan-guide-next").performClick()
            if(tier==AppPlan.PREMIUM) {
                rule.onNodeWithTag("plan-guide-next").performClick()
                rule.onNodeWithTag("plan-guide-PREMIUM").assertExists()
                rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()}
                rule.onNodeWithTag("plan-guide-PLUS").assertExists()
                rule.onNodeWithTag("plan-guide-next").performClick()
            }
            rule.runOnIdle {assertEquals(0,prefs.getInt(PLAN_GUIDE_ACK,0))}
            rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).assertIsDisplayed().performClick()
            runBlocking {prefs.getPersistedPreferences()}
            assertEquals(tier.ordinal,prefs.getInt(PLAN_GUIDE_ACK,0))
        }
        assertEquals(3,completed)
    }
    @Test fun `back at start dismisses automatic guide without advancing to unseen premium`() {
        val manager=signedManager(AppPlan.PREMIUM)
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager) {
            DotDialerTheme(systemDark=false) {PlanGuideHost {}}
        }}
        rule.onNodeWithTag("plan-activation-welcome").assertExists()
        rule.onNodeWithTag("plan-guide-back").performClick()
        runBlocking {prefs.getPersistedPreferences()}
        rule.onNodeWithTag("plan-guide-PLUS").assertDoesNotExist()
        rule.onNodeWithTag("plan-guide-PREMIUM").assertDoesNotExist()
        assertEquals(AppPlan.FREE.ordinal,prefs.getInt(PLAN_GUIDE_ACK,0))
    }
    @Test fun `automatic upgrade guide can go back and only completion acknowledges premium`() {
        val manager=signedManager(AppPlan.PREMIUM)
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager) {
            DotDialerTheme(systemDark=false) {PlanGuideHost {}}
        }}
        rule.onNodeWithTag("plan-activation-welcome").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_activation_active, "Complete")).assertExists()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithTag("plan-guide-PREMIUM").assertExists()
        rule.onNodeWithTag("plan-guide-back").performClick()
        rule.onNodeWithTag("plan-guide-PLUS").assertExists()
        rule.runOnIdle {assertEquals(0,prefs.getInt(PLAN_GUIDE_ACK,0))}
        rule.onNodeWithTag("plan-guide-next").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).performClick()
        runBlocking {prefs.getPersistedPreferences()}
        rule.waitUntil(5000) {rule.onAllNodesWithTag("plan-guide-PREMIUM").fetchSemanticsNodes().isEmpty()}
        assertEquals(AppPlan.PREMIUM.ordinal,prefs.getInt(PLAN_GUIDE_ACK,0))
        rule.onNodeWithTag("plan-activation-welcome").assertDoesNotExist()
    }
    @Test fun `signed Power Up and later Complete show actual plan once and preserve eligible guides`() {
        var manager by mutableStateOf(signedManager(AppPlan.FREE))
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager) {
            DotDialerTheme(systemDark=false) {PlanGuideHost {}}
        }}
        rule.onNodeWithTag("plan-activation-welcome").assertDoesNotExist()
        for (tier in listOf(AppPlan.PLUS, AppPlan.PREMIUM)) {
            val next=signedManager(tier)
            rule.runOnIdle {manager=next}
            rule.onNodeWithTag("plan-activation-welcome").assertExists()
            rule.onNodeWithText(rule.activity.getString(R.string.plan_activation_active, if(tier==AppPlan.PLUS) "Power Up" else "Complete")).assertExists()
            rule.onNodeWithTag("plan-guide-next").performClick()
            rule.onNodeWithTag("plan-guide-${tier.name}").assertExists()
            rule.onNodeWithTag("plan-guide-back").performClick()
            rule.onNodeWithTag("plan-activation-welcome").assertExists()
            rule.onNodeWithTag("plan-guide-next").performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.plan_guide_begin)).performClick()
            runBlocking {prefs.getPersistedPreferences()}
            rule.waitUntil(5000) {rule.onAllNodesWithTag("plan-activation-welcome").fetchSemanticsNodes().isEmpty()}
            assertEquals(tier.ordinal,prefs.getInt(PLAN_GUIDE_ACK,0))
            val refreshed=signedManager(tier)
            rule.runOnIdle {manager=refreshed}
            rule.onNodeWithTag("plan-activation-welcome").assertDoesNotExist()
        }
        val downgraded=signedManager(AppPlan.PLUS)
        rule.runOnIdle {manager=downgraded}
        rule.onNodeWithTag("plan-activation-welcome").assertDoesNotExist()
    }

    @Test fun `dismissed welcome is deferred on refresh but a higher plan can introduce itself`() {
        var manager by mutableStateOf(signedManager(AppPlan.PLUS))
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager) {
            DotDialerTheme(systemDark=false) {PlanGuideHost {}}
        }}
        rule.onNodeWithTag("plan-activation-welcome").assertExists()
        rule.onNodeWithTag("plan-guide-back").performClick()
        rule.onNodeWithTag("plan-activation-welcome").assertDoesNotExist()
        val refreshed=signedManager(AppPlan.PLUS)
        rule.runOnIdle {manager=refreshed}
        rule.onNodeWithTag("plan-activation-welcome").assertDoesNotExist()
        assertEquals(0,prefs.getInt(PLAN_GUIDE_ACK,0))
        val upgrade=signedManager(AppPlan.PREMIUM)
        rule.runOnIdle {manager=upgrade}
        rule.onNodeWithText(rule.activity.getString(R.string.plan_activation_active,"Complete")).assertExists()
    }

}
