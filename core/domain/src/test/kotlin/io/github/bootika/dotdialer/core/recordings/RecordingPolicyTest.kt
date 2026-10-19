package io.github.bootika.dotdialer.core.recordings

import io.github.bootika.dotdialer.core.license.AppPlan
import org.junit.Assert.*
import org.junit.Test

class RecordingPolicyTest {
    private val ready=DictateCapabilities(true,"valid",true,true,true,268435456,14400,listOf("ro","en"))
    @Test fun independentEntitlementsAndCapabilitiesAreAllRequired() {
        for(plan in AppPlan.entries) for(connected in listOf(false,true)) for(license in listOf("valid","required","unavailable"))
            for(key in listOf(false,true)) for(support in listOf(false,true)) {
                val cap=ready.copy(connected=connected,licenseState=license,apiKeyConfigured=key,transcription=support,translation=support)
                val expected=plan>=AppPlan.PLUS && connected && license=="valid" && key && support
                assertEquals("$plan $cap",expected,RecordingPolicy.processing(plan,cap)==RecordingAccess.READY)
                assertEquals(expected,RecordingPolicy.processing(plan,cap,true)==RecordingAccess.READY)
            }
    }
    @Test fun translationNeedsNegotiatedLanguages() {
        assertEquals(RecordingAccess.UNSUPPORTED,RecordingPolicy.processing(AppPlan.PLUS,ready.copy(targetLanguages=emptyList()),true))
        assertEquals(RecordingAccess.READY,RecordingPolicy.processing(AppPlan.PLUS,ready.copy(targetLanguages=emptyList())))
    }
    @Test fun automaticWorkRequiresCompleteAndExplicitOptIn() {
        for(plan in AppPlan.entries)for(opt in listOf(false,true)) {
            assertEquals(plan==AppPlan.PREMIUM && opt,RecordingPolicy.automatic(plan,opt,ready))
            assertFalse(RecordingPolicy.automatic(plan,opt,ready.copy(apiKeyConfigured=false)))
            assertFalse(RecordingPolicy.automatic(plan,opt,ready.copy(licenseState="unavailable")))
        }
    }
    @Test fun recordingInputCapsAreActualNegotiatedLimits() {
        assertTrue(RecordingPolicy.acceptedInput(ready.maxInputBytes,ready.maxInputSeconds*1000,ready))
        assertFalse(RecordingPolicy.acceptedInput(ready.maxInputBytes+1,1000,ready))
        assertFalse(RecordingPolicy.acceptedInput(1,ready.maxInputSeconds*1000+1,ready))
        assertFalse(RecordingPolicy.acceptedInput(0,1000,ready))
        assertFalse(RecordingPolicy.acceptedInput(1,0,ready))
        assertFalse(RecordingPolicy.acceptedInput(1,1000,ready.copy(maxInputBytes=0)))
        assertFalse(RecordingPolicy.acceptedInput(1,1000,ready.copy(maxInputSeconds=0)))
    }
    @Test fun longAudioNeedsNegotiatedSegmentationCodec() {
        val caps=ready.copy(maxChunkBytes=8388608,maxChunkSeconds=300,segmentedAudioMimeTypes=listOf("audio/mp4a-latm"))
        assertTrue(RecordingPolicy.acceptedAudio(1024,300000,"audio/mpeg",caps))
        assertFalse(RecordingPolicy.acceptedAudio(1024,300001,"audio/mpeg",caps))
        assertFalse(RecordingPolicy.acceptedAudio(8388609,1000,"audio/mpeg",caps))
        assertTrue(RecordingPolicy.acceptedAudio(8388609,600000,"audio/mp4a-latm",caps))
        assertFalse(RecordingPolicy.acceptedAudio(1,1000,"audio/mp4a-latm",caps.copy(maxChunkBytes=0)))
    }
    @Test fun progressNeverInventsAPercentageOrAcceptsImpossibleCounts() {
        assertNull(RecordingPolicy.progress(0,0));assertNull(RecordingPolicy.progress(-1,2));assertNull(RecordingPolicy.progress(3,2))
        assertEquals(0 to 2,RecordingPolicy.progress(0,2));assertEquals(2 to 2,RecordingPolicy.progress(2,2))
    }
}
