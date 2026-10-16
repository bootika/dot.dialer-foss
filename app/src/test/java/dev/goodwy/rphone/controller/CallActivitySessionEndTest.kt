package dev.goodwy.rphone.controller

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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
        ) { _, method, _ ->
            when (method.name) {
                "getChanges" -> kotlinx.coroutines.flow.emptyFlow<Unit>()
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
