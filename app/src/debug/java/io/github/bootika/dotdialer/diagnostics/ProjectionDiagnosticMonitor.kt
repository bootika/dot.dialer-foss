package io.github.bootika.dotdialer.diagnostics

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

internal class ProjectionDiagnosticMonitor(context: Context, onChanged: (Int) -> Unit) {
    private val connection = CarConnection(context)
    private val observer = Observer<Int> { value -> onChanged(value) }
    init { connection.type.observeForever(observer) }
    fun close() { connection.type.removeObserver(observer) }
}
