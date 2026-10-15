package dev.goodwy.rphone.backup

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Isolated QA process: no contacts, telemetry, license, Koin or real Application startup. */
class BackupQaRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, name: String, context: Context): Application {
        check(context.packageName == "dot.dialer.backupqa.test") { "Isolated QA package required" }
        return super.newApplication(loader, Application::class.java.name, context)
    }
}
