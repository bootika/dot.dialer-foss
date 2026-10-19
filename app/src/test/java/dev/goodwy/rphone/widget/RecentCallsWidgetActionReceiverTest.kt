package dev.goodwy.rphone.widget

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RecentCallsWidgetActionReceiverTest {
    private val receiver = RecentCallsWidgetActionReceiver()

    @Test
    fun `widget action starts a direct call for the supplied number`() {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())

        receiver.onReceive(context, dialIntent("+40700000000"))

        assertEquals(1, context.started.size)
        assertEquals(Intent.ACTION_CALL, context.started.single().action)
        assertEquals("+40700000000", context.started.single().data?.schemeSpecificPart)
    }

    @Test
    fun `widget action falls back to the dialer when a direct call cannot start`() {
        val context = RecordingContext(
            base = ApplicationProvider.getApplicationContext(),
            failFirstStart = true,
        )

        receiver.onReceive(context, dialIntent("*222"))

        assertEquals(listOf(Intent.ACTION_DIAL), context.started.map(Intent::getAction))
        assertEquals("tel:*222", context.started.single().dataString)
    }

    @Test
    fun `widget action ignores an unrelated action and a blank number`() {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())

        receiver.onReceive(context, Intent("unrelated.action"))
        receiver.onReceive(context, dialIntent("  "))

        assertTrue(context.started.isEmpty())
    }

    @Test
    fun `privileged widget action is handled only by a non exported receiver`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        val provider = repoFile(
            "app/src/main/java/dev/goodwy/rphone/widget/RecentCallsWidgetProvider.kt",
        ).readText()

        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest.byteInputStream())
        val android = "http://schemas.android.com/apk/res/android"
        val receivers = document.getElementsByTagName("receiver")
        val actions = (0 until receivers.length).map { receivers.item(it) as org.w3c.dom.Element }
            .filter { it.getAttributeNS(android, "name") == ".widget.RecentCallsWidgetActionReceiver" }
        assertEquals(1, actions.size)
        assertEquals("false", actions.single().getAttributeNS(android, "exported"))
        assertTrue(provider.contains("Intent(context, RecentCallsWidgetActionReceiver::class.java)"))
    }

    private fun dialIntent(number: String) =
        Intent(RecentCallsWidgetActionReceiver.ACTION_DIAL).apply {
            putExtra(RecentCallsWidgetActionReceiver.EXTRA_PHONE_NUMBER, number)
        }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile)
            ?: error("Unable to find repository file: $relativePath")
    }

    private class RecordingContext(
        base: Context,
        private var failFirstStart: Boolean = false,
    ) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            if (failFirstStart) {
                failFirstStart = false
                throw SecurityException("Direct call deliberately rejected")
            }
            started += intent
        }
    }
}
