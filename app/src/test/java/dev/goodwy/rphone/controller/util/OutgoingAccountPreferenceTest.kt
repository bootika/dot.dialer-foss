package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.ComponentName
import android.telecom.PhoneAccountHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class OutgoingAccountPreferenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val a = account("A")
    private val b = account("B")
    private val number = "+40700000011"
    private fun account(id: String): CallingAccountInfo {
        val handle = PhoneAccountHandle(ComponentName("example.phone", "Calls"), id)
        return CallingAccountInfo(handle, handle.toPreferenceKey(), "Network $id", null, 0, null, true)
    }
    private fun prefs(values: Map<String, Any>): PreferenceManager = PreferenceManager(context).also {
        runBlocking { it.restoreAllPreferences(values, replacePortable = true) }
    }

    @Test fun `number overrides contact and app without depending on account order`() {
        val p = prefs(mapOf("favorite_sim_number_40700000011" to b.key,
            "favorite_sim_contact" to a.key, PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT to a.key))
        assertEquals(b.handle, preferredOutgoingAccount(context, p, number, "contact", listOf(a, b)))
        assertEquals(b.handle, preferredOutgoingAccount(context, p, number, "contact", listOf(b, a)))
    }

    @Test fun `removed explicit account asks again instead of using global`() {
        val p = prefs(mapOf("favorite_sim_number_40700000011" to "removed",
            PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT to a.key))
        assertNull(preferredOutgoingAccount(context, p, number, null, listOf(a, b)))
    }

    @Test fun `remember scopes persist only their own target and once writes nothing`() {
        val p = prefs(emptyMap())
        rememberOutgoingAccount(p, number, b.key, 0)
        assertNull(p.getFavoriteSimForNumber(number))
        rememberOutgoingAccount(p, number, b.key, 1)
        runBlocking { p.getPersistedPreferences() }
        val afterNumber = PreferenceManager(context)
        assertEquals(b.key, afterNumber.getFavoriteSimForNumber(number))
        assertNull(afterNumber.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null))
        rememberOutgoingAccount(afterNumber, number, a.key, 2)
        runBlocking { afterNumber.getPersistedPreferences() }
        val afterAll = PreferenceManager(context)
        assertEquals(a.key, afterAll.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null))
        assertEquals(b.handle, preferredOutgoingAccount(context, afterAll, number, null, listOf(a, b)))
        assertEquals(a.handle, preferredOutgoingAccount(context, afterAll, "+40700000012", null, listOf(a, b)))
    }
}
