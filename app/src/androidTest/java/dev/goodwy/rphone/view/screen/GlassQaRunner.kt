package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** No normal contact/telemetry/license startup in the isolated test package. */
class GlassQaRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, name: String, context: Context): Application {
        check(context.packageName == "dot.dialer.glassqa.test")
        return super.newApplication(loader, Application::class.java.name, context)
    }
}
