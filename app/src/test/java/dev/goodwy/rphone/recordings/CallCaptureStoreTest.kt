package dev.goodwy.rphone.recordings

import kotlinx.coroutines.flow.MutableStateFlow

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CallCaptureStoreTest {
    private lateinit var repo: RecordingsRepository
    private lateinit var root: File
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("recordings.db")
        root=File(context.filesDir,"recordings");root.deleteRecursively()
        repo=RecordingsRepository(context,integrationEnabled={true},moduleState=MutableStateFlow(true))
    }
    @After fun cleanup(){repo.catalog.close();root.deleteRecursively()}
    @Test fun interruptedStereoWavRecoversOnceAndRecordCreatesNoJobs()=runBlocking {
        val store=CallCaptureStore(repo);val identity=RecordingContactIdentity("+40720000001","test-contact","Test Alexandru Butica")
        val (asset,file)=store.begin("Test Alexandru Butica","call-test",false,"",identity)
        RandomAccessFile(file,"rw").use {it.seek(44);it.write(ByteArray(64003){32})}
        // Last three bytes were interrupted mid-frame. Recover only complete stereo frames.
        CallCaptureStore(repo).recover();store.recover()
        assertEquals(1,repo.catalog.recordings().size)
        val saved=checkNotNull(repo.catalog.recording(asset.id))
        assertEquals(1000L,saved.durationMs);assertEquals(64044L,saved.bytes)
        assertEquals(identity.number,saved.phoneNumber);assertEquals(identity.key,saved.contactKey);assertEquals(identity.name,saved.contactName)
        assertEquals(64044L,file.length());assertTrue(repo.catalog.jobs().isEmpty())
        RandomAccessFile(file,"r").use {it.seek(22);assertEquals(2,it.read());it.seek(40);assertEquals(0,it.read());assertEquals(250,it.read())}
    }
    @Test fun emptyCaptureDoesNotLeaveBrokenAsset()=runBlocking {
        val store=CallCaptureStore(repo);val (_,file)=store.begin("Test Alexandru Butica","call-test",false,"")
        store.recover();assertFalse(file.exists());assertTrue(repo.catalog.recordings().isEmpty())
    }
    @Test fun manualTargetCreatesDurableNonAutomaticTranslationAndOriginalOptOutWins()=runBlocking {
        val store=CallCaptureStore(repo);val (asset,file)=store.begin("Test Alexandru Butica","call-test",false,"")
        RandomAccessFile(file,"rw").use {it.seek(44);it.write(ByteArray(64000))};store.recover()
        val job=repo.catalog.enqueue(asset.id,"transcribe","@output:ro")
        assertTrue(repo.catalog.complete(checkNotNull(repo.catalog.job(job)),"A test conversation.","en","fr"))
        val translation=repo.catalog.jobs().single {it.operation=="translate"}
        assertEquals("ro",translation.language);assertFalse(translation.automatic)
        assertFalse(repo.catalog.complete(checkNotNull(repo.catalog.job(job)),"Duplicate.","en","fr"))
        val next=repo.catalog.enqueue(asset.id,"transcribe","@output:")
        repo.catalog.complete(checkNotNull(repo.catalog.job(next)),"Another test.","en","fr")
        assertEquals(1,repo.catalog.jobs().count {it.operation=="translate"})
    }
    @Test fun offlineTranscriptionIntentIsDurableAndDoesNotBlockTheNextCapture()=runBlocking {
        val store=CallCaptureStore(repo)
        repeat(2){index ->
            val (asset,file)=store.begin("Test Alexandru Butica","call-$index",true,"ro")
            RandomAccessFile(file,"rw").use{it.seek(44);it.write(ByteArray(64000))}
            store.recover();store.recover()
            val job=repo.catalog.jobs().single{it.recordingId==asset.id}
            assertEquals("queued",job.state);assertEquals("@capture:ro",job.language);assertFalse(job.accepted)
        }
        assertEquals(2,repo.catalog.recordings().size);assertEquals(2,repo.catalog.jobs().size)
    }
    @Test fun headerRejectsOversizeAndIncompleteStereoFrame() {
        val file=File(root.apply {mkdirs()},"header.wav")
        RandomAccessFile(file,"rw").use {
            assertThrows(IllegalArgumentException::class.java){CallCaptureStore.writeHeader(it,CallCaptureStore.MAX_BYTES+4)}
            assertThrows(IllegalArgumentException::class.java){CallCaptureStore.writeHeader(it,3)}
        }
    }
    @Test fun fullCallWavBeyondPrototypeLimitRecoversWithoutTruncation()=runBlocking {
        val store=CallCaptureStore(repo);val (asset,file)=store.begin("Test Alexandru Butica","call-test",false,"")
        val bytes=64000L*125
        RandomAccessFile(file,"rw").use {it.setLength(bytes+44);CallCaptureStore.writeHeader(it,bytes)}
        store.recover()
        assertEquals(125000L,repo.catalog.recording(asset.id)?.durationMs)
        assertEquals(bytes+44,file.length());assertTrue(repo.catalog.jobs().isEmpty())
    }
    @Test fun riffHeaderSupportsLongCallAndStorageReserveStopsBeforeDiskFull() {
        RandomAccessFile(File(root.apply{mkdirs()},"long.wav"),"rw").use {
            CallCaptureStore.writeHeader(it,64000L*60*120)
            it.seek(40);var length=0L;for(i in 0..3)length=length or (it.read().toLong() shl (8*i))
            assertEquals(64000L*60*120,length)
        }
        CallCaptureStore.requireStorage(CallCaptureStore.STORAGE_RESERVE+1)
        assertThrows(IllegalStateException::class.java){CallCaptureStore.requireStorage(CallCaptureStore.STORAGE_RESERVE)}
    }
}
