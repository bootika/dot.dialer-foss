package dev.goodwy.rphone.recordings

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.recordings.CaptureSetupActivity
import kotlinx.coroutines.*

internal object CaptureSetupNotifications {
    private const val CHANNEL="call_capture_setup"
    const val PAIR=9127
    const val RESTART=9128
    private const val REMINDER_CHANNEL="call_capture_reactivation"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,context.getString(R.string.capture_setup),NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun pairing(context: Context): Notification {
        channel(context)
        val tap=PendingIntent.getActivity(context,PAIR,Intent(context,CaptureSetupActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reply=PendingIntent.getBroadcast(context,PAIR,Intent(context,CapturePairingReceiver::class.java).putExtra("capture_generation",IntegratedCallCapture.get(context).generation()),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val input=RemoteInput.Builder("pair_code").setLabel(context.getString(R.string.capture_pair_code)).build()
        return NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(context.getString(R.string.capture_pair_title)).setContentText(context.getString(R.string.capture_pair_notification))
            .setContentIntent(tap).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(NotificationCompat.Action.Builder(0,context.getString(R.string.capture_enter_code),reply).addRemoteInput(input).build()).build()
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val reminder=CaptureReminder()
    private fun clear(context: Context) {
        val manager=context.getSystemService(NotificationManager::class.java)
        if(manager.activeNotifications.any {it.id==RESTART})manager.cancel(RESTART)
    }
    /** Also called on app resume; a CAPS check never creates an audio recorder. */
    fun check(context: Context) {scope.launch {reconcile(context.applicationContext)}}
    suspend fun reconcile(context: Context) {
        val backend=IntegratedCallCapture.get(context)
        // Replace the old audible/lock-screen reminder after upgrading; pairing stays unchanged.
        val manager=context.getSystemService(NotificationManager::class.java)
        if(manager.activeNotifications.any {it.id==RESTART && it.notification.channelId==CHANNEL})manager.cancel(RESTART)
        reminder.reconcile(
            eligible={BuildConfig.DEBUG && BuildConfig.DICTATE_INTEGRATION_ENABLED && backend.enabled() && backend.previouslyActivated()},
            settingUp={backend.state.value.phase in setOf("pairing","paired","starting","finishing")},
            ready={backend.available()},
            post={post(context)},clear={clear(context)})
    }
    private fun post(context: Context) {
        val delivery=CaptureReminderDelivery(context.getSharedPreferences("capture_reminder_delivery",Context.MODE_PRIVATE))
        delivery.postOnce(IntegratedCallCapture.get(context).reminderSession()) {
            if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return@postOnce false
            try {NotificationManagerCompat.from(context).notify(RESTART,reactivationNotification(context));true}
            catch(_: SecurityException){false}
        }
    }
    internal fun reactivationNotification(context: Context): Notification {
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL,context.getString(R.string.capture_reactivate),NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility=Notification.VISIBILITY_SECRET
            setSound(null,null);enableVibration(false)
        })
        val tap=PendingIntent.getActivity(context,RESTART,Intent(context,CaptureSetupActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context,REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(context.getString(R.string.capture_reactivate)).setContentText(context.getString(R.string.capture_reactivate_notification))
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET).setSilent(true).build()
    }
    fun cancel(context: Context){reminder.clear {clear(context)}}
}

/** Exists only while the user performs pairing in Android Settings, bounded to three minutes. */
class CapturePairingService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(!BuildConfig.DEBUG || !BuildConfig.DICTATE_INTEGRATION_ENABLED){stopSelf();return START_NOT_STICKY}
        val backend=IntegratedCallCapture.get(this)
        if(!backend.accepts(intent?.getLongExtra("capture_generation",-1) ?: -1)){stopSelf();return START_NOT_STICKY}
        startForeground(CaptureSetupNotifications.PAIR,CaptureSetupNotifications.pairing(this))
        IntegratedCallCapture.get(this).discover()
        intent?.getStringExtra("pair_code")?.let {code ->scope.launch {
            try {val backend=IntegratedCallCapture.get(this@CapturePairingService)
                val ticket=intent.getLongExtra("capture_generation",-1)
                backend.pair(code,ticket=ticket)
                if(backend.state.value.phase=="paired" && backend.accepts(ticket))backend.activate(ticket=ticket)
            }finally{stopSelf()}
        }}
        scope.launch{delay(180000);stopSelf()}
        return START_NOT_STICKY
    }
    override fun onDestroy(){IntegratedCallCapture.get(this).endDiscovery();scope.cancel();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}

class CapturePairingReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(!BuildConfig.DEBUG || !BuildConfig.DICTATE_INTEGRATION_ENABLED)return
        if(!IntegratedCallCapture.get(context).accepts(intent.getLongExtra("capture_generation",-1)))return
        val code=RemoteInput.getResultsFromIntent(intent)?.getCharSequence("pair_code")?.toString()?.trim() ?: return
        if(code.matches(Regex("[0-9]{6}")))context.startForegroundService(Intent(context,CapturePairingService::class.java).putExtra("pair_code",code).putExtra("capture_generation",intent.getLongExtra("capture_generation",-1)))
    }
}

class CaptureRestartReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED))return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {CaptureSetupNotifications.reconcile(context.applicationContext)}finally {pending.finish()}
        }
    }
}
