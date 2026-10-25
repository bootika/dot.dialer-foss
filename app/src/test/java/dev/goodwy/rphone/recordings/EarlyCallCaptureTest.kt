package dev.goodwy.rphone.recordings

import android.telecom.Call
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.io.EOFException
import java.io.IOException

class EarlyCallCaptureTest {
    private class Channel(val next: ()->ByteArray): CaptureChannel {
        override fun status()="STARTED"
        override fun frame()=next()
        override fun close(){}
    }
    @Test fun earlyAudioContinuesThroughConnectionWithoutRestart()=runTest {
        var connected=false;var stop=false;var opens=0;var frames=0;var closes=0
        captureEarlyCall({connected},{true},{stop},{opens++;Channel{byteArrayOf(1,0,2,0)}},{fail("No wait after successful early audio")},
            {frames++;if(frames==1)connected=true;if(frames==3)stop=true},{closes++})
        assertEquals(1,opens);assertEquals(3,frames);assertEquals(1,closes)
    }
    @Test fun unavailableEarlyCaptureRetriesOnceOnConnection()=runTest {
        var connected=false;var stop=false;var opens=0;var frames=0;var waits=0
        captureEarlyCall({connected},{true},{stop},{opens++;if(opens==1)throw EOFException();Channel{byteArrayOf(1,0,2,0)}},
            {waits++;connected=true},{frames++;stop=true},{})
        assertEquals(2,opens);assertEquals(1,frames);assertEquals(1,waits)
    }
    @Test fun partialEarlyAudioAndConnectedFallbackFeedOneConsumer()=runTest {
        var connected=false;var stop=false;var opens=0;var first=true;val frames=mutableListOf<Byte>()
        captureEarlyCall({connected},{true},{stop},{opens++;Channel{if(opens==1){if(first){first=false;byteArrayOf(1,0,1,0)}else throw EOFException()}else byteArrayOf(2,0,2,0)}},
            {connected=true},{frames+=it[0];if(frames.size==2)stop=true},{})
        assertEquals(listOf<Byte>(1,2),frames);assertEquals(2,opens)
    }
    @Test fun cancelledOrInvalidatedPendingRequestNeverStartsOnLaterConnection()=runTest {
        for(reason in listOf("stop","off","hold","destroyed","replacement","conference","identity","generation","rights")) {
            var valid=true;var connected=false;var stop=false;var opens=0
            captureEarlyCall({connected},{valid},{stop},{opens++;throw EOFException()},
                {if(reason=="stop")stop=true else valid=false;connected=true},{fail(reason)},{})
            assertEquals(reason,1,opens)
        }
    }
    @Test fun connectedFailureDoesNotRetryOrWaitForever()=runTest {
        var connected=false;var opens=0
        val result=runCatching {captureEarlyCall({connected},{true},{false},{opens++;throw EOFException()},
            {connected=true},{fail("No frames")},{})}
        assertTrue(result.exceptionOrNull() is EOFException);assertEquals(2,opens)
    }
    @Test fun validationAndStorageFailuresAreNotDeferredAsAudioUnavailability()=runTest {
        var opens=0
        val invalid=runCatching {captureEarlyCall({false},{true},{false},{opens++;throw IllegalArgumentException("Invalid identity")},{fail("No wait")},{},{})}
        assertTrue(invalid.exceptionOrNull() is IllegalArgumentException);assertEquals(1,opens)
        val storage=runCatching {captureEarlyCall({false},{true},{false},{Channel{byteArrayOf(1,0,2,0)}},{fail("No wait")},{throw IOException("Disk")},{})}
        assertTrue(storage.exceptionOrNull() is IOException)
    }
    @Test fun onlyLiveStandalonePhasesCanBeRequestedAndWaitingIsBusy() {
        for(s in listOf(Call.STATE_CONNECTING,Call.STATE_DIALING,Call.STATE_RINGING,Call.STATE_ACTIVE))assertTrue(captureCallStateAllowed(s))
        for(s in listOf(Call.STATE_NEW,Call.STATE_SELECT_PHONE_ACCOUNT,Call.STATE_HOLDING,Call.STATE_DISCONNECTING,Call.STATE_DISCONNECTED))assertFalse(captureCallStateAllowed(s))
        assertTrue(CallCaptureState("call","waiting").busy)
    }
}
