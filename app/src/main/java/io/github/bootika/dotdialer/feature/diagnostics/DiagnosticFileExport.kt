package io.github.bootika.dotdialer.feature.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal const val DIAGNOSTIC_CLIPBOARD_MAX_BYTES = 512 * 1024
internal fun diagnosticClipboardFits(report: String) = report.length <= DIAGNOSTIC_CLIPBOARD_MAX_BYTES / 2 && report.toByteArray(Charsets.UTF_8).size <= DIAGNOSTIC_CLIPBOARD_MAX_BYTES

/** Explicit share only. One narrow cache directory; no contacts/system files or automatic upload. */
internal fun prepareDiagnosticExportFile(context: Context, report: String, now: Long = System.currentTimeMillis()): File {
    val folder = File(context.cacheDir,"diagnostic_reports").apply { check(isDirectory || mkdirs()) }
    folder.listFiles()?.filter { it.name.matches(Regex("dot-dialer-diagnostics-[a-f0-9-]{36}\\.txt")) &&
        now - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
    val file = File(folder,"dot-dialer-diagnostics-${UUID.randomUUID()}.txt")
    file.writeText(report,Charsets.UTF_8)
    return file
}
internal fun diagnosticFileShareIntent(context: Context, report: String): Intent {
    val file = prepareDiagnosticExportFile(context,report)
    return try {
        diagnosticFileReadIntent(FileProvider.getUriForFile(context,context.packageName + ".provider",file))
    } catch(failure: Exception) { file.delete(); throw failure }
}
internal fun diagnosticFileReadIntent(uri: Uri): Intent {
    require(uri.scheme == "content" && uri.path.orEmpty().startsWith("/diagnostic_reports/"))
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_STREAM,uri)
        clipData = ClipData.newRawUri("Dot Dialer diagnostics",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
