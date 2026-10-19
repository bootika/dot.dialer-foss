package dev.goodwy.rphone.recordings

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.debug.DictateBridgeLabActivity
import java.io.File
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real APK pairing/capabilities. No licence/API fixtures or provider uploads. */
@RunWith(AndroidJUnit4::class)
class DictateBridgeDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    @Before fun optionalIntegration() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("dictateBridgeIntegration")=="true")
        instrumentation.targetContext.getSharedPreferences("dictate_pairing",0).edit().clear().commit()
        val ui=instrumentation.uiAutomation
        val info=ui.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.serviceInfo=info
    }
    private fun pair(approve: Boolean): JSONObject {
        val intent=Intent(instrumentation.targetContext,DictateBridgeLabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity=instrumentation.startActivitySync(intent) as DictateBridgeLabActivity
        try {
            val until=SystemClock.uptimeMillis()+20_000
            var clicked=false
            while(SystemClock.uptimeMillis()<until && !clicked) {
                val root=instrumentation.uiAutomation.rootInActiveWindow
                val id=if(approve)"android:id/button1" else "android:id/button2"
                val button=root?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull {it.packageName?.toString()==DictateProtocol.PACKAGE}
                if(button!=null)clicked=button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if(!clicked)SystemClock.sleep(100)
            }
            assertTrue("Actual Dictate consent dialog was not shown",clicked)
            val ready=SystemClock.uptimeMillis()+30_000
            while(activity.completion==null && SystemClock.uptimeMillis()<ready)SystemClock.sleep(100)
            val result=JSONObject(requireNotNull(activity.completion))
            File(instrumentation.targetContext.filesDir,"dictate-bridge-${if(approve)"approved" else "denied"}.json").writeText(result.toString(2))
            return result
        } finally {instrumentation.runOnMainSync {activity.finish()}}
    }
    @Test fun realAndroidFileProviderHasOnlyNarrowRecordingRoots() {
        val context=instrumentation.targetContext
        val id=java.util.UUID.randomUUID().toString()
        val file=File(context.filesDir,"recordings/audio/$id.m4a").apply {parentFile?.mkdirs();writeBytes(byteArrayOf(1,2,3))}
        try {
            val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.provider",file)
            assertEquals("content",uri.scheme)
            assertArrayEquals(byteArrayOf(1,2,3),context.contentResolver.openInputStream(uri)!!.use {it.readBytes()})
            context.grantUriPermission(DictateProtocol.PACKAGE,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertThrows(IllegalArgumentException::class.java) {
                androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.provider",File(context.filesDir,"outside-recording.txt"))
            }
        } finally {file.delete()}
    }
    @Test fun realAudioImportPreservesBytesAndDetectsCodec() {
        val context=instrumentation.targetContext;val files=RecordingFiles(context)
        val id=java.util.UUID.randomUUID().toString()
        val source=File(context.filesDir,"recordings/bridge/$id.wav").apply {parentFile?.mkdirs()}
        val buffer=java.nio.ByteBuffer.allocate(32044).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(32036).put("WAVEfmt ".toByteArray()).putInt(16)
        buffer.putShort(1.toShort()).putShort(1.toShort()).putInt(16000).putInt(32000).putShort(2.toShort()).putShort(16.toShort())
        buffer.put("data".toByteArray()).putInt(32000)
        source.writeBytes(buffer.array())
        try {
            val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.provider",source)
            val asset=files.import(uri,"Test Alexandru Butica","call-check",8388608)
            try {
                assertEquals("call-check",asset.callKey);assertTrue(asset.durationMs in 900..1100)
                assertTrue(asset.codecMime.startsWith("audio/"));assertArrayEquals(source.readBytes(),files.audio(asset).readBytes())
            } finally {files.audio(asset).delete()}
        } finally {source.delete()}
    }
    @Test fun deniedPairingNeverPinsTheProvider() {
        val result=pair(false)
        assertFalse(result.getBoolean("approved"));assertFalse(result.getBoolean("trusted"))
    }
    @Test fun approvedPairingExchangesCapabilitiesAcrossActualApks() {
        val result=pair(true)
        assertFalse(result.has("error"))
        assertTrue(result.getBoolean("approved"));assertTrue(result.getBoolean("trusted"));assertTrue(result.getBoolean("connected"))
        assertFalse(result.getBoolean("supportsCallCapture"))
        assertTrue(result.getLong("maxInputBytes")>0);assertTrue(result.getLong("maxInputSeconds")>0)
        assertTrue(result.getString("licenseState") in setOf("valid","required","unavailable"))
    }
}
