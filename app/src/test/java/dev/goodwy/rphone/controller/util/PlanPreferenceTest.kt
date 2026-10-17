package dev.goodwy.rphone.controller.util

import android.app.Application
import io.github.bootika.dotdialer.core.license.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class PlanPreferenceTest {
    @Test fun `effective premium appearance downgrades without destroying stored preferences or core settings`() = runBlocking {
        var allowed=true;var requests=0
        val prefs=PreferenceManager(RuntimeEnvironment.getApplication(),featureAllowed={allowed},onPlanRequired={requests++})
        prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_ACCENT_COLOR to "violet",PreferenceManager.KEY_LIQUID_GLASS to true,PreferenceManager.KEY_THEME_MODE to "dark"))
        assertEquals("violet",prefs.getString(PreferenceManager.KEY_ACCENT_COLOR,null))
        allowed=false
        assertEquals("jade",prefs.getString(PreferenceManager.KEY_ACCENT_COLOR,null))
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS,true))
        assertEquals("dark",prefs.getString(PreferenceManager.KEY_THEME_MODE,null))
        prefs.setString(PreferenceManager.KEY_ACCENT_COLOR,"blue");PreferenceWriteBarrier.awaitPending()
        assertEquals(1,requests)
        allowed=true
        assertEquals("violet",prefs.getString(PreferenceManager.KEY_ACCENT_COLOR,null))
        assertTrue(prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS,false))
    }
    @Test fun `background write rechecks at datastore commit and existing pointers survive downgrade`() = runBlocking {
        var allowed=true
        val prefs=PreferenceManager(RuntimeEnvironment.getApplication(),featureAllowed={allowed})
        val key=prefs.contactBackgroundIdKey("123")
        assertTrue(prefs.updateContactBackgroundEntriesSuspend(mapOf(key to "/private/retained.jpg")))
        allowed=false
        assertFalse(prefs.updateContactBackgroundEntriesSuspend(mapOf(key to "/private/new.jpg"),requireAppearance=true))
        assertEquals("/private/retained.jpg",prefs.getContactBackground("123"))
        allowed=true
        assertEquals("/private/retained.jpg",prefs.getContactBackground("123"))
    }
    @Test fun `Complete message preference survives downgrade and blocked writes then returns on upgrade`() = runBlocking {
        var allowed = true
        val requests = mutableListOf<PlanFeature>()
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication(), featureAllowed = { allowed }, onPlanRequired = { requests += it })
        prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL to 1), replacePortable = true)
        assertEquals(1, prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0))
        allowed = false
        assertEquals(0, prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0))
        assertEquals(0, prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 2))
        prefs.setInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 2)
        PreferenceWriteBarrier.awaitPending()
        assertEquals(listOf(PlanFeature.CALL_MESSAGE_CHANNEL), requests)
        allowed = true
        assertEquals(1, prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0))
    }

}
