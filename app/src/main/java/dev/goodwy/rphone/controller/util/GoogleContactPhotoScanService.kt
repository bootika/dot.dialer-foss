package dev.goodwy.rphone.controller.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.goodwy.rphone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** A manually requested data transfer; never launched on boot or by a recurring scheduler. */
class GoogleContactPhotoScanService : Service() {
    private val scan: GoogleContactPhotoScan by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            scan.cancel()
            if (job == null) stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.photo_scan_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            })
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(scan.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            // CPU only, bounded: never changes screen timeout or keeps the display illuminated.
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:contact-photos").apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1_000L)
            }
            job = scope.launch(Dispatchers.IO) { scan.executeFromService(intent?.getBooleanExtra(RETRY_ONLY, false) == true) }
            observer?.cancel()
            observer = scope.launch {
                scan.state.collect { state ->
                    if (state.running) {
                        manager.notify(NOTIFICATION_ID, notification(state))
                    } else {
                        ServiceCompat.stopForeground(this@GoogleContactPhotoScanService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        } catch (error: Exception) {
            scan.serviceStartFailed()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(state: PhotoScanState): Notification {
        val cancel = PendingIntent.getService(this, NOTIFICATION_ID,
            Intent(this, GoogleContactPhotoScanService::class.java).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, NOTIFICATION_ID, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dot_dialer_notification)
            .setContentTitle(getString(R.string.photo_scan_notification_title))
            .setContentText(getString(if (state.stopping) R.string.contact_photo_scan_stopping else R.string.contact_photo_scan_progress, state.checked, state.total))
            .setProgress(state.total, state.checked, state.total == 0)
            .setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(android.R.string.cancel), cancel)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        scan.serviceInterrupted()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (scan.state.value.running) scan.serviceInterrupted()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        internal const val RETRY_ONLY = "retry_failed_photos"
        private const val CANCEL = "cancel_photo_scan"
        private const val CHANNEL = "google_contact_photo_scan"
        internal const val NOTIFICATION_ID = 7421
    }
}
