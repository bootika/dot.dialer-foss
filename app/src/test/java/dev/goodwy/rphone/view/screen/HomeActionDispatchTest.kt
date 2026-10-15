package dev.goodwy.rphone.view.screen

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeActionDispatchTest {
    @Test fun `call and contact invoke only their own callback`() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val calls = mutableListOf<String>()
            var contacts = 0
            performHomeAction(1, activity, NUMBER, { calls += it }, { contacts++ })
            assertEquals(listOf(NUMBER), calls)
            assertEquals(0, contacts)
            performHomeAction(7, activity, NUMBER, { calls += it }, { contacts++ })
            assertEquals(listOf(NUMBER), calls)
            assertEquals(1, contacts)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test fun `SMS opens composer WhatsApp opens its destination and Copy writes exact number`() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val unexpected = { _: String -> error("Unexpected call") }
            val unexpectedContact = { error("Unexpected contact") }
            performHomeAction(2, activity, NUMBER, unexpected, unexpectedContact)
            assertEquals("sms:$NUMBER", shadowOf(activity).nextStartedActivity.dataString)
            performHomeAction(4, activity, NUMBER, unexpected, unexpectedContact)
            assertEquals("https://api.whatsapp.com/send?phone=40700000000", shadowOf(activity).nextStartedActivity.dataString)
            performHomeAction(5, activity, NUMBER, unexpected, unexpectedContact)
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(NUMBER, clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test fun `no action and removed actions have no side effects`() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            listOf(0, 3, 6, -1, 999).forEach { action ->
                performHomeAction(action, activity, NUMBER, { error("Unexpected call") }, { error("Unexpected contact") })
            }
            assertNull(shadowOf(activity).nextStartedActivity)
            assertNull((activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip)
        }
    }

    private companion object { const val NUMBER = "+40700000000" }
}
