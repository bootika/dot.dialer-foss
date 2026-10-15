package dev.goodwy.rphone.controller.util

import android.telecom.CallAudioState
import android.telecom.InCallService
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 34, 36], application = android.app.Application::class)
class CallAudioRepositoryCompatibilityTest {
    class AudioFixtureService : InCallService()
    @Test fun legacySnapshotAndServiceResetRemainSupportedAcrossAndroidVersions() {
        val repository = CallRepositoryImpl()
        val service = Robolectric.buildService(AudioFixtureService::class.java).create().get()
        repository.bindService(service)
        val legacy = CallAudioState(true, 1, 9)
        repository.updateAudioState(legacy)
        assertSame(legacy, repository.audioState.value)
        repository.unbindService()
        assertNull(repository.audioState.value)
        repository.bindService(service)
        repository.updateAudioState(CallAudioState(false, 8, 9))
        assertEquals(8, repository.audioState.value!!.route)
        assertFalse(repository.audioState.value!!.isMuted)
    }
}
