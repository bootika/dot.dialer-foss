package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29,36], application = Application::class)
class SimRedialCoordinatorTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun account(handle: PhoneAccountHandle) = CallingAccountInfo(handle,handle.id,handle.id,null,0,null,true)

    @Test fun redialWaitsForActualDisconnectionSurvivesNoUiAndDoesNotChangeDefaults() = runTest {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE)
        val commands = mutableListOf<String>()
        val call = QuickCallFixture.call(commands = commands)
        val repository = CallRepositoryImpl().apply { updateAllCalls(listOf(call)) }
        val placed = mutableListOf<PhoneAccountHandle>()
        val coordinator = SimRedialCoordinator(context,repository,this,
            {listOf(account(QuickCallFixture.first),account(QuickCallFixture.second))}, { _,handle -> placed.add(handle) })
        assertTrue(coordinator.request(call,QuickCallFixture.second))
        runCurrent()
        assertEquals(listOf("disconnectCall"),commands)
        assertTrue(placed.isEmpty())
        assertFalse(coordinator.request(call,QuickCallFixture.second))
        QuickCallFixture.state(call,Call.STATE_DISCONNECTING);runCurrent()
        assertTrue(placed.isEmpty())
        QuickCallFixture.state(call,Call.STATE_DISCONNECTED);runCurrent()
        assertEquals(listOf(QuickCallFixture.second),placed)
        assertFalse(coordinator.busy.value)
        QuickCallFixture.state(call,Call.STATE_DISCONNECTED);runCurrent();assertEquals(1,placed.size)
    }

    @Test fun competingIncomingCallCancelsPermanentlyEvenIfItLaterEnds() = runTest {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE)
        val call=QuickCallFixture.call();val repository=CallRepositoryImpl().apply{updateAllCalls(listOf(call))}
        var placed=0
        val coordinator=SimRedialCoordinator(context,repository,this,
            {listOf(account(QuickCallFixture.first),account(QuickCallFixture.second))},{_,_->placed++})
        assertTrue(coordinator.request(call,QuickCallFixture.second));runCurrent()
        val incoming=QuickCallFixture.call(Call.STATE_RINGING,direction=Call.Details.DIRECTION_INCOMING)
        repository.updateAllCalls(listOf(call,incoming));runCurrent()
        QuickCallFixture.state(call,Call.STATE_DISCONNECTED);repository.updateAllCalls(emptyList());runCurrent()
        assertEquals(0,placed);assertFalse(coordinator.busy.value)
    }

    @Test fun timeoutMissingTargetAndPermissionRevocationCannotStartNewCall() = runTest {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE)
        for(mode in listOf("timeout","missing","permission")) {
            Shadows.shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE)
            val call=QuickCallFixture.call();val repository=CallRepositoryImpl().apply{updateAllCalls(listOf(call))}
            var available=true;var placed=0
            val coordinator=SimRedialCoordinator(context,repository,this,
                {listOf(account(QuickCallFixture.first)) + if(available) listOf(account(QuickCallFixture.second)) else emptyList()},
                {_,_->placed++})
            assertTrue(coordinator.request(call,QuickCallFixture.second));runCurrent()
            if(mode=="timeout") advanceTimeBy(10_001) else {
                if(mode=="missing") available=false
                if(mode=="permission") Shadows.shadowOf(context).denyPermissions(Manifest.permission.CALL_PHONE)
                QuickCallFixture.state(call,Call.STATE_DISCONNECTED)
            }
            runCurrent();assertEquals(mode,0,placed);assertFalse(coordinator.busy.value)
        }
    }

    @Test fun rejectsIncomingConferenceEmergencyMmiAnotherCallAndSameAccount() = runTest {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE)
        val calls=listOf(QuickCallFixture.call(Call.STATE_RINGING,direction=Call.Details.DIRECTION_INCOMING),
            QuickCallFixture.call(number="112"),QuickCallFixture.call(number="*123#"))
        calls.forEach { assertFalse(eligibleSimRedial(it,listOf(it))) }
        val call=QuickCallFixture.call()
        assertFalse(eligibleSimRedial(call,listOf(call,QuickCallFixture.call())))
        ReflectionHelpers.setField(call.details,"mCallProperties",Call.Details.PROPERTY_CONFERENCE)
        assertFalse(eligibleSimRedial(call,listOf(call)))
        val ordinary=QuickCallFixture.call();val repository=CallRepositoryImpl().apply{updateAllCalls(listOf(ordinary))}
        val coordinator=SimRedialCoordinator(context,repository,this,
            {listOf(account(QuickCallFixture.first),account(QuickCallFixture.second))},{_,_->fail("Unexpected redial")})
        assertFalse(coordinator.request(ordinary,QuickCallFixture.first))
    }
}
