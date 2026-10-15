package dev.goodwy.rphone.controller.util

import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyResult
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyStage
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetail
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics

internal object ContactPhotoApplyTrace {
    fun record(stage: ContactPhotoApplyStage, result: ContactPhotoApplyResult) {
        AppDiagnostics.recordDetail(DiagnosticDetail.ContactPhotoApply(stage, result))
    }
    fun failed(stage: ContactPhotoApplyStage, result: ContactPhotoApplyResult): Boolean {
        record(stage, result)
        return false
    }
}
