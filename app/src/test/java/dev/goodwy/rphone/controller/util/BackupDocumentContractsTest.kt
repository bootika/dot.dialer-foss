package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupDocumentContractsTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `create backup picker starts in local Downloads`() {
        val intent = CreateBackupDocumentContract()
            .createIntent(context, "DotDialer_Backup_20260925.rphone")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertEquals(
            "DotDialer_Backup_20260925.rphone",
            intent.getStringExtra(Intent.EXTRA_TITLE),
        )
        assertEquals(
            downloadsDocumentUri(),
            intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java),
        )
    }

    @Test
    fun `restore backup picker starts in local Downloads`() {
        val intent = OpenBackupDocumentContract().createIntent(context, Unit)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertEquals(
            downloadsDocumentUri(),
            intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java),
        )
    }

    @Test
    fun `cancelled picker does not return a document`() {
        val resultIntent = Intent().setData(Uri.parse("content://example/backup.rphone"))

        assertNull(CreateBackupDocumentContract().parseResult(0, resultIntent))
        assertNull(OpenBackupDocumentContract().parseResult(0, resultIntent))
    }
}
