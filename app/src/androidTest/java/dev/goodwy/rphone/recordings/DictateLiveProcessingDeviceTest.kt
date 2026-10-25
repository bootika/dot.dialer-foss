package dev.goodwy.rphone.recordings

import android.app.Application
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bootika.dotdialer.core.recordings.RecordingAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicitly opted-in paid-service probe; never reads provider credentials or changes pairing. */
class DictateLiveProcessingDeviceTest {
    @Test fun authorizedNeutralAudioTranscribesAndTranslatesThroughRealProvider() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runDictateLiveProbe")=="true")
        val args=InstrumentationRegistry.getArguments()
        val fixture=args.getString("fixtureAudioName") ?: "adb-speech-fixture.wav"
        require(fixture in setOf("adb-speech-fixture.wav","experimental-call-fixture.wav"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        check(context.packageName=="dot.dialer.test")
        val report=JSONObject()
        val result=File(context.cacheDir,"dictate-live-result.json")
        val client=DictateBridgeClient(context)
        val repo=RecordingsRepository(context,bridge=client)
        var asset: SavedRecording?=null
        val staging=File(context.filesDir,"recordings/bridge/${UUID.randomUUID()}.wav")
        try {
            assertTrue("Existing trusted pairing required; test never grants it",client.trusted())
            repo.awaitReady()
            val caps=client.refresh()
            report.put("trusted",true).put("licenseState",caps.licenseState)
                .put("apiKeyConfigured",caps.apiKeyConfigured).put("access",repo.allowed().name)
            assertEquals(RecordingAccess.READY,repo.allowed())
            assertTrue("Romanian must be negotiated", "ro" in caps.targetLanguages)
            assertTrue("Existing work must remain untouched",repo.catalog.jobs().none {it.state in setOf("queued","running","cancelling")})
            staging.parentFile!!.mkdirs()
            File(context.cacheDir,fixture).copyTo(staging)
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.provider",staging)
            asset=repo.files.import(uri,"Test Alexandru Butica","",caps.maxInputBytes)
            repo.catalog.add(asset)
            suspend fun completed(id: String): RecordingJob = withTimeout(180_000) {
                while(true) {
                    RecordingSyncEngine.synchronize(repo)
                    val job=checkNotNull(repo.catalog.job(id))
                    if(job.state=="completed")return@withTimeout job
                    check(job.state !in setOf("failed","blocked","cancelled")) {"${job.operation}: ${job.state}/${job.error}"}
                    delay(1500)
                }
                error("unreachable")
            }
            val originalJob=completed(repo.queue(asset.id))
            val original=repo.catalog.texts(asset.id).single {it.jobId==originalJob.id}
            report.put("transcript",original.text).put("sourceLanguage",original.language)
            if(fixture=="adb-speech-fixture.wav")assertTrue(original.text.contains("meeting",true))
            else assertTrue(original.text.isNotBlank())
            if(args.getString("runTranslation")=="false") {
                report.put("transcriptionState",originalJob.state).put("passed",true)
                return@runBlocking
            }
            val translatedJob=completed(repo.queue(asset.id,"ro",original.id))
            val translated=repo.catalog.texts(asset.id).single {it.jobId==translatedJob.id}
            report.put("translation",translated.text).put("targetLanguage",translated.language)
                .put("transcriptionState",originalJob.state).put("translationState",translatedJob.state)
            assertTrue(translated.text.isNotBlank())
            assertEquals("ro",translated.language)
            report.put("passed",true)
            Unit
        } catch(error: Throwable) {
            report.put("passed",false).put("errorType",error.javaClass.simpleName)
                .put("error",error.message?.take(400))
            throw error
        } finally {
            asset?.let {created->
                repo.catalog.jobs().filter {it.recordingId==created.id && it.state in setOf("queued","running","blocked")}
                    .forEach {job->try {client.request(DictateProtocol.CANCEL,android.os.Bundle().apply {putString("jobId",job.id)})}catch(_:Exception){} }
                repo.delete(created)
            }
            staging.delete()
            result.writeText(report.toString(2))
        }
    }
}
