package dev.goodwy.rphone.recordings

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.controller.license.verifiedPlanFixture
import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.recordings.DictateCapabilities
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Transport fixture tests durable consumer transitions; not a real cross-app/provider test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class,shadows=[RecordingFileProviderShadow::class])
class RecordingSyncEngineTest {
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val opened=mutableListOf<RecordingsRepository>()
    private lateinit var context: Application
    @Before fun setup() {context=ApplicationProvider.getApplicationContext();context.deleteDatabase("recordings.db")}
    @After fun cleanup() {opened.forEach {it.catalog.close()};worker.cancel()}
    private fun repo(plan: AppPlan,bridge: FixtureBridge): RecordingsRepository {
        val manager=verifiedPlanFixture(context.packageName,plan,worker)
        return RecordingsRepository(context,{manager},bridge,{true},moduleState=MutableStateFlow(true)).also {opened.add(it)}
    }
    private fun asset(repo: RecordingsRepository): SavedRecording {
        val id=UUID.randomUUID().toString()
        val value=SavedRecording(id,"Test Alexandru Butica",1000,60_000,32,"$id.m4a","call-1","audio/mp4")
        repo.files.audio(value).writeBytes(ByteArray(32));repo.catalog.add(value);return value
    }
    @Test fun acceptedJobsPollAfterRecoveryRatherThanResubmit()=runBlocking {
        val bridge=FixtureBridge();val first=repo(AppPlan.PLUS,bridge);val recording=asset(first)
        val id=first.catalog.enqueue(recording.id,"transcribe")
        RecordingSyncEngine.synchronize(first)
        assertTrue(requireNotNull(first.catalog.job(id)).accepted)
        val recovered=repo(AppPlan.PLUS,bridge)
        RecordingSyncEngine.synchronize(recovered)
        assertEquals(listOf(DictateProtocol.SUBMIT,DictateProtocol.STATUS),bridge.calls)
        assertEquals(1,recovered.catalog.recordings().size)
    }
    @Test fun unavailableAuthorityPausesQueuedWorkWithoutBlockingOrCancellingIt()=runBlocking {
        val bridge=FixtureBridge();val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo)
        val id=repo.catalog.enqueue(asset.id,"transcribe")
        val valid=bridge.state.value
        bridge.state.value=valid.copy(licenseState="unavailable")
        assertTrue(RecordingSyncEngine.synchronize(repo))
        assertEquals("queued",repo.catalog.job(id)?.state);assertTrue(bridge.calls.isEmpty())
        bridge.state.value=valid;RecordingSyncEngine.synchronize(repo)
        assertEquals(listOf(DictateProtocol.SUBMIT),bridge.calls)
    }
    @Test fun missingDictateKeyBlocksWorkWithoutTouchingSavedAssets()=runBlocking {
        val bridge=FixtureBridge();bridge.state.value=bridge.state.value.copy(apiKeyConfigured=false)
        val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo);val id=repo.catalog.enqueue(asset.id,"transcribe")
        RecordingSyncEngine.synchronize(repo)
        assertEquals("blocked",repo.catalog.job(id)?.state);assertTrue(bridge.calls.isEmpty())
        assertTrue(repo.files.audio(asset).exists());assertEquals(1,repo.catalog.recordings().size)
    }
    @Test fun lossOfEitherLicenceCancelsAlreadyAcceptedWork()=runBlocking {
        val bridge=FixtureBridge();val paid=repo(AppPlan.PLUS,bridge);val asset=asset(paid);val id=paid.catalog.enqueue(asset.id,"transcribe")
        RecordingSyncEngine.synchronize(paid)
        bridge.state.value=bridge.state.value.copy(licenseState="required")
        RecordingSyncEngine.synchronize(paid)
        assertEquals(listOf(DictateProtocol.SUBMIT,DictateProtocol.CANCEL),bridge.calls)
        assertEquals("blocked",paid.catalog.job(id)?.state);assertTrue(paid.files.audio(asset).exists())
    }
    @Test fun dialerDowngradeAfterRecoveryCancelsRemoteWorkAndPreservesLibrary()=runBlocking {
        val bridge=FixtureBridge();val paid=repo(AppPlan.PLUS,bridge);val asset=asset(paid);val id=paid.catalog.enqueue(asset.id,"transcribe")
        RecordingSyncEngine.synchronize(paid)
        val free=repo(AppPlan.FREE,bridge);RecordingSyncEngine.synchronize(free)
        assertEquals(DictateProtocol.CANCEL,bridge.calls.last());assertEquals("blocked",free.catalog.job(id)?.state)
        assertEquals(asset,free.catalog.recordings().single())
    }
    @Test fun deleteCancelsEvenWhenProcessingRightsAreGone()=runBlocking {
        val bridge=FixtureBridge();val paid=repo(AppPlan.PLUS,bridge);val asset=asset(paid);val id=paid.catalog.enqueue(asset.id,"transcribe")
        RecordingSyncEngine.synchronize(paid);paid.catalog.delete(asset.id)
        bridge.state.value=bridge.state.value.copy(licenseState="required",apiKeyConfigured=false)
        val free=repo(AppPlan.FREE,bridge);RecordingSyncEngine.synchronize(free)
        assertEquals("cancelled",free.catalog.job(id)?.state);assertEquals(DictateProtocol.CANCEL,bridge.calls.last())
        assertTrue(free.catalog.texts(asset.id).isEmpty())
    }
    @Test fun completeOnlyAutomationCannotRunAsPowerUp()=runBlocking {
        val bridge=FixtureBridge();val power=repo(AppPlan.PLUS,bridge);val asset=asset(power)
        val id=power.catalog.enqueue(asset.id,"transcribe",automatic=true)
        RecordingSyncEngine.synchronize(power)
        assertEquals("blocked",power.catalog.job(id)?.state);assertTrue(bridge.calls.isEmpty())
    }
    @Test fun providerCannotAttachAStatusFromAnotherJob()=runBlocking {
        val bridge=FixtureBridge();bridge.wrongJob=true
        val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo);val id=repo.catalog.enqueue(asset.id,"transcribe")
        RecordingSyncEngine.synchronize(repo)
        assertEquals("failed",repo.catalog.job(id)?.state);assertTrue(repo.catalog.texts(asset.id).isEmpty())
    }
    @Test fun permanentRejectionDoesNotRetryForever()=runBlocking {
        val bridge=FixtureBridge();bridge.failure="UNSUPPORTED_AUDIO_CONTAINER"
        val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo);val id=repo.catalog.enqueue(asset.id,"transcribe")
        assertFalse(RecordingSyncEngine.synchronize(repo))
        assertEquals("failed",repo.catalog.job(id)?.state);assertTrue(repo.files.audio(asset).exists())
    }
    @Test fun missingRemoteJobCompletesDeletionWithoutRetryingForever()=runBlocking {
        val bridge=FixtureBridge();val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo)
        val id=repo.catalog.enqueue(asset.id,"transcribe");repo.catalog.update(id,"running",true)
        repo.catalog.delete(asset.id);bridge.failure="JOB_NOT_FOUND"
        assertFalse(RecordingSyncEngine.synchronize(repo));assertEquals("cancelled",repo.catalog.job(id)?.state)
    }
    @Test fun disconnectCancelsBeforeUnpairingAndKeepsLocalLibrary()=runBlocking {
        val bridge=FixtureBridge();val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo)
        val id=repo.catalog.enqueue(asset.id,"transcribe");repo.catalog.update(id,"running",true)
        repo.disconnect()
        assertEquals(listOf(DictateProtocol.CANCEL),bridge.calls)
        assertFalse(bridge.capabilities.value.connected);assertEquals("cancelled",repo.catalog.job(id)?.state)
        assertEquals(asset,repo.catalog.recordings().single());assertTrue(repo.files.audio(asset).exists())
    }
    @Test fun separateSpeakerJobCannotSilentlyFallBackToOrdinaryTranscription()=runBlocking {
        val bridge=FixtureBridge();val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo)
        val id=repo.catalog.enqueue(asset.id,"transcribe","@capture:")
        RecordingSyncEngine.synchronize(repo)
        assertEquals("blocked",repo.catalog.job(id)?.state);assertEquals("UPDATE_REQUIRED",repo.catalog.job(id)?.error)
        assertTrue(bridge.calls.isEmpty());assertTrue(repo.files.audio(asset).exists())
        bridge.state.value=bridge.state.value.copy(separateSpeakerChannels=true);repo.catalog.retry(id)
        RecordingSyncEngine.synchronize(repo)
        assertEquals("separate_speakers",bridge.lastSubmit?.getString("channelMode"))
        assertEquals("auto",bridge.lastSubmit?.getString("sourceLanguage"))
        assertEquals("queued",repo.catalog.job(id)?.state);assertTrue(checkNotNull(repo.catalog.job(id)).accepted)
    }
    @Test fun masterOffPausesPendingWorkAndBlocksNewProcessingWithoutIpc()=runBlocking {
        val bridge=FixtureBridge();val repo=repo(AppPlan.PLUS,bridge);val asset=asset(repo)
        val id=repo.catalog.enqueue(asset.id,"transcribe")
        val master=repo.moduleState as MutableStateFlow<Boolean>;master.value=false
        assertFalse(RecordingSyncEngine.synchronize(repo));assertEquals(0,bridge.refreshCalls);assertTrue(bridge.calls.isEmpty())
        assertEquals("queued",repo.catalog.job(id)?.state);assertEquals(1,repo.catalog.recordings().size)
        assertTrue(runCatching {repo.queue(asset.id)}.isFailure);assertEquals(0,bridge.refreshCalls)
        master.value=true;RecordingSyncEngine.synchronize(repo)
        assertTrue(bridge.calls.contains(DictateProtocol.SUBMIT));assertTrue(bridge.refreshCalls>0)
    }
    class FixtureBridge : DictateBridge {
        val state=MutableStateFlow(DictateCapabilities(true,"valid",true,true,true,268435456,14400,maxChunkBytes=8388608,maxChunkSeconds=300))
        override val capabilities=state.asStateFlow()
        val calls=mutableListOf<Int>();var wrongJob=false;var failure: String?=null
        var lastSubmit: Bundle?=null
        override fun installed()=true
        override fun setupIntent()=Intent()
        override suspend fun approveSetup()=Unit
        override fun trusted()=true
        override suspend fun disconnect() {state.value=DictateCapabilities()}
        var refreshCalls=0
        override suspend fun refresh()=state.value.also {refreshCalls++}
        override suspend fun request(what: Int,extras: Bundle): Bundle {
            if(what==DictateProtocol.SUBMIT)lastSubmit=Bundle(extras)
            calls.add(what)
            failure?.let {throw DictateFailure(it)}
            return Bundle().apply {putInt("protocolVersion",1);putBoolean("ok",true)
                putString("jobId",if(wrongJob)UUID.randomUUID().toString()else extras.getString("jobId"))
                putString("state",if(what==DictateProtocol.CANCEL)"cancelled"else"queued");putInt("completedParts",0);putInt("totalParts",1)}
        }
    }
}
