package dev.goodwy.rphone.recordings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class RecordingCatalogTest {
    private lateinit var catalog: RecordingCatalog
    @Before fun setup() {val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("recordings.db");catalog=RecordingCatalog(context)}
    @After fun cleanup() {catalog.close()}
    private fun asset(): SavedRecording {
        val id=UUID.randomUUID().toString()
        return SavedRecording(id,"Test Alexandru Butica",1000,60_000,480_000,"$id.m4a","call-1","audio/mp4").also(catalog::add)
    }
    @Test fun originalAndTranslationsPersistIndependentlyAfterReopening() {
        val recording=asset();val id=catalog.enqueue(recording.id,"transcribe")
        assertTrue(catalog.complete(requireNotNull(catalog.job(id)),"Discutie de test.","ro"))
        val original=catalog.texts(recording.id).single()
        val translated=catalog.enqueue(recording.id,"translate","en",original.id)
        assertTrue(catalog.complete(requireNotNull(catalog.job(translated)),"Test conversation.","en"))
        catalog.close();catalog=RecordingCatalog(ApplicationProvider.getApplicationContext())
        assertEquals(2,catalog.texts(recording.id).size)
        assertEquals("Discutie de test.",catalog.texts(recording.id).first().text)
        assertEquals("completed",catalog.job(translated)?.state)
        assertEquals(recording,catalog.recordings().single())
    }
    @Test fun deleteLeavesDurableCancellationAndRejectsLateResults() {
        val recording=asset();val id=catalog.enqueue(recording.id,"transcribe");catalog.update(id,"running",true)
        val job=requireNotNull(catalog.job(id));catalog.delete(recording.id)
        assertEquals("cancelling",catalog.job(id)?.state)
        assertFalse(catalog.complete(job,"Late result","ro"))
        assertTrue(catalog.recordings().isEmpty());assertTrue(catalog.texts(recording.id).isEmpty())
        catalog.close();catalog=RecordingCatalog(ApplicationProvider.getApplicationContext())
        assertEquals("cancelling",catalog.job(id)?.state);catalog.cancelled(id)
        assertEquals("cancelled",catalog.job(id)?.state)
    }
    @Test fun enqueueIsIdempotentAndBlockedWorkRetainsItsIdentity() {
        val recording=asset();val id=catalog.enqueue(recording.id,"transcribe")
        assertEquals(id,catalog.enqueue(recording.id,"transcribe"))
        catalog.update(id,"blocked",false,error="KEY_REQUIRED");catalog.retry(id)
        assertEquals("queued",catalog.job(id)?.state);assertEquals(id,catalog.enqueue(recording.id,"transcribe"))
        catalog.update(id,"failed",true)
        assertNotEquals(id,catalog.enqueue(recording.id,"transcribe"))
    }
    @Test fun cancellationWinsOverConcurrentStatusAndCompletion() {
        val recording=asset();val id=catalog.enqueue(recording.id,"transcribe");val job=requireNotNull(catalog.job(id))
        catalog.requestCancel(id);catalog.update(id,"running",true,1,2)
        assertEquals("cancelling",catalog.job(id)?.state)
        assertFalse(catalog.complete(job,"Must not appear","ro"));assertTrue(catalog.texts(recording.id).isEmpty())
    }
    @Test fun schemaUpgradeKeepsExistingRecordingsAndTranscripts() {
        val recording=asset();val id=catalog.enqueue(recording.id,"transcribe")
        catalog.complete(requireNotNull(catalog.job(id)),"Discutie de test.","ro")
        catalog.writableDatabase.execSQL("ALTER TABLE recordings DROP COLUMN codecMime")
        catalog.writableDatabase.version=1
        catalog.close()
        catalog=RecordingCatalog(ApplicationProvider.getApplicationContext())
        assertEquals(recording,catalog.recordings().single())
        assertEquals("Discutie de test.",catalog.texts(recording.id).single().text)
        assertEquals(2,catalog.readableDatabase.version)
    }
    @Test fun acceptedReplyNeverLosesCancellationAndTranslationChainIsDurable() {
        val recording=asset();val cancelled=catalog.enqueue(recording.id,"transcribe")
        catalog.requestCancel(cancelled);catalog.accepted(cancelled);catalog.update(cancelled,"running",true)
        assertEquals("cancelling",catalog.job(cancelled)?.state);assertTrue(requireNotNull(catalog.job(cancelled)).accepted)
        catalog.cancelled(cancelled)
        val id=catalog.enqueue(recording.id,"transcribe");val job=requireNotNull(catalog.job(id))
        assertTrue(catalog.complete(job,"Discutie de test.","ro","en"))
        val translation=catalog.jobs().single {it.operation=="translate"}
        assertEquals("en",translation.language);assertTrue(translation.automatic)
        assertEquals(catalog.texts(recording.id).single().id,translation.sourceTextId)
        assertFalse(catalog.complete(job,"Discutie de test.","ro","en"))
        assertEquals(1,catalog.jobs().count {it.operation=="translate"})
    }
    @Test fun resultIdentityAndOperationCannotCrossRecordings() {
        val recording=asset();val id=catalog.enqueue(recording.id,"transcribe");val job=requireNotNull(catalog.job(id))
        fun result()=JSONObject().put("protocolVersion",1).put("jobId",id).put("recordingId",recording.id).put("operation","transcribe").put("text","Discutie de test.").put("language","ro")
        assertEquals("Discutie de test.",RecordingResult.parse(result().toString(),job).text)
        for(field in listOf("jobId","recordingId","operation")) {
            assertThrows(IllegalArgumentException::class.java) {RecordingResult.parse(result().put(field,"different").toString(),job)}
        }
        assertThrows(IllegalArgumentException::class.java) {RecordingResult.parse(result().put("protocolVersion",2).toString(),job)}
        assertThrows(IllegalArgumentException::class.java) {RecordingResult.parse(result().put("text","").toString(),job)}
    }
    @Test fun libraryNeverAcceptsArbitraryPathsOrForeignTranscriptReferences() {
        val recording=asset()
        assertThrows(IllegalArgumentException::class.java) {catalog.add(recording.copy(id=UUID.randomUUID().toString(),fileName="../other.m4a"))}
        assertThrows(IllegalArgumentException::class.java) {catalog.enqueue(recording.id,"translate","en",999)}
        assertEquals(1,catalog.recordings().size)
    }
}
