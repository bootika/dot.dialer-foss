package io.github.bootika.dotdialer.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticDetailTest {
    @Test fun contactApplyReasonsAreTypedLocalDebugWithoutIdentity() {
        for (stage in ContactPhotoApplyStage.entries) for (result in ContactPhotoApplyResult.entries) {
            val event = DiagnosticDetail.ContactPhotoApply(stage, result)
            assertEquals(DiagnosticLevel.DEBUG, event.level)
            assertEquals("contact_photo_apply | stage=$stage | result=$result", DiagnosticDetailFormatter.format(event))
        }
        assertFalse(DiagnosticDetail.ContactPhotoApply::class.java.declaredFields.any {
            it.type == String::class.java || it.type.isArray
        })
    }
    @Test fun audioReportIncludesSupportedMaskWithoutDeviceIdentity() {
        val event = DiagnosticDetail.AudioState(1, false, true, 9)
        assertTrue(DiagnosticDetailFormatter.format(event).contains("supported_route_mask=9"))
        assertEquals(DiagnosticLevel.DEBUG, event.level)
    }
    @Test fun releaseAlwaysNormalRegardlessOfSavedSelection() {
        for (level in DiagnosticLevel.entries) assertEquals(DiagnosticLevel.NORMAL,
            DiagnosticLevel.effective(false, level, 200, 100))
    }
    @Test fun deadlineAndClockChangesCannotKeepVerboseBeyondWindow() {
        assertEquals(DiagnosticLevel.VERBOSE, DiagnosticLevel.effective(true, DiagnosticLevel.VERBOSE, 200, 100))
        for (now in listOf(200L, 201L, -DiagnosticLevel.WINDOW_MILLIS)) assertEquals(DiagnosticLevel.NORMAL,
            DiagnosticLevel.effective(true, DiagnosticLevel.VERBOSE, 200, now))
    }
    @Test fun corruptSettingUsesNormal() {
        for (value in listOf(null, "debug", "", "Test Alexandru Butica")) assertEquals(DiagnosticLevel.NORMAL, DiagnosticLevel.parse(value))
    }
    @Test fun manualStopModeSurvivesClockChangesButNeverEnablesReleaseLogging() {
        for (now in listOf(Long.MIN_VALUE, 0L, DiagnosticLevel.WINDOW_MILLIS, Long.MAX_VALUE)) {
            assertEquals(DiagnosticLevel.VERBOSE, DiagnosticLevel.effective(true, DiagnosticLevel.VERBOSE,
                DiagnosticLevel.UNTIL_MANUAL_STOP, now))
            assertEquals(DiagnosticLevel.NORMAL, DiagnosticLevel.effective(false, DiagnosticLevel.VERBOSE,
                DiagnosticLevel.UNTIL_MANUAL_STOP, now))
        }
    }
    @Test fun typedDetailContainsNoStringOrImageIdentityFields() {
        for (type in listOf(DiagnosticDetail.PhotoState::class.java, DiagnosticDetail.AudioState::class.java,
            DiagnosticDetail.PhotoProbe::class.java, DiagnosticDetail.ConnectionState::class.java, DiagnosticDetail.CallIdentity::class.java, DiagnosticDetail.BluetoothTransition::class.java)) {
            assertFalse(type.declaredFields.any { it.type == String::class.java || it.type.isArray })
        }
    }
    @Test fun debugAndVerboseHaveDistinctThresholdsAndUnknownIsExplicit() {
        val photo = DiagnosticDetail.PhotoState(1, PhotoDiagnosticStage.METADATA, 1, true, true, false, false)
        assertEquals(DiagnosticLevel.DEBUG, photo.level)
        assertEquals(DiagnosticLevel.VERBOSE, DiagnosticDetail.PhotoProbe(1, PhotoDiagnosticResult.NO_MATCH).level)
        val unknown = DiagnosticDetailFormatter.format(DiagnosticDetail.ConnectionState(false, -1, -1, -1, -1, -1))
        assertTrue(unknown.contains("hfp_state=-1"))
        assertTrue(unknown.contains("pbap_traffic=system_log_required"))
    }
}
