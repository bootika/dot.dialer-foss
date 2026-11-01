package io.github.bootika.dotdialer.diagnostics

import dev.goodwy.rphone.BuildConfig
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetail
import io.github.bootika.dotdialer.core.diagnostics.DialNumberStage

/** Test-build, local-only shape trace. Never retain/export the number, URI or its hash. */
internal fun recordDialNumber(stage: DialNumberStage, number: String?, changed: Boolean = false) {
    if (!BuildConfig.TEST_DISTRIBUTION) return
    AppDiagnostics.recordDetail(DiagnosticDetail.DialNumber(stage, diagnosticNumberForm(number, true), changed))
}
