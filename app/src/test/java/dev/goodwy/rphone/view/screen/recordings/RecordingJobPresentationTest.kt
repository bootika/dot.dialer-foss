package dev.goodwy.rphone.view.screen.recordings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.recordings.RecordingCatalog
import dev.goodwy.rphone.recordings.RecordingJob
import dev.goodwy.rphone.recordings.SavedRecording
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RecordingJobPresentationTest {
    private fun job(id: String, state: String = "failed", operation: String = "transcribe",
        language: String = "", sourceTextId: Long = 0, recordingId: String = "recording-a") =
        RecordingJob(id, recordingId, operation, language, sourceTextId, state, true, 0, 1,
            if (state == "failed") "LICENSE_REQUIRED" else "", false)

    @Test fun failedAttemptMovesToHistoryWhileMatchingRetryIsActive() {
        for (state in listOf("queued", "running", "blocked", "cancelling")) {
            val old = job("old")
            val retry = job("retry", state)
            val presentation = recordingJobPresentation(listOf(retry, old))
            assertEquals(listOf(retry), presentation.current)
            assertEquals(listOf(old), presentation.history)
            assertEquals("failed", old.state)
            assertEquals("LICENSE_REQUIRED", old.error)
        }
    }

    @Test fun successfulRetryKeepsBothImmutableAttemptsInHistory() {
        val old = job("old")
        val success = job("retry", "completed")
        val presentation = recordingJobPresentation(listOf(success, old))
        assertTrue(presentation.current.isEmpty())
        assertEquals(listOf(success, old), presentation.history)
    }

    @Test fun latestFailureRemainsActionableWhileEarlierFailuresAreHistory() {
        val newest = job("latest")
        val old = job("old")
        val presentation = recordingJobPresentation(listOf(newest, old))
        assertEquals(listOf(newest), presentation.current)
        assertEquals(listOf(old), presentation.history)
    }

    @Test fun unrelatedSuccessCannotHideAnotherOperationLanguageSourceOrRecordingFailure() {
        val failure = job("failure", operation = "translate", language = "ro", sourceTextId = 7)
        val unrelated = listOf(
            failure.copy(id = "other-operation", state = "completed", operation = "transcribe"),
            failure.copy(id = "other-language", state = "completed", language = "de"),
            failure.copy(id = "other-source", state = "completed", sourceTextId = 8),
            failure.copy(id = "other-recording", state = "completed", recordingId = "recording-b"),
        )
        for (success in unrelated) {
            val presentation = recordingJobPresentation(listOf(success, failure))
            assertEquals(listOf(failure), presentation.current)
            assertEquals(listOf(success), presentation.history)
        }
    }

    @Test fun independentlyActiveWorkIsNeverDiscardedByPresentation() {
        val running = job("running", "running")
        val other = job("other", language = "@output:ro")
        val presentation = recordingJobPresentation(listOf(other, running))
        assertEquals(listOf(other, running), presentation.current)
        assertTrue(presentation.history.isEmpty())
    }

    @Test fun cancelledRetryDoesNotReviveItsOlderFailedAttempt() {
        val cancelled = job("retry", "cancelled")
        val old = job("old")
        val presentation = recordingJobPresentation(listOf(cancelled, old))
        assertTrue(presentation.current.isEmpty())
        assertEquals(listOf(cancelled, old), presentation.history)
    }

    @Test fun automaticFlagDoesNotSplitTheExistingEnqueueRequestIdentity() {
        val old = job("old")
        val completed = old.copy(id = "retry", state = "completed", automatic = true)
        assertTrue(recordingJobPresentation(listOf(completed, old)).current.isEmpty())
    }

    @Test fun catalogueRetryCompletionAndReopenKeepOriginalFailureAndSavedResult() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("recordings.db")
        var catalog = RecordingCatalog(context)
        try {
            val id = UUID.randomUUID().toString()
            catalog.add(SavedRecording(id, "Test Alexandru Butica", 1, 1000, 44, "$id.wav", "", "audio/wav", "audio/raw"))
            val failed = catalog.enqueue(id, "transcribe")
            catalog.update(failed, "failed", true, 0, 1, "LICENSE_REQUIRED")
            catalog.requestCancel(failed) // A failed provider attempt cannot be rewritten as cancelled.
            val retry = catalog.enqueue(id, "transcribe")
            assertNotEquals(failed, retry)
            assertEquals(listOf(retry), recordingJobPresentation(catalog.jobs()).current.map { it.id })
            catalog.update(retry, "running", true)
            assertTrue(catalog.complete(requireNotNull(catalog.job(retry)), "Text de test.", "ro"))
            catalog.close()
            catalog = RecordingCatalog(context)
            val presentation = recordingJobPresentation(catalog.jobs())
            assertTrue(presentation.current.isEmpty())
            assertEquals(listOf(retry, failed), presentation.history.map { it.id })
            assertEquals("failed", catalog.job(failed)?.state)
            assertEquals("LICENSE_REQUIRED", catalog.job(failed)?.error)
            assertEquals("Text de test.", catalog.texts(id).single().text)
        } finally { catalog.close() }
    }

    @Test fun blockedSameIdRetryRemainsCurrentWithoutInventingHistory() {
        val blocked = job("same-id", "blocked")
        assertEquals(listOf(blocked), recordingJobPresentation(listOf(blocked)).current)
        val queued = blocked.copy(state = "queued", error = "")
        val presentation = recordingJobPresentation(listOf(queued))
        assertEquals(listOf(queued), presentation.current)
        assertTrue(presentation.history.isEmpty())
    }
}