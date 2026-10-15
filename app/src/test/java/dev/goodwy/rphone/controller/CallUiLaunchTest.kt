package dev.goodwy.rphone.controller

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CallUiLaunchTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()); CallActivity.isInForeground.value = false; CallUiHandoff.controller.reset() }
    @After fun cleanup() { CallUiHandoff.controller.reset(); CallActivity.isInForeground.value = false; Dispatchers.resetMain() }

    @Test fun outgoingHelperSharesClaimAndPreservesPendingAndTaskFlags() {
        val launched = mutableListOf<Intent>()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication() as Context) {
            override fun startActivity(intent: Intent) { launched += intent }
        }
        CallUiHandoff.controller.beginOutgoing()
        assertTrue(context.launchCallActivitySafely(outgoing = true))
        assertTrue(context.launchCallActivitySafely(outgoing = true))
        assertEquals(1, launched.size)
        assertTrue(launched.single().getBooleanExtra(CallActivity.EXTRA_PENDING_OUTGOING_CALL, false))
        assertEquals(CallActivity::class.java.name, launched.single().component?.className)
        assertTrue(launched.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(launched.single().flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(launched.single().flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun failureClearsPendingAndIncomingOrAlreadyVisibleRemainIndependent() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication() as Context) {
            override fun startActivity(intent: Intent) { throw android.content.ActivityNotFoundException("Unavailable") }
        }
        CallUiHandoff.controller.beginOutgoing()
        assertFalse(context.launchCallActivitySafely(outgoing = true))
        assertNull(CallUiHandoff.controller.state.value)
        CallActivity.isInForeground.value = true
        assertTrue(context.launchCallActivitySafely(outgoing = true))
        assertFalse(context.launchCallActivitySafely(outgoing = false))
    }
}
