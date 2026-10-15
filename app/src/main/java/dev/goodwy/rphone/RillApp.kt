package dev.goodwy.rphone

import android.app.Application
import android.os.Build
import dev.goodwy.rphone.auto.AndroidAutoIntegration
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import io.github.bootika.dotdialer.observability.AppTelemetry
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RillApp : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.goodwy.rphone.controller.license.TestAccessManager.get(this)
        AppDiagnostics.initialize(this)
        AppDiagnostics.record(
            DiagnosticEvent.AppStarted(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                flavor = BuildConfig.FLAVOR,
                apiLevel = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
            )
        )
        val koinApplication = startKoin {
            androidContext(this@RillApp)
            modules(appModule)
        }
        val preferences = koinApplication.koin.get<PreferenceManager>()
        AppTelemetry.initialize(this, preferences)
        dev.goodwy.rphone.controller.util.ContactsOptimizerJob.schedule(this)
        if (AndroidAutoIntegration.isAvailable()) {
            AndroidAutoIntegration.setEnabled(
                this,
                preferences.getBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, false),
            )
        }
    }
}
