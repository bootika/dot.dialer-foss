package dev.goodwy.rphone.controller.util

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeGesturePreferencesTest {
    @Test fun `persisted legacy tap preference migrates to the same destination after cold read`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        listOf(true to 1, false to 7).forEach { (directCall, expected) ->
            prefs.restoreAllPreferences(mapOf(
                PreferenceManager.KEY_RECENT_TAP_ACTION to -1,
                PreferenceManager.KEY_DIRECT_CALL_ON_TAP to directCall,
            ))
            assertEquals(expected, PreferenceManager(context).getRecentTapAction())
        }
    }

    @Test fun `every selectable tap and swipe action persists including explicit no action`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        HOME_ROW_ACTIONS.forEach { action ->
            prefs.setRecentTapAction(action)
            prefs.setSwipeRightAction(action)
            prefs.setSwipeLeftAction(action)
            withTimeout(5_000) {
                prefs.settingsChanged.first {
                    prefs.getInt(PreferenceManager.KEY_RECENT_TAP_ACTION, -1) == action &&
                        prefs.getInt(PreferenceManager.KEY_SWIPE_RIGHT_ACTION, -1) == action &&
                        prefs.getInt(PreferenceManager.KEY_SWIPE_LEFT_ACTION, -1) == action
                }
            }
            val restored = PreferenceManager(context)
            assertEquals(action, restored.getRecentTapAction())
            assertEquals(action, restored.getSwipeRightAction())
            assertEquals(action, restored.getSwipeLeftAction())
        }
    }

    @Test fun `restored video swipe preferences are no action rather than hidden working video calls`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager(context).restoreAllPreferences(mapOf(
            PreferenceManager.KEY_SWIPE_RIGHT_ACTION to 3,
            PreferenceManager.KEY_SWIPE_LEFT_ACTION to 3,
        ))
        val restored = PreferenceManager(context)
        assertEquals(0, restored.getSwipeRightAction())
        assertEquals(0, restored.getSwipeLeftAction())
    }
}
