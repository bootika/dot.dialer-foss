package dev.goodwy.rphone.controller.util

import android.content.ComponentName
import android.net.Uri
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

internal object QuickCallFixture {
    val first = PhoneAccountHandle(ComponentName("com.android.server.telecom", "PhoneService"), "personal")
    val second = PhoneAccountHandle(ComponentName("com.android.server.telecom", "PhoneService"), "work")
    fun call(state: Int = Call.STATE_ACTIVE, number: String = "+40700000101", direction: Int = Call.Details.DIRECTION_OUTGOING,
        commands: MutableList<String> = mutableListOf()): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 31) ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", number, null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mAccountHandle", first)
        ReflectionHelpers.setField(details, "mCallDirection", direction)
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        ReflectionHelpers.setField(call, "mCallbackRecords", CopyOnWriteArrayList<Any>())
        ReflectionHelpers.setField(call, "mChildrenIds", arrayListOf<String>())
        ReflectionHelpers.setField(call, "mChildren", arrayListOf<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", emptyList<Call>())
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        ReflectionHelpers.setField(call, "mTelecomCallId", "session")
        val binderType = Class.forName("com.android.internal.telecom.IInCallAdapter")
        val binder = Proxy.newProxyInstance(binderType.classLoader, arrayOf(binderType)) { _, method, _ ->
            commands.add(method.name); null
        }
        val type = Class.forName("android.telecom.InCallAdapter")
        val constructor = type.getDeclaredConstructor(binderType).apply { isAccessible = true }
        ReflectionHelpers.setField(call, "mInCallAdapter", constructor.newInstance(binder))
        return call
    }
    fun state(call: Call, state: Int) {
        ReflectionHelpers.setField(call, "mState", state)
        if (android.os.Build.VERSION.SDK_INT >= 31) ReflectionHelpers.setField(call.details, "mState", state)
        val records = ReflectionHelpers.getField<List<Any>>(call, "mCallbackRecords").toList()
        records.forEach { ReflectionHelpers.getField<Call.Callback>(it, "mCallback").onStateChanged(call, state) }
    }
}
