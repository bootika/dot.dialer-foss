package io.github.bootika.dotdialer.observability

import android.content.Context
import android.util.Log
import dev.goodwy.rphone.BuildConfig
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticFormatter
import io.sentry.Breadcrumb
import io.sentry.Sentry
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.sentry.SentryEvent
import io.sentry.transport.ITransportGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Privacy-constrained Sentry integration for crashes, ANRs and typed lifecycle breadcrumbs. */
object AppTelemetry {
    private const val BREADCRUMB_CATEGORY = "dot_dialer.lifecycle"
    private const val TEST_EVENT_NAME = "dot_dialer_manual_test"
    private const val FLUSH_TIMEOUT_MILLIS = 3_000L

    const val REPORTING_KEY = "diagnostics_reporting_mode"
    @Volatile private var mode = ReportingMode.Automatic
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configurationLock = Mutex()
    private var automaticCacheEpoch = 0L
    private var queueReady = true

    suspend fun setReportingMode(preferences: PreferenceManager, selected: ReportingMode) {
        // Close the automatic transport gate immediately, before durable persistence or SDK shutdown.
        mode = selected
        ProductStatistics.setReportingGate(selected)
        preferences.restoreAllPreferences(mapOf(REPORTING_KEY to selected.value))
        configurationLock.withLock { configure(selected) }
    }

    private val initialized = AtomicBoolean(false)
    private val enabled = AtomicBoolean(false)

    val isConfigured: Boolean
        get() = BuildConfig.SENTRY_DSN.isNotBlank()

    val isEnabled: Boolean
        get() = enabled.get() && Sentry.isEnabled()

    fun initialize(context: Context, preferences: PreferenceManager) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        try {
            automaticCacheEpoch = appContext.getSharedPreferences("telemetry_queue_epoch", Context.MODE_PRIVATE).getLong("epoch", 0)
        } catch (error: RuntimeException) {
            queueReady = false
            Log.e("AppTelemetry", "Unable to read report queue generation; reporting stays disabled", error)
        }
        mode = ReportingMode.parse(preferences.getString(REPORTING_KEY, null))
        if (isConfigured) configure(mode)
        var observed = mode
        scope.launch {
            preferences.settingsChanged.collect {
                val selected = ReportingMode.parse(preferences.getString(REPORTING_KEY, null))
                if (selected != observed) {
                    observed = selected
                    mode = selected
                    configurationLock.withLock { configure(selected) }
                }
            }
        }
    }

    // Synchronous commit is required to keep queued report generations separate after restart.
    @android.annotation.SuppressLint("ApplySharedPref", "UseKtx")
    private fun configure(selected: ReportingMode, manual: Boolean = false) {
        if (selected != mode) return
        enabled.set(false)
        try {
            Sentry.close()
            // Never reopen queued automatic reports after opting out, including after a process restart.
            if (selected == ReportingMode.OnDemand) {
                if (!manual) {
                    val obsolete = java.io.File(appContext.noBackupFilesDir, "telemetry/automatic-$automaticCacheEpoch")
                    automaticCacheEpoch++
                    check(appContext.getSharedPreferences("telemetry_queue_epoch", Context.MODE_PRIVATE)
                        .edit().putLong("epoch", automaticCacheEpoch).commit()) { "Unable to persist report queue generation" }
                    obsolete.deleteRecursively()
                    java.io.File(appContext.noBackupFilesDir, "telemetry/automatic").deleteRecursively()
                    return
                }
            }
            if (!isConfigured || !queueReady) return
            SentryAndroid.init(appContext) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.environment = if (BuildConfig.DEBUG) "test" else "production"
                options.release = buildReleaseName()
                options.dist = BuildConfig.VERSION_CODE.toString()
                options.cacheDirPath = java.io.File(appContext.noBackupFilesDir,
                    "telemetry/" + if (manual) "manual" else "automatic-$automaticCacheEpoch").absolutePath
                options.shutdownTimeoutMillis = 0
                options.isSendClientReports = false
                options.isEnableAutoSessionTracking = false
                options.isEnableUncaughtExceptionHandler = !manual
                options.isEnableNdk = !manual
                options.isReportHistoricalAnrs = !manual
                options.isReportHistoricalMemoryLimiterExits = !manual
                options.isAnrEnabled = !manual
                options.isAnrReportInDebug = !manual
                options.isSendDefaultPii = false
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.isEnableUserInteractionBreadcrumbs = false
                options.isEnableUserInteractionTracing = false
                options.sampleRate = 1.0
                options.tracesSampleRate = 0.0
                options.setTransportGate(ITransportGate { mode == selected })
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                    if (ReportingMode.maySend(mode, selected, event.getTag("submission") == "manual")) {
                        SentryPrivacyFilter.scrub(event)
                    } else null
                }
            }
            enabled.set(Sentry.isEnabled())
            Sentry.setTag("distribution", BuildConfig.FLAVOR)
            Sentry.setTag("build_type", BuildConfig.BUILD_TYPE)
        } catch (error: Exception) {
            queueReady = false
            Log.e("AppTelemetry", "Telemetry initialization failed; reporting stays disabled", error)
        }
    }

    fun addBreadcrumb(event: DiagnosticEvent) {
        if (!isEnabled) return

        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = BREADCRUMB_CATEGORY
                level = SentryLevel.INFO
                message = DiagnosticFormatter.format(event)
            }
        )
    }

    suspend fun captureTestEvent(): String? = withContext(Dispatchers.IO) {
        configurationLock.withLock {
            if (!isConfigured) return@withLock null
            val manual = mode == ReportingMode.OnDemand
            if (manual) configure(mode, manual = true)
            try {
                if (!isEnabled) return@withLock null
                val event = SentryEvent().apply {
                    message = io.sentry.protocol.Message().apply { formatted = TEST_EVENT_NAME }
                    level = SentryLevel.WARNING
                    setTag("submission", "manual")
                }
                val eventId = Sentry.captureEvent(event)
                Sentry.flush(FLUSH_TIMEOUT_MILLIS)
                eventId.takeUnless { it == io.sentry.protocol.SentryId.EMPTY_ID }?.toString()
            } finally {
                if (manual) { enabled.set(false); Sentry.close() }
            }
        }
    }

    private fun buildReleaseName(): String =
        "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
}
