package io.github.bootika.dotdialer.diagnostics

import android.content.Context

/** Release has no projected-car diagnostic dependency or observer. */
internal class ProjectionDiagnosticMonitor(context: Context, onChanged: (Int) -> Unit) {
    fun close() = Unit
}
