package dev.goodwy.rphone.controller.util

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

/** Deferrable daily maintenance; never a foreground service or result notification. */
class ContactsOptimizerJob : JobService() {
    private val processor: WhatsAppAvatarSyncProcessor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            var retry = false
            var more = false
            try { more = withTimeout(50_000) { UnsavedNumberAvatars.pruneExpired(this@ContactsOptimizerJob); processor.runAutomaticPass() } }
            catch (_: TimeoutCancellationException) { retry = true }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.w("ContactsOptimizer", "Job retry: " + error.javaClass.simpleName)
                retry = true
            }
            withContext(Dispatchers.Main) {
                jobFinished(params, retry)
                if (more) continuation(this@ContactsOptimizerJob)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        const val ID = 84105
        private fun continuation(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.schedule(
                JobInfo.Builder(ID + 1, ComponentName(context, ContactsOptimizerJob::class.java))
                    .setMinimumLatency(15 * 60 * 1000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresBatteryNotLow(true).setPersisted(true).build())
        }
        internal fun schedule(context: Context) {
            ContactOptimizationNotifications.cancelLegacy(context)
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(ID) != null) return
            scheduler.schedule(JobInfo.Builder(ID, ComponentName(context, ContactsOptimizerJob::class.java))
                .setPeriodic(24 * 60 * 60 * 1000L, 6 * 60 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresBatteryNotLow(true)
                .setPersisted(true).build())
        }
    }
}
