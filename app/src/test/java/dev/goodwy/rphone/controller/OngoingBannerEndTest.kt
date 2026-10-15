package dev.goodwy.rphone.controller

import android.app.Application
import android.telecom.Call
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OngoingBannerEndTest {
    @Test fun `only displayed live call is disconnected and stale targets are rejected`() {
        val commands = mutableListOf<String>()
        fun call(id: String, state: Int): Call {
            val call = Shadow.newInstanceOf(Call::class.java)
            val details = Shadow.newInstanceOf(Call.Details::class.java)
            ReflectionHelpers.setField(details, "mState", state)
            ReflectionHelpers.setField(call, "mState", state)
            ReflectionHelpers.setField(call, "mDetails", details)
            ReflectionHelpers.setField(call, "mTelecomCallId", id)
            val binderType = Class.forName("com.android.internal.telecom.IInCallAdapter")
            val binder = Proxy.newProxyInstance(binderType.classLoader, arrayOf(binderType)) { _, method, args ->
                if (method.name == "disconnectCall") commands.add(args!![0] as String)
                null
            }
            val adapterType = Class.forName("android.telecom.InCallAdapter")
            val constructor = adapterType.getDeclaredConstructor(binderType).apply { isAccessible = true }
            ReflectionHelpers.setField(call, "mInCallAdapter", constructor.newInstance(binder))
            return call
        }
        val repository = CallRepositoryImpl()
        val manager = CallStateManager(GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = null
        }))
        val model = CallViewModel(RuntimeEnvironment.getApplication(), repository, manager)
        val active = call("first", Call.STATE_ACTIVE)
        val held = call("second", Call.STATE_HOLDING)
        repository.updateAllCalls(listOf(active, held))
        repository.updateCurrentCallSession(CallSession(active, Call.STATE_ACTIVE))
        assertTrue(model.endBannerCall(active))
        assertEquals(listOf("first"), commands)
        assertFalse(model.endBannerCall(held))
        repository.updateCurrentCallSession(CallSession(held, Call.STATE_HOLDING))
        assertFalse(model.endBannerCall(active))
        assertTrue(model.endBannerCall(held))
        assertEquals(listOf("first", "second"), commands)
        ReflectionHelpers.setField(held.details, "mState", Call.STATE_DISCONNECTED)
        assertFalse(model.endBannerCall(held))
        val ringing = call("third", Call.STATE_RINGING)
        repository.updateAllCalls(listOf(ringing))
        repository.updateCurrentCallSession(CallSession(ringing, Call.STATE_RINGING))
        assertFalse(model.endBannerCall(ringing))
        repository.updateAllCalls(emptyList())
        assertFalse(model.endBannerCall(ringing))
        repository.updateCurrentCallSession(null)
        assertFalse(model.endBannerCall(active))
        assertEquals(listOf("first", "second"), commands)
    }
}
