package dev.goodwy.rphone.controller.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY =
    "com.android.externalstorage.documents"
private const val DOWNLOADS_DOCUMENT_ID = "primary:Download"

internal fun downloadsDocumentUri(): Uri = DocumentsContract.buildDocumentUri(
    EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
    DOWNLOADS_DOCUMENT_ID,
)

private fun Intent.startInDownloads(): Intent = apply {
    putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDocumentUri())
}

class CreateBackupDocumentContract : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, input)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.startInDownloads()

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

class OpenBackupDocumentContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.startInDownloads()

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}
