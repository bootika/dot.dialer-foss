package io.github.bootika.dotdialer.diagnostics

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import io.github.bootika.dotdialer.core.diagnostics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class CarLoggingRuntimeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun call(number: String = "+40700000001", presentation: Int = TelecomManager.PRESENTATION_ALLOWED): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details,"mHandle",Uri.fromParts("tel",number,null))
        ReflectionHelpers.setField(details,"mHandlePresentation",presentation)
        ReflectionHelpers.setField(details,"mCallerDisplayName","Test Alexandru Butica")
        ReflectionHelpers.setField(details,"mCallerDisplayNamePresentation",TelecomManager.PRESENTATION_ALLOWED)
        if(Build.VERSION.SDK_INT >= 30) ReflectionHelpers.setField(details,"mContactDisplayName","Test Alexandru Butica")
        if(Build.VERSION.SDK_INT >= 34) ReflectionHelpers.setField(details,"mContactPhotoUri",Uri.parse("content://com.android.contacts/photo/1"))
        if(Build.VERSION.SDK_INT >= 31) ReflectionHelpers.setField(details,"mState",Call.STATE_RINGING)
        ReflectionHelpers.setField(call,"mState",Call.STATE_RINGING)
        ReflectionHelpers.setField(call,"mDetails",details)
        return call
    }
    @Test @Config(sdk=[29,30,34,35]) fun apiGuardsAndPermissionsKeepUnavailabilityDistinctFromNoName() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        val event=readCallIdentityDiagnostic(context,call(),7)
        assertEquals(DiagnosticPresence.PRESENT,event.callerName)
        assertEquals(if(Build.VERSION.SDK_INT>=30) DiagnosticPresence.PRESENT else DiagnosticPresence.UNAVAILABLE,event.contactName)
        assertEquals(if(Build.VERSION.SDK_INT>=34) DiagnosticPresence.PRESENT else DiagnosticPresence.UNAVAILABLE,event.contactPhoto)
        assertEquals(DiagnosticNumberForm.INTERNATIONAL_PLUS,event.numberForm)
        assertEquals(145,event.addressType)
        val report=DiagnosticDetailFormatter.format(event)
        for(secret in listOf("Test Alexandru Butica","+40700000001","content://")) assertFalse(report.contains(secret))
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals(DiagnosticPresence.UNAVAILABLE,readCallIdentityDiagnostic(context,call(),7).contactName)
        assertEquals(DiagnosticPresence.UNAVAILABLE,readCallIdentityDiagnostic(context,call(),7).contactPhoto)
    }
    @Test fun hiddenHandlesCannotBeInterpretedAsNationalNumbersOrKnownContacts() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        val event=readCallIdentityDiagnostic(context,call("0700000001",TelecomManager.PRESENTATION_RESTRICTED),1)
        assertEquals(DiagnosticNumberForm.HIDDEN,event.numberForm)
        assertEquals(-1,event.addressType)
        assertEquals(DiagnosticPresence.UNAVAILABLE,event.contactName)
    }
    @Test fun nativeCallerAndContactNameUpdatesAreVisibleWithoutExportingEitherName() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        val call=call("0700000001")
        val first=readCallIdentityDiagnostic(context,call,1)
        ReflectionHelpers.setField(call.details,"mCallerDisplayName",null)
        ReflectionHelpers.setField(call.details,"mContactDisplayName",null)
        val second=readCallIdentityDiagnostic(context,call,1)
        assertNotEquals(first,second)
        assertEquals(DiagnosticPresence.ABSENT,second.callerName)
        assertEquals(DiagnosticPresence.ABSENT,second.contactName)
        assertEquals(DiagnosticNumberForm.NATIONAL,second.numberForm)
    }
    @Test fun numberShapeAndKnownServiceNeverGuessUnknownPackagesOrShortCodes() {
        assertEquals(DiagnosticNumberForm.INTERNATIONAL_PREFIX,diagnosticNumberForm("0040700000001",true))
        assertEquals(DiagnosticNumberForm.NATIONAL,diagnosticNumberForm("0700 000 001",true))
        for(number in listOf("112","*123#","name@example.test")) assertEquals(DiagnosticNumberForm.OTHER,diagnosticNumberForm(number,true))
        assertEquals(DiagnosticNumberForm.UNKNOWN,diagnosticNumberForm(null,true))
        assertEquals(DiagnosticCallService.WHATSAPP,diagnosticCallService("com.whatsapp"))
        assertEquals(DiagnosticCallService.WHATSAPP_BUSINESS,diagnosticCallService("com.whatsapp.w4b"))
        assertEquals(DiagnosticCallService.OTHER,diagnosticCallService("unknown.connection"))
        assertEquals(DiagnosticCallService.UNKNOWN,diagnosticCallService(null))
    }
    @Test fun bluetoothBroadcastsAreDeduplicatedAndManualStopUnregisters() {
        val events=mutableListOf<DiagnosticDetail>(); var enabled=true
        val monitor=BluetoothDiagnosticMonitor(context,{enabled},events::add)
        val intent=Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED).putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,0)
            .putExtra(BluetoothProfile.EXTRA_STATE,2).putExtra("android.bluetooth.device.extra.NAME","Test Alexandru Butica")
        try {
            monitor.start(); context.sendBroadcast(intent); context.sendBroadcast(intent); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1,events.size)
            assertFalse(DiagnosticDetailFormatter.format(events.single()).contains("Test Alexandru Butica"))
            monitor.close(); context.sendBroadcast(intent); shadowOf(Looper.getMainLooper()).idle(); assertEquals(1,events.size)
            enabled=false; monitor.start(); context.sendBroadcast(intent); shadowOf(Looper.getMainLooper()).idle(); assertEquals(1,events.size)
        } finally { monitor.close() }
        assertNull(bluetoothDiagnosticTransition(Intent("unknown.action")))
        assertEquals(-1,bluetoothDiagnosticTransition(Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED).putExtra(BluetoothProfile.EXTRA_STATE,999))!!.current)
    }
    @Test fun identityProducerDeduplicatesUnchangedMetadataAndKeepsUpdatedSystemName() = kotlinx.coroutines.runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        org.robolectric.util.ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(AppDiagnostics::class.java,"initialized").set(false)
        AppDiagnostics.initialize(context); AppDiagnostics.clear(context); AppDiagnostics.selectLevel(DiagnosticLevel.DEBUG)
        val job=kotlinx.coroutines.SupervisorJob()
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate+job)
        val repository=object : dev.goodwy.rphone.domain.repository.ICallerRepository {
            override suspend fun getContactByNumber(number: String): dev.goodwy.rphone.modal.data.Contact? = null
        }
        val state=dev.goodwy.rphone.data.manager.CallStateManager(dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase(repository),scope)
        val call=call()
        val diagnostics=CallPhotoDiagnostics(context,scope,state,{"call-701"})
        try {
            diagnostics.update(call); diagnostics.update(call)
            val first=AppDiagnostics.report(context)
            assertEquals(1,first.lineSequence().count { it.contains("call_identity | session=call-701") })
            ReflectionHelpers.setField(call.details,"mContactDisplayName",null)
            diagnostics.update(call)
            val second=AppDiagnostics.report(context)
            assertEquals(2,second.lineSequence().count { it.contains("call_identity | session=call-701") })
            assertTrue(second.contains("system_contact_name=ABSENT"))
            AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL); diagnostics.update(call)
            assertEquals(2,AppDiagnostics.report(context).lineSequence().count { it.contains("call_identity | session=call-701") })
        } finally { diagnostics.close(); job.cancel(); AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL) }
    }
    @Test fun byteRetentionKeepsWholeUnicodeRecordsWithinTheCap() {
        val file=File(context.cacheDir,"journal-retention.txt")
        val lines=(1..400).map { "event=$it | marker=Șț😀" }
        file.writeText(lines.joinToString("\n",postfix="\n"),Charsets.UTF_8)
        trimDiagnosticJournal(file,1024)
        assertTrue(file.length()<=512)
        assertTrue(file.readText().endsWith(lines.last()+"\n"))
        assertFalse(file.readText().contains("�"))
        assertTrue(file.readLines().all { it.startsWith("event=") })
        file.writeText("x".repeat(2000)); trimDiagnosticJournal(file,1024); assertEquals(0,file.length())
    }
}
