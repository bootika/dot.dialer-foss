package io.github.bootika.dotdialer.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import dev.goodwy.rphone.BuildConfig
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticFormatter
import io.github.bootika.dotdialer.observability.AppTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded, private diagnostic journal.
 *
 * Only typed [DiagnosticEvent] values are accepted. There is intentionally no raw-message API,
 * which keeps phone numbers, contact names, notes and exception messages out of support reports.
 */
object AppDiagnostics {
    private const val FILE_NAME = "dot_dialer_diagnostics.log"
    private val MAX_FILE_BYTES = if (BuildConfig.DEBUG) 8 * 1024 * 1024L else 256 * 1024L
    private var bluetoothMonitor: BluetoothDiagnosticMonitor? = null

    private val initialized = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dot-dialer-diagnostics").apply { isDaemon = true }
    }
    private val mutableRevision = MutableStateFlow(0L)

    private lateinit var appContext: Context
    private lateinit var logFile: File
    internal lateinit var detailSettings: DiagnosticDetailSettings

    @Volatile
    private var lastWriteError: String? = null

    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        bluetoothMonitor?.close()
        appContext = context.applicationContext
        logFile = File(appContext.filesDir, FILE_NAME)
        detailSettings = DiagnosticDetailSettings(appContext).also { it.load() }
        bluetoothMonitor = (appContext as? android.app.Application)?.let { application ->
            BluetoothDiagnosticMonitor(application, { allows(io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel.DEBUG) }, ::recordDetail)
        }
        updateBluetoothMonitoring()
    }

    internal fun allows(level: io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel): Boolean =
        initialized.get() && BuildConfig.DEBUG && detailSettings.current().ordinal >= level.ordinal

    internal fun levels(): kotlinx.coroutines.flow.Flow<io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel> =
        if (initialized.get()) detailSettings.level else kotlinx.coroutines.flow.flowOf(io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel.NORMAL)

    internal suspend fun selectLevel(level: io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel) {
        detailSettings.select(level)
        updateBluetoothMonitoring()
        mutableRevision.value += 1L
    }

    private fun updateBluetoothMonitoring() {
        if (allows(io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel.DEBUG)) bluetoothMonitor?.start()
        else bluetoothMonitor?.close()
    }

    internal fun recordDetail(event: io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetail) {
        if (!allows(event.level)) return
        // Extended test probes stay local; do not add Sentry breadcrumbs or product statistics.
        val line = "${Instant.now()} | ${io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetailFormatter.format(event)}"
        executor.execute {
            if (!allows(event.level)) return@execute
            try {
                logFile.appendText("$line\n", Charsets.UTF_8)
                trimIfNeeded()
                lastWriteError = null
                mutableRevision.value += 1L
            } catch (error: Exception) { lastWriteError = error.javaClass.simpleName.take(80) }
        }
    }

    fun record(event: DiagnosticEvent) {
        if (!initialized.get()) return
        val timestamp = Instant.now().toString()
        val safeLine = "$timestamp | ${DiagnosticFormatter.format(event)}"
        AppTelemetry.addBreadcrumb(event)

        executor.execute {
            try {
                logFile.parentFile?.mkdirs()
                logFile.appendText("$safeLine\n", Charsets.UTF_8)
                trimIfNeeded()
                lastWriteError = null
                mutableRevision.value += 1L
            } catch (error: Exception) {
                lastWriteError = error.javaClass.simpleName.take(80)
            }
        }
    }

    suspend fun report(context: Context): String = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        executor.submit(Callable { buildReport() }).get()
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        executor.submit(Callable {
            if (logFile.exists()) logFile.writeText("", Charsets.UTF_8)
            lastWriteError = null
            mutableRevision.value += 1L
        }).get()
    }

    private fun ensureInitialized(context: Context) {
        if (!initialized.get()) initialize(context)
    }

    private fun buildReport(): String {
        val telecomManager = appContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val isDefaultDialer = telecomManager?.defaultDialerPackage == appContext.packageName
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val canUseFullScreenIntent =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager.canUseFullScreenIntent()
        val missingPermissions = relevantPermissions()
            .filterNot { permission ->
                appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
            .map(::shortPermissionName)

        val events = if (logFile.exists()) logFile.readText(Charsets.UTF_8).trim() else ""
        return buildString {
            appendLine("DOT DIALER DIAGNOSTICS")
            appendLine("generated_at=${Instant.now()}")
            appendLine("app_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("flavor=${BuildConfig.FLAVOR}")
            appendLine("debug=${BuildConfig.DEBUG}")
            appendLine("package=${appContext.packageName}")
            appendLine("android_api=${Build.VERSION.SDK_INT}")
            appendLine("device=${safeHeaderValue(Build.MANUFACTURER)} ${safeHeaderValue(Build.MODEL)}")
            appendLine("default_dialer=$isDefaultDialer")
            appendLine("full_screen_intent_allowed=$canUseFullScreenIntent")
            appendLine("missing_permissions=${missingPermissions.ifEmpty { listOf("none") }.joinToString(",")}")
            appendLine("journal_write_error=${lastWriteError ?: "none"}")
            if (BuildConfig.DEBUG) appendLine("log_level=${detailSettings.current()} | extended_scope=local_telecom_photo_audio_bluetooth_state_not_hci | extended_duration=${detailSettings.duration()}")
            appendLine("journal_max_bytes=$MAX_FILE_BYTES")
            appendLine("monotonic_ms=${android.os.SystemClock.elapsedRealtime()}")
            appendLine("bluetooth_packets=not_captured_by_app | system_capture=full_hci_snoop_and_bugreport")
            appendLine("privacy=phone numbers, contact names, notes and exception messages are excluded")
            appendLine()
            appendLine("EVENTS")
            if (events.isBlank()) appendLine("No diagnostic events recorded.") else append(events)
        }
    }

    private fun relevantPermissions(): List<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.WRITE_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun shortPermissionName(permission: String): String = permission.substringAfterLast('.')

    private fun safeHeaderValue(value: String): String = value
        .map { character -> if (character.isISOControl() || character == '=') '_' else character }
        .joinToString(separator = "")
        .trim()
        .take(80)

    private fun trimIfNeeded() {
        trimDiagnosticJournal(logFile, MAX_FILE_BYTES)
    }
}
