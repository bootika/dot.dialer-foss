package dev.goodwy.rphone.recordings

import android.content.Context
import android.net.Uri
import dev.goodwy.rphone.controller.license.PlanAccessManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.recordings.*
import io.github.bootika.dotdialer.core.license.AppPlan
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal class RecordingsRepository internal constructor(val context: android.app.Application, planProvider: ()->PlanAccessManager = {PlanAccessManager.get(context)},
    val bridge: DictateBridge = DictateBridgeClient(context),
    val integrationEnabled: ()->Boolean = {dev.goodwy.rphone.BuildConfig.DICTATE_INTEGRATION_ENABLED},
    val moduleState: kotlinx.coroutines.flow.StateFlow<Boolean> = IntegratedCallCapture.get(context).optedIn) {
    internal val catalog=RecordingCatalog(context)
    internal val files=RecordingFiles(context)
    private val plans by lazy(planProvider)
    private val prefs by lazy {PreferenceManager(context)}
    private val mutable=MutableStateFlow(0L)
    val revision=mutable.asStateFlow()
    val preferencesRevision get()=prefs.settingsChanged
    fun changed() { mutable.value=mutable.value+1 }
    suspend fun awaitReady() {withTimeout(15_000) {plans.state.first {it.ready}}}
    fun moduleEnabled()=moduleState.value
    fun plan()=plans.effective
    fun optedIn()=prefs.getBoolean(AUTO_TRANSCRIBE,false)
    fun translationLanguage()=prefs.getString(AUTO_TRANSLATE,"") ?: ""
    fun outputLanguage()=prefs.getString(OUTPUT_LANGUAGE,"") ?: ""
    fun separateSpeakers(id: String): Boolean {
        require(java.util.UUID.fromString(id).toString()==id)
        val file=java.io.File(context.filesDir,"recordings/capture/$id.json")
        if(!file.exists())return false
        require(file.length() in 1..1024)
        val source=org.json.JSONObject(file.readText())
        return source.getInt("version")==1 && source.getString("channels")=="uplink_downlink"
    }
    suspend fun setOutputLanguage(language: String) {
        awaitReady();require(allowed()==RecordingAccess.READY)
        require(language.isEmpty() || language in refreshBridge().targetLanguages && allowed(true)==RecordingAccess.READY)
        prefs.setString(OUTPUT_LANGUAGE,language);changed()
    }
    private val captureAuthorization by lazy {CaptureAuthorization(context)}
    suspend fun refreshBridge(): DictateCapabilities = bridge.refresh().also {captureAuthorization.observe(it)}
    fun captureAllowed(separateSpeakers: Boolean=false): Boolean {
        if(!integrationEnabled() || !moduleEnabled() || plan()<AppPlan.PLUS || !bridge.trusted())return false
        return captureAuthorization.valid(separateSpeakers)
    }
    fun allowed(translate: Boolean=false)=if(integrationEnabled() && moduleEnabled())RecordingPolicy.processing(plan(),bridge.capabilities.value,translate)else RecordingAccess.UNSUPPORTED
    suspend fun import(uri: Uri,title: String,callKey: String,phoneNumber: String=""): SavedRecording = withContext(Dispatchers.IO) {
        awaitReady()
        require(moduleEnabled())
        val capabilities=refreshBridge()
        require(allowed()==RecordingAccess.READY)
        val identity=recordingContactIdentity(context,phoneNumber)
        val asset=files.import(uri,title,callKey,capabilities.maxInputBytes).copy(phoneNumber=identity.number,contactKey=identity.key,contactName=identity.name)
        if(!RecordingPolicy.acceptedAudio(asset.bytes,asset.durationMs,asset.codecMime,capabilities)) {
            files.audio(asset).delete();throw DictateFailure("UNSUPPORTED_AUDIO")
        }
        try { catalog.add(asset) } catch(error: Exception) {files.audio(asset).delete();throw error}
        changed()
        if(RecordingPolicy.automatic(plan(),optedIn(),capabilities)) queue(asset.id,automatic=true)
        asset
    }
    suspend fun queue(id: String,translateTo: String="",sourceTextId: Long=0,automatic: Boolean=false,outputLanguage: String?=null): String = withContext(Dispatchers.IO) {
        awaitReady();require(moduleEnabled());refreshBridge()
        require(allowed(translateTo.isNotEmpty())==RecordingAccess.READY)
        if(automatic) require(plan()==AppPlan.PREMIUM)
        if(translateTo.isNotEmpty()) require(translateTo in bridge.capabilities.value.targetLanguages)
        if(!outputLanguage.isNullOrEmpty())require(allowed(true)==RecordingAccess.READY && outputLanguage in bridge.capabilities.value.targetLanguages)
        val asset=catalog.recording(id) ?: error("Recording removed")
        val separate=translateTo.isEmpty() && separateSpeakers(id)
        if(separate)require(bridge.capabilities.value.separateSpeakerChannels)
        require(RecordingPolicy.acceptedAudio(asset.bytes,asset.durationMs,asset.codecMime,bridge.capabilities.value))
        val language=when {separate->"@capture:${outputLanguage ?: this@RecordingsRepository.outputLanguage()}";outputLanguage!=null->"@output:$outputLanguage";else->translateTo}
        val job=catalog.enqueue(id,if(translateTo.isEmpty())"transcribe" else "translate",language,sourceTextId,automatic)
        RecordingSyncJob.schedule(context);changed();job
    }
    suspend fun cancel(id: String)=withContext(Dispatchers.IO) {catalog.requestCancel(id);RecordingSyncJob.schedule(context);changed()}
    suspend fun retry(job: RecordingJob)=withContext(Dispatchers.IO) {
        require(moduleEnabled());refreshBridge();require(allowed(job.operation=="translate")==RecordingAccess.READY)
        if(job.state=="blocked" && !job.accepted) {catalog.retry(job.id);RecordingSyncJob.schedule(context);changed()}
        else {catalog.requestCancel(job.id);queue(job.recordingId,if(job.operation=="translate")job.language else "",job.sourceTextId,job.automatic,
            job.language.takeIf {it.startsWith("@output:") || it.startsWith("@capture:")}?.substringAfter(':'))}
    }
    suspend fun delete(asset: SavedRecording)=withContext(Dispatchers.IO) {
        catalog.delete(asset.id);files.revoke(asset);files.audio(asset).delete();java.io.File(context.filesDir,"recordings/capture/${asset.id}.json").delete();RecordingSyncJob.schedule(context);changed()
    }
    suspend fun disconnect()=withContext(Dispatchers.IO) {RecordingSyncEngine.mutate {
        for(job in catalog.jobs().filter {it.state in setOf("queued","running","blocked","cancelling")}) {
            catalog.requestCancel(job.id)
            if(job.accepted || bridge.trusted())try {bridge.request(DictateProtocol.CANCEL,android.os.Bundle().apply {putString("jobId",job.id)})}
                catch(error: DictateFailure) {if(error.code!="JOB_NOT_FOUND")throw error}
            catalog.cancelled(job.id);files.clearJob(job)
        }
        captureAuthorization.clear();bridge.disconnect();changed()
    }}
    suspend fun setAutomatic(enabled: Boolean) {
        require(!enabled || integrationEnabled() && moduleEnabled())
        require(plan()==AppPlan.PREMIUM)
        prefs.setBoolean(AUTO_TRANSCRIBE,enabled);changed()
    }
    suspend fun setTranslation(language: String) {
        require(language.isEmpty() || integrationEnabled() && moduleEnabled())
        require(plan()==AppPlan.PREMIUM)
        require(language.isEmpty() || language in refreshBridge().targetLanguages)
        prefs.setString(AUTO_TRANSLATE,language);changed()
    }
    companion object {
        const val AUTO_TRANSCRIBE="recording_auto_transcribe"
        const val AUTO_TRANSLATE="recording_auto_translate_language"
        const val OUTPUT_LANGUAGE="recording_output_language"
        @Volatile private var instance: RecordingsRepository?=null
        fun get(context: Context): RecordingsRepository=instance ?: synchronized(this) {
            instance ?: RecordingsRepository(context.applicationContext as android.app.Application).also {instance=it}
        }
    }
}
