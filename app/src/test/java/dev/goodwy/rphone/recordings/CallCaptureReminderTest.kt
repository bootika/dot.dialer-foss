package dev.goodwy.rphone.recordings

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class CallCaptureReminderTest {
    private class Fixture {
        val reminder=CaptureReminder()
        var enabled=true;var setup=false;var ready=false;var shown=true;var posts=0;var probes=0
        suspend fun check(probe: suspend ()->Boolean={ready})=reminder.reconcile(
            {enabled},{setup},{probes++;probe()},{shown=true;posts++},{shown=false})
    }
    @Test fun readyClearsStaleReminderWithoutStartingAnything()=runBlocking {
        val f=Fixture();f.ready=true;f.check();assertFalse(f.shown);assertEquals(0,f.posts);assertEquals(1,f.probes)
    }
    @Test fun disabledOrSetupNeverPostsOrProbes()=runBlocking {
        val f=Fixture();f.enabled=false;f.check();assertFalse(f.shown);assertEquals(0,f.probes)
        f.enabled=true;f.setup=true;f.shown=true;f.check();assertFalse(f.shown);assertEquals(0,f.posts);assertEquals(0,f.probes)
    }
    @Test fun confirmedUnavailablePosts()=runBlocking {
        val f=Fixture();f.check();assertTrue(f.shown);assertEquals(1,f.posts);assertEquals(2,f.probes)
    }
    @Test fun transientFailureClearsInsteadOfPosting()=runBlocking {
        val f=Fixture();f.check {f.probes>1};assertFalse(f.shown);assertEquals(0,f.posts)
    }
    @Test fun newReadyObservationWinsOverOldFailedProbe()=runBlocking {
        val f=Fixture();val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val job=launch {f.check {entered.complete(Unit);finish.await();false}}
        entered.await();f.reminder.clear {f.shown=false};finish.complete(Unit);job.join()
        assertFalse(f.shown);assertEquals(0,f.posts)
    }
    @Test fun disableDuringProbeCannotRepost()=runBlocking {
        val f=Fixture();f.check {f.enabled=false;false};assertFalse(f.shown);assertEquals(0,f.posts)
    }
    @Test fun concurrentChecksAreCoalesced()=runBlocking {
        val f=Fixture();val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val job=launch {f.check {entered.complete(Unit);finish.await();false}}
        entered.await();f.check();finish.complete(Unit);job.join();assertEquals(1,f.posts);assertEquals(2,f.probes)
    }
}
