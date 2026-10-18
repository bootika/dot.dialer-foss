package dev.goodwy.rphone.controller.util

import android.app.Application
import android.telecom.Call
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CallSessionRemindersTest {
    @Test fun reminderIsRelativeSingleReplaceableAndCancelledWithSession() = runTest {
        val context=RuntimeEnvironment.getApplication()
        val call=QuickCallFixture.call();val repository=CallRepositoryImpl().apply{updateAllCalls(listOf(call))}
        var alerts=0
        val reminders=CallSessionReminders(context,repository,PreferenceManager(context),backgroundScope,{testScheduler.currentTime},{alerts++})
        runCurrent();assertTrue(reminders.schedule(call,2));runCurrent()
        advanceTimeBy(60_000);assertTrue(reminders.schedule(call,5));runCurrent()
        advanceTimeBy(120_000);runCurrent();assertEquals(0,alerts)
        advanceTimeBy(180_000);runCurrent();assertEquals(1,alerts);assertTrue(reminders.state.value[call]!!.due)
        advanceTimeBy(600_000);runCurrent();assertEquals(1,alerts)
        reminders.cancel(call);assertTrue(reminders.state.value.isEmpty())
        assertTrue(reminders.schedule(call,2));runCurrent()
        QuickCallFixture.state(call,Call.STATE_DISCONNECTED);repository.updateCurrentCallSession(CallSession(call,Call.STATE_DISCONNECTED));runCurrent()
        advanceTimeBy(120_001);runCurrent();assertEquals(1,alerts);assertTrue(reminders.state.value.isEmpty())
    }

    @Test fun freeDowngradeAndInvalidMinutesNeverAlertAndNeverChangeCallState() = runTest {
        val context=RuntimeEnvironment.getApplication();var allowed=false
        val prefs=PreferenceManager(context,featureAllowed={allowed})
        val call=QuickCallFixture.call();val repository=CallRepositoryImpl().apply{updateAllCalls(listOf(call))}
        var alerts=0
        val reminders=CallSessionReminders(context,repository,prefs,backgroundScope,{testScheduler.currentTime},{alerts++})
        assertFalse(reminders.schedule(call,2));allowed=true
        assertFalse(reminders.schedule(call,0));assertFalse(reminders.schedule(call,60))
        assertTrue(reminders.schedule(call,2));runCurrent();allowed=false
        advanceTimeBy(120_001);runCurrent();assertEquals(0,alerts)
        assertEquals(Call.STATE_ACTIVE,call.currentTelecomState())
        assertTrue(reminders.state.value.isEmpty())
    }
}
