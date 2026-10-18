package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.telecom.Call
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class)
class QuickActionPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("call_back_reminders_v2", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun failedDiskCommitNeverClaimsSchedulingOrCancellation() {
        val failing = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val real=super.getSharedPreferences(name,mode)
                val type=android.content.SharedPreferences::class.java
                return java.lang.reflect.Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args ->
                    if(method.name=="edit") {
                        val editor=real.edit();val editorType=android.content.SharedPreferences.Editor::class.java
                        java.lang.reflect.Proxy.newProxyInstance(editorType.classLoader,arrayOf(editorType)) { proxy,operation,values ->
                            if(operation.name=="commit") false else {
                                val result=operation.invoke(editor,*(values?:emptyArray()))
                                if(operation.returnType==editorType) proxy else result
                            }
                        }
                    } else method.invoke(real,*(args?:emptyArray()))
                } as android.content.SharedPreferences
            }
        }
        val number="0700000101";val due=System.currentTimeMillis()+900_000
        assertFalse(CallBackReminderScheduler.schedule(failing,number,"Test Alexandru Butica",due))
        assertNull(CallBackReminderScheduler.pending(context,number))
        assertTrue(CallBackReminderScheduler.schedule(context,number,"Test Alexandru Butica",due))
        assertFalse(CallBackReminderScheduler.cancel(failing,number))
        assertNotNull(CallBackReminderScheduler.pending(context,number))
    }

    @Test fun callbackReplacesPersistsRecoversAndCancelsOnlyItsOwnNumber() {
        val now=System.currentTimeMillis()
        assertTrue(CallBackReminderScheduler.schedule(context,"0700000101","Test Alexandru Butica",now+900_000))
        val first=CallBackReminderScheduler.pending(context,"0700000101")!!
        assertTrue(CallBackReminderScheduler.schedule(context,"0700 000 101","Test Alexandru Butica",now+3_600_000))
        val second=CallBackReminderScheduler.pending(context,"0700000101")!!
        assertNotEquals(first.token,second.token);assertEquals(now+3_600_000,second.due)
        assertTrue(CallBackReminderScheduler.schedule(context,"0700000102","Atelier Nord",now+900_000))
        CallBackReminderReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(second,CallBackReminderScheduler.pending(context,"0700000101"))
        assertTrue(CallBackReminderScheduler.cancel(context,"0700000101"))
        assertNull(CallBackReminderScheduler.pending(context,"0700000101"))
        assertNotNull(CallBackReminderScheduler.pending(context,"0700000102"))
        CallBackReminderScheduler.deliver(context,first.number,first.token)
        assertEquals(0,Shadows.shadowOf(context.getSystemService(NotificationManager::class.java)).size())
    }
    @Test fun deniedPermissionPastTimeAndBlockedChannelNeverClaimSuccess() {
        assertFalse(CallBackReminderScheduler.schedule(context,"0700000101","",System.currentTimeMillis()-1))
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(CallBackReminderScheduler.schedule(context,"0700000101","",System.currentTimeMillis()+10_000))
        assertNull(CallBackReminderScheduler.pending(context,"0700000101"))
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("call_back_reminders","Reminders",NotificationManager.IMPORTANCE_NONE))
        assertFalse(CallBackReminderScheduler.schedule(context,"0700000101","",System.currentTimeMillis()+10_000))
    }
    @Test fun notificationUsesDialIntentNeverAutomaticallyCallsAndStaleTokenCannotDeliver() {
        val number="0700000101"
        assertTrue(CallBackReminderScheduler.schedule(context,number,"Test Alexandru Butica",System.currentTimeMillis()+10_000))
        val pending=CallBackReminderScheduler.pending(context,number)!!
        ShadowSystemClock.advanceBy(Duration.ofSeconds(11))
        CallBackReminderScheduler.deliver(context,number,"stale")
        assertEquals(0,Shadows.shadowOf(context.getSystemService(NotificationManager::class.java)).size())
        CallBackReminderScheduler.deliver(context,number,pending.token,pending.due + 1)
        val notifications=Shadows.shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1,notifications.size)
        val intent=Shadows.shadowOf(notifications.single().contentIntent).savedIntent
        assertEquals(Intent.ACTION_DIAL,intent.action)
        assertEquals(number,intent.data?.schemeSpecificPart)
        assertNull(CallBackReminderScheduler.pending(context,number))
        assertNull(Shadows.shadowOf(context).nextStartedActivity)
    }
    @Test fun extensionMetadataIsPerNumberPortableAndUsesNoSuffixGuessing() = runTest {
        val prefs=PreferenceManager(context)
        prefs.restoreAllPreferences(emptyMap(),replacePortable=true)
        PhoneExtensions.replace(prefs,emptyList(),mapOf("+40 700 000 101" to PhoneExtension("204#","Vânzări"),
            "+40700000102" to PhoneExtension("9","Birou")))
        assertEquals("204#",PhoneExtensions.find(prefs,"+40700000101")?.digits)
        assertEquals("204#",PhoneExtensions.find(prefs,"0700000101","RO")?.digits)
        assertNull(PhoneExtensions.find(prefs,"0700000101"))
        assertNull(PhoneExtensions.find(prefs,"+33700000101","RO"))
        val backup = requireNotNull(BackupManager.createBackup(context))
        java.util.zip.ZipFile(backup).use { archive ->
            val json=archive.getInputStream(archive.getEntry("prefs.json")).bufferedReader().readText()
            assertTrue(json.contains(PhoneExtensions.KEY))
        }
        val snapshot=prefs.getPersistedPreferences()
        prefs.restoreAllPreferences(emptyMap(),replacePortable=true)
        assertTrue(BackupManager.restoreBackup(context,backup))
        backup.delete()
        assertEquals(snapshot[PhoneExtensions.KEY],prefs.getPersistedPreferences()[PhoneExtensions.KEY])
        assertEquals("Birou",PhoneExtensions.find(prefs,"+40700000102")?.label)
        PhoneExtensions.replace(prefs,listOf("+40700000101"),emptyMap())
        assertNull(PhoneExtensions.find(prefs,"+40700000101"))
        assertNotNull(PhoneExtensions.find(prefs,"+40700000102"))
    }
    @Test fun invalidExtensionAndFreeGestureCannotBeWritten() = runTest {
        val prefs=PreferenceManager(context,featureAllowed={false})
        prefs.restoreAllPreferences(emptyMap(),replacePortable=true)
        prefs.setBoolean("home_quick_action_gesture",true)
        prefs.getPersistedPreferences()
        assertFalse(prefs.getBoolean("home_quick_action_gesture",false))
        try { PhoneExtensions.replace(PreferenceManager(context),emptyList(),mapOf("0700000101" to PhoneExtension("2p04")));fail() } catch (_:IllegalArgumentException) {}
        assertTrue(PhoneExtensions.read(prefs).isEmpty())
    }
    @Test fun dtmfSendsOnlyManualValidActiveSequenceAndCanRepeat() = runTest {
        val commands=mutableListOf<String>();val call=QuickCallFixture.call(commands=commands)
        val sender=ExtensionDtmfSender(backgroundScope){true};val digits=mutableListOf<Char>()
        assertTrue(commands.isEmpty());assertFalse(sender.send(call,"2p04"))
        assertTrue(sender.send(call,"20#"){digits+=it});assertFalse(sender.send(call,"9"))
        runCurrent();advanceTimeBy(751);runCurrent()
        assertEquals(listOf('2','0','#'),digits);assertFalse(sender.busy.value)
        assertEquals(3,commands.count {it=="playDtmfTone"})
        assertTrue(sender.send(call,"9"));runCurrent();advanceTimeBy(251);runCurrent()
        assertFalse(commands.any {it in setOf("disconnect","hold","unhold","placeCall")})
    }
    @Test fun dtmfHoldEndPlanLossAndTargetChangeStopRemainingDigits() = runTest {
        for (reason in listOf("hold","end","plan","target","handle","dispose")) {
            val commands=mutableListOf<String>();val call=QuickCallFixture.call(commands=commands)
            var allowed=true
            val sender=ExtensionDtmfSender(backgroundScope){allowed};val digits=mutableListOf<Char>()
            assertTrue(sender.send(call,"204"){digits+=it});runCurrent()
            when(reason) {"hold"->QuickCallFixture.state(call,Call.STATE_HOLDING);"end"->QuickCallFixture.state(call,Call.STATE_DISCONNECTED)
                "plan","target"->allowed=false
                "handle" -> {
                    org.robolectric.util.ReflectionHelpers.setField(call.details,"mHandle",android.net.Uri.parse("tel:+40700000102"))
                    org.robolectric.util.ReflectionHelpers.getField<List<Any>>(call,"mCallbackRecords").toList().forEach {
                        org.robolectric.util.ReflectionHelpers.getField<Call.Callback>(it,"mCallback").onDetailsChanged(call,call.details)
                    }
                }
                else->sender.cancel()}
            advanceTimeBy(1000);runCurrent()
            assertEquals(reason,listOf('2'),digits);assertFalse(sender.busy.value)
            assertTrue(commands.contains("stopDtmfTone"))
            if (reason in listOf("hold","end","plan","target")) assertFalse(sender.send(call,"204"))
        }
    }
}
