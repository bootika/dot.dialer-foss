package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.*
import android.content.pm.*
import dev.goodwy.rphone.controller.util.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
internal fun installMessageApp(context: Context, pkg: String) {
    val app = ApplicationInfo().apply { packageName = pkg }
    val activity = ActivityInfo().apply {
        name = "$pkg.Main"; packageName = pkg; applicationInfo = app; exported = true; enabled = true
    }
    val info = PackageInfo().apply { packageName = pkg; applicationInfo = app; activities = arrayOf(activity) }
    val pm = shadowOf(context.packageManager)
    pm.installPackage(info)
    pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg),
        ResolveInfo().apply { activityInfo = activity })
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CallMessageRoutingTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `default and corrupt preference remain SMS and choices respect availability`() {
        assertEquals(CallMessageChannel.SMS, CallMessageChannel.fromStored(0))
        assertEquals(CallMessageChannel.SMS, CallMessageChannel.fromStored(-5))
        assertEquals(CallMessageChannel.SMS, CallMessageChannel.fromStored(99))
        assertEquals(CallMessageChannel.entries.toList(), (0..2).map(CallMessageChannel::fromStored))
        assertEquals(listOf(CallMessageChannel.SMS), callMessageChoices(false))
        assertEquals(listOf(CallMessageChannel.SMS, CallMessageChannel.WHATSAPP), callMessageChoices(true))
    }

    @Test fun `missing app never opens browser or declines incoming`() {
        var declined = false
        assertNull(callMessageWhatsAppIntent(context, "+40720123456", "Revin mai târziu"))
        assertFalse(openCallMessageWhatsApp(context, "+40720123456", CallMessageRequest("Revin", true)) { declined = true })
        assertFalse(declined)
    }

    @Test fun `Business is supported and personal WhatsApp has precedence`() {
        installMessageApp(context, "com.whatsapp.w4b")
        assertEquals("com.whatsapp.w4b", callMessageWhatsAppPackage(context))
        installMessageApp(context, "com.whatsapp")
        assertEquals("com.whatsapp", callMessageWhatsAppPackage(context))
    }

    @Test fun `draft survives accents emoji newlines and query delimiters without injection`() {
        installMessageApp(context, "com.whatsapp")
        val body = "Revin mai târziu & + ? = # 🙂\nMulțumesc!"
        val intent = callMessageWhatsAppIntent(context, "+40 720 123 456", body)!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("40720123456", intent.data!!.getQueryParameter("phone"))
        assertEquals(body, intent.data!!.getQueryParameter("text"))
        assertEquals(setOf("phone", "text"), intent.data!!.queryParameterNames)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull(callMessageWhatsAppIntent(context, "", body))
        assertFalse(callMessageWhatsAppIntent(context, "+40720123456", null)!!.data!!.queryParameterNames.contains("text"))
    }

    @Test fun `incoming draft declines once then launches while active-call message never declines`() {
        installMessageApp(context, "com.whatsapp")
        val events = mutableListOf<String>()
        val wrapped = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { events += "launch" }
        }
        assertTrue(openCallMessageWhatsApp(wrapped, "+40720123456", CallMessageRequest("Revin", true)) { events += "decline" })
        assertEquals(listOf("decline", "launch"), events)
        events.clear()
        assertTrue(openCallMessageWhatsApp(wrapped, "+40720123456", CallMessageRequest(null, false)) { events += "decline" })
        assertEquals(listOf("launch"), events)
    }

    @Test fun `package launch failure never sends an SMS or launches an implicit fallback`() {
        installMessageApp(context, "com.whatsapp")
        var attempts = 0
        val wrapped = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { attempts++; throw ActivityNotFoundException() }
        }
        assertFalse(openCallMessageWhatsApp(wrapped, "+40720123456", CallMessageRequest("Revin", false)) {})
        assertEquals(1, attempts)
    }
}
