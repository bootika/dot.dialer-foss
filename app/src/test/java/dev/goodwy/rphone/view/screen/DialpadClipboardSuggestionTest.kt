package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DialpadClipboardSuggestionTest {
    @Test fun `one phone number normalizes separators plus and unicode digits`() {
        assertEquals("+40700000000", clipboardPhoneNumber(" +40 (700) 000-000 "))
        assertEquals("0700000000", clipboardPhoneNumber("0700.000.000"))
        assertEquals("0700000000", clipboardPhoneNumber("٠٧٠٠٠٠٠٠٠٠"))
        assertEquals("+123456789012345", clipboardPhoneNumber("+123456789012345"))
    }

    @Test fun `text links multiple numbers special codes and bad plus are not silently mined`() {
        listOf(null, "", "112", "123456", "1234567890123456", "Tel: 0700000000",
            "https://example.org/0700000000", "tel:0700000000", "*123#", "0700\n000000",
            "0700000000 / 0700000001", "0700+000000", "++40700000000", "0700000000,12")
            .forEach { assertNull("Must not suggest $it", clipboardPhoneNumber(it)) }
    }

    @Test fun `refresh is gated by empty draft focus and resumed lifecycle`() {
        val state = DialpadClipboardSuggestionState()
        var reads = 0
        for (empty in listOf(false, true)) for (focus in listOf(false, true)) for (resumed in listOf(false, true)) {
            val before = reads
            state.refresh(empty, focus, resumed) { reads++; NUMBER }
            assertEquals(if (empty && focus && resumed) before + 1 else before, reads)
            assertEquals(if (empty && focus && resumed) NUMBER else null, state.number)
        }
    }

    @Test fun `dismiss survives same clipboard refocus but a changed number or new session is offered`() {
        val state = DialpadClipboardSuggestionState()
        state.refresh(true, true, true) { NUMBER }
        state.dismiss()
        state.refresh(true, false, true) { error("No background read") }
        state.refresh(true, true, true) { NUMBER }
        assertNull(state.number)
        state.refresh(true, true, true) { "0700000001" }
        assertEquals("0700000001", state.number)
        val reopened = DialpadClipboardSuggestionState()
        reopened.refresh(true, true, true) { NUMBER }
        assertEquals(NUMBER, reopened.number)
    }

    @Test fun `Use consumes only into empty draft and does not touch clipboard`() {
        val state = DialpadClipboardSuggestionState()
        state.refresh(true, true, true) { NUMBER }
        assertNull(state.use("0700000001"))
        assertEquals(NUMBER, state.number)
        assertEquals(NUMBER, state.use(""))
        assertNull(state.use(""))
    }

    @Test fun `Android clipboard accepts plain text and rejects sensitive URI and multi item clips`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("phone number", NUMBER))
        assertEquals(NUMBER, readClipboardPhoneNumber(manager))
        manager.setPrimaryClip(ClipData.newPlainText("phone number", NUMBER).apply {
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        })
        assertNull(readClipboardPhoneNumber(manager))
        manager.setPrimaryClip(ClipData.newRawUri("attachment", Uri.parse("content://contacts/0700000000")))
        assertNull(readClipboardPhoneNumber(manager))
        manager.setPrimaryClip(ClipData.newPlainText("phone number", NUMBER).apply { addItem(ClipData.Item("0700000001")) })
        assertNull(readClipboardPhoneNumber(manager))
        manager.clearPrimaryClip()
        assertNull(readClipboardPhoneNumber(manager))
    }

    private companion object { const val NUMBER = "+40700000000" }
}
