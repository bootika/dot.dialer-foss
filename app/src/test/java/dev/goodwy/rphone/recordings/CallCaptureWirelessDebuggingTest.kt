package dev.goodwy.rphone.recordings

import org.junit.Test
import org.junit.Assert.*

class CallCaptureWirelessDebuggingTest {
    @Test fun onlyFixedWirelessDisableAndReadbackAreAllowed() {
        val commands=mutableListOf<List<String>>()
        assertTrue(WirelessDebuggingStopper.disable {args ->commands.add(args.toList());if(args[1]=="get")"0\n" else ""})
        assertEquals(listOf(listOf("/system/bin/settings","put","global","adb_wifi_enabled","0"),
            listOf("/system/bin/settings","get","global","adb_wifi_enabled")),commands)
        assertTrue(commands.flatten().none {it=="adb_enabled" || it=="1"})
    }
    @Test fun permissionDeniedIsNotReportedAsWirelessOff() {
        assertFalse(WirelessDebuggingStopper.disable {throw SecurityException("Settings refused")})
    }
    @Test fun failedOrUnknownReadbackNeedsManualShutdown() {
        for(value in listOf("1","null","", "Error"))assertFalse(WirelessDebuggingStopper.disable {args ->if(args[1]=="get")value else ""})
    }
}
