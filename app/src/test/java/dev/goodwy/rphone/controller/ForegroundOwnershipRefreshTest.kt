package dev.goodwy.rphone.controller

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundOwnershipRefreshTest {
    @Test fun foregroundIsAsynchronousThrottledAndOfflineSafe() = runTest {
        var now=1000L; var reachable=false; var busy=false; var refreshes=0
        val refresh=ForegroundOwnershipRefresh(this,{reachable},{busy},{now}){refreshes++}
        refresh.onForeground()
        assertEquals(0,refreshes)
        runCurrent();assertEquals(0,refreshes)
        reachable=true
        refresh.onForeground();assertEquals(0,refreshes)
        runCurrent();assertEquals(1,refreshes)
        repeat(20){refresh.onForeground()};runCurrent();assertEquals(1,refreshes)
        now+=300000
        busy=true;refresh.onForeground();runCurrent();assertEquals(1,refreshes)
        busy=false;refresh.onForeground();runCurrent();assertEquals(2,refreshes)
    }
    @Test fun unavailableConnectivityCannotThrowIntoAnActivityOrConsumeRetryWindow() = runTest {
        var fails=true;var refreshes=0
        val refresh=ForegroundOwnershipRefresh(this,{if(fails)error("connectivity unavailable") else true},{false},{1000L}){refreshes++}
        refresh.onForeground();runCurrent();assertEquals(0,refreshes)
        fails=false;refresh.onForeground();runCurrent();assertEquals(1,refreshes)
    }
}
