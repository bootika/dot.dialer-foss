package dev.goodwy.rphone.controller.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleContactPhotoScanTest {
    private val candidates = (1L..4L).map { PhotoScanCandidate(it, "0770090012$it") }

    @Test fun reportsUpdatesSkipsAndFailuresWithoutCallingEverythingFixed() = runTest {
        val outcomes = listOf(CallPhotoHydrationOutcome.Updated, CallPhotoHydrationOutcome.AlreadyAvailable,
            CallPhotoHydrationOutcome.NotEligible, CallPhotoHydrationOutcome.Failed("Unavailable"))
        val result = runPhotoScan(candidates, { false }, { true }, { outcomes[(it.contactId - 1).toInt()] }, {})
        assertEquals(PhotoScanStatus.Completed, result.status)
        assertEquals(4, result.checked)
        assertEquals(1, result.updated)
        assertEquals(2, result.skipped)
        assertEquals(1, result.failed)
        assertFalse(result.running)
    }

    @Test fun cancelFinishesCurrentContactAndNeverTouchesNext() = runTest {
        var stop = false
        var calls = 0
        val result = runPhotoScan(candidates, { stop }, { true }, {
            calls++; stop = true; CallPhotoHydrationOutcome.Updated
        }, {})
        assertEquals(PhotoScanStatus.Stopped, result.status)
        assertEquals(1, calls)
        assertEquals(1, result.updated)
    }

    @Test fun revokedConsentStopsWithoutClaimingRemainingContactsWereChecked() = runTest {
        val result = runPhotoScan(candidates, { false }, { true }, { CallPhotoHydrationOutcome.ConsentRequired }, {})
        assertEquals(PhotoScanStatus.ConsentRequired, result.status)
        assertEquals(0, result.checked)
    }

    @Test fun missingWritePermissionNeverMutatesAContact() = runTest {
        val result = runPhotoScan(candidates, { false }, { false }, { error("Must not write") }, {})
        assertEquals(PhotoScanStatus.PermissionMissing, result.status)
        assertEquals(0, result.checked)
    }

    @Test fun emptyAccountCompletesWithoutNetworkOrWrites() = runTest {
        val result = runPhotoScan(emptyList(), { false }, { true }, { error("Must not run") }, {})
        assertEquals(PhotoScanStatus.Completed, result.status)
        assertEquals(0, result.total)
    }

    @Test fun networkFailureStopsInsteadOfRetryingEveryContact() = runTest {
        var calls = 0
        val result = runPhotoScan(candidates, { false }, { true }, {
            calls++; CallPhotoHydrationOutcome.Failed("Offline", abortScan = true)
        }, {})
        assertEquals(PhotoScanStatus.Failed, result.status)
        assertEquals(1, calls)
        assertEquals(1, result.failed)
        assertEquals(1, result.checked)
    }

    @Test fun transientFailureRetriesCurrentContactWithoutCountingItTwice() = runTest {
        var calls = 0
        val result = runPhotoScan(candidates.take(1), { false }, { true }, {
            if (++calls < 3) CallPhotoHydrationOutcome.Failed("Network unavailable", true) else CallPhotoHydrationOutcome.Updated
        }, {}, retryTransient = true, retryPause = {})
        assertEquals(3, calls)
        assertEquals(1, result.checked)
        assertEquals(1, result.updated)
        assertEquals(0, result.failed)
    }

    @Test fun repeatedOutageStopsAfterBoundedRetriesAndRecordsSafeCategory() = runTest {
        var calls = 0
        val result = runPhotoScan(candidates, { false }, { true }, {
            calls++; CallPhotoHydrationOutcome.Failed("Google photo service returned HTTP 503", true)
        }, {}, retryTransient = true, retryPause = {})
        assertEquals(3, calls)
        assertEquals(PhotoScanStatus.Failed, result.status)
        assertEquals(1, result.failureCounts[PhotoScanFailureKind.GoogleService])
    }

    @Test fun resumeKeepsPriorTotalsAndProcessesOnlyRemainingCandidates() = runTest {
        val initial = PhotoScanState(PhotoScanStatus.Stopped, total = 4, checked = 2, updated = 1, skipped = 1)
        val result = runPhotoScan(candidates.drop(2), { false }, { true }, { CallPhotoHydrationOutcome.AlreadyAvailable }, {}, initial = initial)
        assertEquals(PhotoScanStatus.Completed, result.status)
        assertEquals(4, result.total)
        assertEquals(4, result.checked)
        assertEquals(1, result.updated)
        assertEquals(3, result.skipped)
    }

    @Test fun cancellingDuringRetryDoesNotStartAnotherDownload() = runTest {
        var calls = 0
        var stop = false
        val result = runPhotoScan(candidates, { stop }, { true }, {
            calls++; CallPhotoHydrationOutcome.Failed("Network unavailable", true)
        }, {}, retryTransient = true, retryPause = { stop = true })
        assertEquals(1, calls)
        assertEquals(0, result.checked)
        assertEquals(PhotoScanStatus.Stopped, result.status)
    }
}
