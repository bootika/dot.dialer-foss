package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Keeps selected document access across process and device restarts when the provider permits it. */
fun retainDocumentReadAccess(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
    true
} catch (error: SecurityException) {
    Log.w("DocumentUriAccess", "The document provider denied persistent read access", error)
    false
} catch (error: RuntimeException) {
    Log.w("DocumentUriAccess", "Persistent document access is unavailable", error)
    false
}
