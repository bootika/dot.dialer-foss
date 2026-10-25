package dev.goodwy.rphone.recordings

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Read-only opt-in real UID/socket handshake. Never starts a recorder. */
class CallCaptureBackendDeviceTest {
    @Test fun authorizedHelperCapabilitiesAreAvailableWithoutStartingCapture()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runCaptureHelperProbe")=="true")
        assertTrue(AdbCallCapture.get(InstrumentationRegistry.getInstrumentation().targetContext).available())
    }
    @Test fun readyModuleRemovesStaleReminderIncludingActivationFastPath()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runCaptureHelperProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val backend=IntegratedCallCapture.get(context)
        assertTrue(backend.enabled());assertTrue(backend.available())
        val manager=context.getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.areNotificationsEnabled())
        fun seed() {
            CaptureSetupNotifications.channel(context)
            manager.notify(CaptureSetupNotifications.RESTART,androidx.core.app.NotificationCompat.Builder(context,"call_capture_setup")
                .setSmallIcon(dev.goodwy.rphone.R.drawable.ic_phone).setContentTitle("Test Alexandru Butica").setSilent(true).build())
            assertTrue(manager.activeNotifications.any {it.id==CaptureSetupNotifications.RESTART})
        }
        fun cleared()=assertFalse(manager.activeNotifications.any {it.id==CaptureSetupNotifications.RESTART})
        try {
            seed();CaptureSetupNotifications.reconcile(context);cleared()
            seed();assertTrue(backend.available());cleared()
            seed();backend.activate();cleared();assertEquals("ready",backend.state.value.phase)
        }finally {manager.cancel(CaptureSetupNotifications.RESTART)}
    }
    @Test fun inspectOnlyExplicitlyNamedCaptureAssets()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("runCaptureAssetProbe")=="true")
        val ids=checkNotNull(args.getString("captureIds")).split(',').map {java.util.UUID.fromString(it).toString()}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=RecordingsRepository.get(context)
        if(args.getString("processIndependentCapture")=="true") {
            require(ids.size==1)
            require(repo.catalog.jobs().none {it.state in setOf("queued","running","cancelling") && it.recordingId !in ids})
            repo.awaitReady();assertTrue(repo.bridge.refresh().separateSpeakerChannels)
            val jobId=repo.queue(ids.single(),outputLanguage="en")
            kotlinx.coroutines.withTimeout(180000) {
                while(true) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){RecordingSyncEngine.synchronize(repo)}
                    val job=checkNotNull(repo.catalog.job(jobId))
                    check(job.state !in setOf("failed","blocked","cancelled")){"${job.state}/${job.error}"}
                    val original=repo.catalog.texts(ids.single()).firstOrNull {it.jobId==jobId}
                    if(original!=null) {
                        assertTrue(original.text.substringAfter("You:\n").substringBefore("\n\nOther participant:").contains("test",true))
                        assertTrue(original.text.contains("Other participant:"))
                        val translation=repo.catalog.jobs().firstOrNull {it.operation=="translate" && it.sourceTextId==original.id}
                        if(translation?.state=="completed") {
                            assertEquals("en",translation.language);assertFalse(translation.automatic)
                            val text=repo.catalog.texts(ids.single()).single {it.jobId==translation.id}
                            assertTrue(text.text.contains("You:"));assertTrue(text.text.contains("Other participant:"));break
                        }
                        check(translation?.state !in setOf("failed","blocked","cancelled")){"Translation failed"}
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
        val report=JSONArray()
        for(id in ids) {
            val asset=checkNotNull(repo.catalog.recording(id));val file=repo.files.audio(asset)
            assertEquals(file.length(),asset.bytes);assertTrue(asset.durationMs>0)
            val bytes=file.readBytes();assertEquals("RIFF",bytes.copyOfRange(0,4).toString(Charsets.US_ASCII))
            assertEquals(2,bytes[22].toInt());assertEquals(16,bytes[34].toInt())
            var peak=0;for(i in 44 until bytes.size-1 step 2)peak=maxOf(peak,kotlin.math.abs(((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toInt()))
            assertTrue(peak>0)
            if(args.getString("markIndependentCapture")=="true") {
                require(ids.size==1 && bytes.size>44 && bytes[22].toInt()==2)
                File(context.filesDir,"recordings/capture/$id.json").apply {parentFile?.mkdirs();writeText("{\"version\":1,\"channels\":\"uplink_downlink\"}")}
            }
            val jobs=repo.catalog.jobs().filter {it.recordingId==id}
            report.put(JSONObject().put("id",id).put("durationMs",asset.durationMs).put("bytes",asset.bytes).put("channels",2).put("peak",peak)
                .put("jobs",JSONArray(jobs.map {JSONObject().put("operation",it.operation).put("state",it.state).put("automatic",it.automatic).put("language",it.language)}))
                .put("texts",JSONArray(repo.catalog.texts(id).map {JSONObject().put("operation",it.operation).put("text",it.text).put("language",it.language)})))
        }
        File(context.cacheDir,"capture-runtime-result.json").writeText(report.toString(2))
    }
}
