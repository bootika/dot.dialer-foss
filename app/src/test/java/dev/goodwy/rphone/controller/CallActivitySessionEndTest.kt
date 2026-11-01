package dev.goodwy.rphone.controller

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

/** Real Activity lifecycle/Compose effects; external-call state never enters Dot's repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallActivitySessionEndTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val repository = CallRepositoryImpl()
    private var scenario: ActivityScenario<CallActivity>? = null
    private lateinit var host: CallActivity
    private var contactFixture: Contact? = null
    private val activityObserver = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity is CallActivity) host = activity
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    private val telecom get() = context.getSystemService(TelecomManager::class.java)

    @Before fun setup() {
        context.registerActivityLifecycleCallbacks(activityObserver)
        val prefs = PreferenceManager(context)
        prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, false)
        runBlocking { prefs.getPersistedPreferences() }
        val contacts = Proxy.newProxyInstance(
            IContactsRepository::class.java.classLoader, arrayOf(IContactsRepository::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "getChanges" -> kotlinx.coroutines.flow.emptyFlow<Unit>()
                "getContactByNumber", "getContactByNumberInRegion" -> contactFixture
                "toString" -> "Local contact fixture"
                else -> null
            }
        } as IContactsRepository
        val manager = CallStateManager(GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = null
        }))
        val model = CallViewModel(context, repository, manager)
        startKoin { modules(module {
            single { prefs }; single<IContactsRepository> { contacts }
            single { model }; single { manager }
        }) }
        rule.mainClock.autoAdvance = false
    }

    @After fun cleanup() {
        DirectCallWindow.cancel()
        scenario?.close()
        context.unregisterActivityLifecycleCallbacks(activityObserver)
        stopKoin()
    }

    @Test fun lastManagedCallEndsWhileExternalTelecomCallRemains() {
        val call = call(Call.STATE_ACTIVE)
        publish(listOf(call), call)
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        assertFalse(host.isFinishing)
        remove(call)
        advance(600)
        assertTrue("External call must not keep Dot's finished screen alive", host.isFinishing)
        assertTrue("Dismissing the screen must not end the external call", telecom.isInCall)
    }

    @Test fun returningFromBackgroundWithoutManagedCallsClosesImmediately() {
        val call = call(Call.STATE_ACTIVE)
        publish(listOf(call), call)
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        scenario!!.moveToState(Lifecycle.State.CREATED)
        remove(call)
        shadowOf(telecom).setIsInCall(false)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertTrue("Resume must reject the retained ended session", host.isFinishing)
    }

    @Test fun holdingManagedCallSurvivesExternalCallAndResume() {
        val call = call(Call.STATE_HOLDING)
        publish(listOf(call), call)
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        advance(4_000)
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertFalse(host.isFinishing)
        remove(call)
        advance(600)
        assertTrue(host.isFinishing)
    }

    @Test fun endingFirstCallKeepsSecondHoldingCallAvailable() {
        val first = call(Call.STATE_ACTIVE)
        val second = call(Call.STATE_HOLDING)
        publish(listOf(first, second), first)
        shadowOf(telecom).setIsInCall(true)
        launch()
        rule.runOnIdle {
            ReflectionHelpers.setField(first, "mState", Call.STATE_DISCONNECTED)
            publish(listOf(second), second)
        }
        advance(600)
        assertFalse(host.isFinishing)
        remove(second)
        advance(600)
        assertTrue(host.isFinishing)
    }

    @Test fun failedOutgoingLaunchExpiresDespiteExternalTelecomCall() {
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        advance(2_500)
        assertFalse(host.isFinishing)
        advance(700)
        assertTrue("A failed outgoing launch must not wait for an external call to end", host.isFinishing)
    }

    @Test fun deliveredSessionConsumesGraceBeforeActivityRecreation() {
        val call = call(Call.STATE_ACTIVE)
        publish(listOf(call), call)
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        scenario!!.moveToState(Lifecycle.State.CREATED)
        remove(call)
        scenario!!.recreate()
        assertTrue("A consumed outgoing intent must not start another grace on recreation", host.isFinishing)
    }

    @Test fun waitingOutgoingLaunchKeepsOriginalDeadlineAcrossRecreation() {
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(2_000))
        scenario!!.recreate()
        advance(300)
        assertFalse(host.isFinishing)
        advance(900)
        assertTrue("Recreation must preserve the deadline, not grant another three seconds", host.isFinishing)
    }

    @Test fun delayedOutgoingSessionCancelsTimeoutAndConsumesLaunch() {
        shadowOf(telecom).setIsInCall(true)
        launch(outgoing = true)
        advance(2_000)
        assertFalse(host.isFinishing)
        val call = call(Call.STATE_DIALING)
        rule.runOnIdle { publish(listOf(call), call) }
        advance(2_000)
        assertFalse("The old launch timer must not close a delivered session", host.isFinishing)
        remove(call)
        advance(600)
        assertTrue(host.isFinishing)
    }

    @Test fun externalCallAloneCannotOpenAnUnrequestedDotScreen() {
        shadowOf(telecom).setIsInCall(true)
        scenario = ActivityScenario.launch(Intent(context, CallActivity::class.java))
        assertTrue(host.isFinishing)
    }

    @Test fun managedSessionSurvivesEvenIfGlobalTelecomStateLags() {
        val call = call(Call.STATE_HOLDING)
        publish(listOf(call), call)
        shadowOf(telecom).setIsInCall(false)
        launch()
        advance(600)
        assertFalse(host.isFinishing)
    }

    @Test fun directTapCanBeCancelledBeforeTelecomSubmission() {
        var submissions = 0
        DirectCallWindow.begin("0700000011", null) { submissions++ }
        launch(outgoing = true)
        advance(300)
        rule.onNodeWithContentDescription(context.getString(dev.goodwy.rphone.R.string.end_call)).performClick()
        advance(1_300)
        org.junit.Assert.assertEquals(0, submissions)
        assertTrue(host.isFinishing)
    }

    @Test fun directTapSubmitsOnlyAfterTheVisibleCancellationWindow() {
        var submissions = 0
        DirectCallWindow.begin("0700000011", null) { submissions++ }
        launch(outgoing = true)
        advance(700)
        org.junit.Assert.assertEquals(0, submissions)
        advance(500)
        org.junit.Assert.assertEquals(1, submissions)
        advance(400)
        org.junit.Assert.assertEquals(1, submissions)
    }

    @Test fun backCancelsTheUnsubmittedCallFromTheNormalScreen() {
        var submissions = 0
        DirectCallWindow.begin("0700000011", null) { submissions++ }
        launch(outgoing = true)
        advance(300)
        rule.runOnIdle { host.onBackPressedDispatcher.onBackPressed() }
        advance(1_200)
        org.junit.Assert.assertEquals(0, submissions)
        assertTrue(host.isFinishing)
    }

    @Test fun directTapIsCancelledWhenTheActivityLeavesForeground() {
        var submissions = 0
        DirectCallWindow.begin("0700000011", null) { submissions++ }
        launch(outgoing = true)
        scenario!!.moveToState(Lifecycle.State.CREATED)
        advance(1_300)
        org.junit.Assert.assertEquals(0, submissions)
        org.junit.Assert.assertNull(DirectCallWindow.state.value)
    }

    @Test fun incomingSessionCancelsTheUnsubmittedDirectCall() {
        var submissions = 0
        DirectCallWindow.begin("0700000011", null) { submissions++ }
        launch(outgoing = true)
        val incoming = call(Call.STATE_RINGING)
        rule.runOnIdle { publish(listOf(incoming), incoming) }
        advance(1_300)
        org.junit.Assert.assertEquals(0, submissions)
        assertFalse(host.isFinishing)
    }

    @Test fun deferredCallUsesOneNormalSurfaceLightCircle() = assertSingleSurface("light", "circular_avatar")
    @Test fun deferredCallUsesOneNormalSurfaceDarkCircle() = assertSingleSurface("dark", "circular_avatar")
    @Test fun deferredCallUsesOneNormalSurfaceLightFull() = assertSingleSurface("light", "full_photo")
    @Test fun deferredCallUsesOneNormalSurfaceDarkFull() = assertSingleSurface("dark", "full_photo")

    @Test fun singleActiveSimCanBeKeptVisibleOnTheActualCallScreenAndToggledLive() {
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val subscriptions = shadowOf(context.getSystemService(android.telephony.SubscriptionManager::class.java))
        subscriptions.setActiveSubscriptionInfoList(listOf(
            org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
                .setId(1).setSimSlotIndex(0).buildSubscriptionInfo()))
        val handle = android.telecom.PhoneAccountHandle(android.content.ComponentName("example.phone", "Calls"), "A")
        telecom.registerPhoneAccount(android.telecom.PhoneAccount.builder(handle, "Network A")
            .setCapabilities(android.telecom.PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION).build())
        val call = call(Call.STATE_ACTIVE)
        ReflectionHelpers.setField(call.details, "mAccountHandle", handle)
        publish(listOf(call), call)
        launch(outgoing = true)
        rule.onNodeWithText("Network A").assertDoesNotExist()
        fun capture(stage: String) = rule.runOnIdle {
            val view = host.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val folder = java.io.File("build/reports/visual/call-sim-screen").apply { mkdirs() }
            java.io.File(folder, "$stage.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        capture("single-default-off")
        val prefs = org.koin.java.KoinJavaComponent.get<PreferenceManager>(PreferenceManager::class.java)
        prefs.setBoolean(dev.goodwy.rphone.controller.util.CallSimIdentityPreference.KEY, true)
        runBlocking { prefs.getPersistedPreferences() }
        advance(1_000)
        rule.onNodeWithText("Network A").assertExists()
        capture("single-on")
        prefs.setBoolean(dev.goodwy.rphone.controller.util.CallSimIdentityPreference.KEY, false)
        runBlocking { prefs.getPersistedPreferences() }
        advance(1_000)
        rule.onNodeWithText("Network A").assertDoesNotExist()
    }

    private fun assertSingleSurface(theme: String, appearance: String) {
        val prefs = org.koin.java.KoinJavaComponent.get<PreferenceManager>(PreferenceManager::class.java)
        prefs.setString(PreferenceManager.KEY_THEME_MODE, theme)
        prefs.setString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, appearance)
        prefs.setBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
        val photo = java.io.File(context.filesDir, "test-call-photo.png")
        android.graphics.Bitmap.createBitmap(640, 640, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.rgb(70, 130, 90))
            photo.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        contactFixture = Contact(id = "test-contact", givenName = "Test Alexandru", familyName = "Butica",
            company = "Atelier local", photoUri = Uri.fromFile(photo).toString())
        DirectCallWindow.begin("0700000011", null) { }
        val id = checkNotNull(DirectCallWindow.state.value).id
        launch(outgoing = true)
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithText("Atelier local").fetchSemanticsNodes().isNotEmpty()
        }
        val tags = listOf("callActionControls", "callHangupSurface", "callPhotoZoneStart")
        fun bounds() = tags.map { rule.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        val before = bounds()
        fun capture(stage: String) {
            val folder = java.io.File("evidence/local-verification/single-call-surface-20261030").apply { mkdirs() }
            rule.runOnIdle {
                val view = host.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                java.io.File(folder, "$theme-$appearance-$stage.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        capture("pending")
        advance(1_200)
        org.junit.Assert.assertEquals(before, bounds())
        val outgoing = call(Call.STATE_DIALING)
        ReflectionHelpers.setField(outgoing.details, "mHandle", Uri.fromParts("tel", "+40700000011", null))
        ReflectionHelpers.setField(outgoing.details, "mIntentExtras", Bundle().apply {
            putString(DirectCallWindow.REQUEST_EXTRA, id)
        })
        rule.runOnIdle { publish(listOf(outgoing), outgoing) }
        advance(48)
        org.junit.Assert.assertEquals("Handoff must retain normal geometry", before, bounds())
        rule.runOnIdle {
            ReflectionHelpers.setField(outgoing, "mState", Call.STATE_ACTIVE)
            ReflectionHelpers.setField(outgoing.details, "mState", Call.STATE_ACTIVE)
            ReflectionHelpers.setField(outgoing.details, "mConnectTimeMillis", System.currentTimeMillis())
            publish(listOf(outgoing), outgoing)
        }
        advance(400)
        rule.onNodeWithTag("callPrimaryTimer", useUnmergedTree = true).assertExists()
        org.junit.Assert.assertEquals("Connection must retain normal geometry", before, bounds())
        capture("active")
    }

    @Test fun moreStaysOpenAcrossDeferredTelecomHandoff() {
        DirectCallWindow.begin("0700000011", null) { }
        val id = checkNotNull(DirectCallWindow.state.value).id
        launch(outgoing = true)
        rule.onNodeWithContentDescription(context.getString(dev.goodwy.rphone.R.string.more)).performClick()
        advance(400)
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.view_contact)).assertExists()
        advance(800)
        val outgoing = call(Call.STATE_DIALING)
        ReflectionHelpers.setField(outgoing.details, "mIntentExtras", Bundle().apply {
            putString(DirectCallWindow.REQUEST_EXTRA, id)
        })
        rule.runOnIdle { publish(listOf(outgoing), outgoing) }
        advance(100)
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.view_contact)).assertExists()
        org.junit.Assert.assertNull(DirectCallWindow.state.value)
    }

    private fun launch(outgoing: Boolean = false) {
        scenario = ActivityScenario.launch(Intent(context, CallActivity::class.java)
            .putExtra(CallActivity.EXTRA_PENDING_OUTGOING_CALL, outgoing))
        scenario!!.onActivity { host = it }
        advance(48)
    }

    private fun publish(calls: List<Call>, foreground: Call?) {
        repository.updateAllCalls(calls)
        repository.updateCurrentCallSession(foreground?.let { CallSession(it, it.currentTelecomState()) })
    }

    private fun remove(call: Call) = rule.runOnIdle {
        ReflectionHelpers.setField(call, "mState", Call.STATE_DISCONNECTED)
        publish(emptyList(), null)
    }

    private fun advance(millis: Long) {
        rule.mainClock.advanceTimeBy(millis)
        rule.waitForIdle()
    }

    private fun call(state: Int): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", "0700000011", null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mCallerDisplayNamePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mExtras", Bundle())
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        ReflectionHelpers.setField(call, "mCallbackRecords", CopyOnWriteArrayList<Any>())
        ReflectionHelpers.setField(call, "mChildren", arrayListOf<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", Collections.emptyList<Call>())
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        ReflectionHelpers.setField(call, "mConferenceableCalls", arrayListOf<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableConferenceableCalls", Collections.emptyList<Call>())
        return call
    }
}
