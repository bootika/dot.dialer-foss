package dev.goodwy.rphone.recordings

import android.os.Bundle
import androidx.core.net.toUri
import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.recordings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object RecordingSyncEngine {
    private val lock=Mutex()
    suspend fun <T> mutate(block: suspend ()->T): T = lock.withLock {block()}
    suspend fun synchronize(repo: RecordingsRepository): Boolean = lock.withLock {
        repo.awaitReady()
        val jobs=repo.catalog.jobs().filter {it.state in setOf("queued","running","cancelling")}
        for(job in jobs) {
            try {
                if(job.state=="cancelling" || repo.catalog.recording(job.recordingId)==null) {
                    if(job.accepted || repo.bridge.trusted())repo.bridge.request(DictateProtocol.CANCEL,bundle(job))
                    repo.catalog.cancelled(job.id);repo.files.clearJob(job);repo.changed();continue
                }
                val capabilities=if(repo.integrationEnabled())repo.bridge.refresh()else repo.bridge.capabilities.value
                val access=repo.allowed(job.operation=="translate")
                val autoAllowed=!job.automatic || repo.plan()==AppPlan.PREMIUM &&
                    (if(job.operation=="translate")repo.translationLanguage()==job.language else repo.optedIn())
                if(access!=RecordingAccess.READY || !autoAllowed || job.operation=="translate" && job.language !in capabilities.targetLanguages) {
                    if(job.accepted)repo.bridge.request(DictateProtocol.CANCEL,bundle(job))
                    repo.catalog.update(job.id,"blocked",job.accepted,error=if(autoAllowed)access.name else "AUTOMATION_DISABLED")
                    repo.files.clearJob(job);repo.changed();continue
                }
                val reply=if(job.accepted)repo.bridge.request(DictateProtocol.STATUS,bundle(job)) else submit(repo,job)
                apply(repo,job,reply)
            } catch(error: CancellationException) { throw error }
            catch(error: DictateFailure) {
                val blocked=error.code in setOf("PAIR_REQUIRED","UNAUTHORIZED","LICENSE_REQUIRED","API_KEY_REQUIRED","KEY_REQUIRED")
                if(blocked && job.state=="cancelling")return@withLock false
                if(blocked)repo.catalog.update(job.id,"blocked",job.accepted,error=error.code)
                repo.changed()
                if(job.state=="cancelling" && error.code=="JOB_NOT_FOUND") {
                    repo.catalog.cancelled(job.id);repo.files.clearJob(job);repo.changed()
                } else if(!blocked) {
                    if(error.code in setOf("DISCONNECTED","NOT_INSTALLED","STORAGE_UNAVAILABLE","INTERNAL_ERROR","PROVIDER_UNAVAILABLE","NETWORK_UNAVAILABLE","RATE_LIMITED"))return@withLock true
                    repo.catalog.update(job.id,"failed",job.accepted,error=error.code);repo.files.clearJob(job);repo.changed()
                }
            } catch(error: Exception) {RecordingDiagnostics.failure("RecordingSyncEngine",error);repo.catalog.update(job.id,"failed",job.accepted,error="INVALID_RESPONSE");repo.files.clearJob(job);repo.changed()}
        }
        repo.catalog.jobs().any {it.state in setOf("queued","running","cancelling")}
    }
    private fun bundle(job: RecordingJob)=Bundle().apply {putString("jobId",job.id)}
    private suspend fun submit(repo: RecordingsRepository,job: RecordingJob): Bundle {
        val asset=repo.catalog.recording(job.recordingId) ?: error("Recording removed")
        require(RecordingPolicy.acceptedAudio(asset.bytes,asset.durationMs,asset.codecMime,repo.bridge.capabilities.value))
        val data=bundle(job).apply {
            putString("recordingId",asset.id);putString("operation",job.operation);putString("sourceLanguage","auto")
            if(job.operation=="translate") {
                val text=repo.catalog.texts(asset.id).single {it.id==job.sourceTextId && it.operation=="transcribe"}.text
                putString("textUri",repo.files.grantText(job,text).toString());putString("targetLanguage",job.language)
            } else putString("audioUri",repo.files.grantAudio(asset).toString())
        }
        return try {
            repo.bridge.request(DictateProtocol.SUBMIT,data).also {
                require(it.getString("jobId")==job.id)
                // Once accepted, Dictate has its own private copy. Recovery must poll, not resubmit.
                repo.catalog.accepted(job.id)
                repo.catalog.update(job.id,"running",true)
            }
        } finally {repo.files.revoke(asset);repo.files.clearJob(job)}
    }
    private suspend fun apply(repo: RecordingsRepository,original: RecordingJob,reply: Bundle) {
        require(reply.getString("jobId")==original.id)
        val state=reply.getString("state") ?: error("Missing job state")
        require(state in DictateProtocol.states)
        val done=reply.getInt("completedParts");val total=reply.getInt("totalParts")
        require(total>=0 && done>=0 && done<=total)
        val job=repo.catalog.job(original.id) ?: return
        if(job.state=="cancelling")return
        if(state=="completed") {
            val uri=(reply.getString("resultUri") ?: error("Missing result URI")).toUri()
            val result=RecordingResult.parse(repo.files.readResult(uri,repo.bridge::trusted),job)
            val language=repo.translationLanguage().takeIf {
                it in repo.bridge.capabilities.value.targetLanguages && repo.plan()==AppPlan.PREMIUM && repo.allowed(true)==RecordingAccess.READY
            }.orEmpty()
            repo.catalog.complete(job.copy(completedParts=done,totalParts=total),result.text,result.language,language)
        } else repo.catalog.update(job.id,state,true,done,total,if(state=="failed")"PROVIDER_ERROR" else "")
        repo.changed()
    }
}
