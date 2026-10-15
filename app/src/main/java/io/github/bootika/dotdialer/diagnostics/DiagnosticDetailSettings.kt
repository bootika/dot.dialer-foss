package io.github.bootika.dotdialer.diagnostics

import android.content.Context
import android.util.AtomicFile
import dev.goodwy.rphone.BuildConfig
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Device-local debug configuration: manual stop for new selections, legacy deadlines retained. */
internal class DiagnosticDetailSettings(
    context: Context,
    private val testBuild: Boolean = BuildConfig.DEBUG,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "diagnostic_detail_level"))
    private val mutableLevel = MutableStateFlow(DiagnosticLevel.NORMAL)
    val level = mutableLevel.asStateFlow()
    @Volatile private var expiresAt = 0L
    private val saveLock = kotlinx.coroutines.sync.Mutex()

    fun load() {
        if (!testBuild) return
        val stored = runCatching { file.openRead().bufferedReader().use { it.readText() }.split('\n') }.getOrNull()
        expiresAt = stored?.getOrNull(1)?.toLongOrNull() ?: 0L
        mutableLevel.value = DiagnosticLevel.effective(testBuild, DiagnosticLevel.parse(stored?.firstOrNull()), expiresAt, now())
    }

    fun current(): DiagnosticLevel {
        val effective = DiagnosticLevel.effective(testBuild, mutableLevel.value, expiresAt, now())
        mutableLevel.value = effective
        return effective
    }

    fun duration(): String = if (current() == DiagnosticLevel.NORMAL) "normal" else
        if (expiresAt == DiagnosticLevel.UNTIL_MANUAL_STOP) "until_manual_stop" else "legacy_24h"

    suspend fun select(value: DiagnosticLevel) = withContext(Dispatchers.IO) {
        if (!testBuild) return@withContext
        saveLock.lock()
        try {
        val deadline = if (value == DiagnosticLevel.NORMAL) 0L else DiagnosticLevel.UNTIL_MANUAL_STOP
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write("${value.name}\n$deadline".toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let(file::failWrite)
            throw error
        }
        expiresAt = deadline
        mutableLevel.value = value
        } finally { saveLock.unlock() }
    }
}
