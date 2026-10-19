package dev.goodwy.rphone.recordings

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import kotlinx.coroutines.*

/** Android owns retries/process recovery; the call UI never waits for an AI job. */
class RecordingSyncJob : JobService() {
    private var work: Job?=null
    override fun onStartJob(params: JobParameters): Boolean {
        work=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            val retry=try {
                if(!getSystemService(UserManager::class.java).isUserUnlocked)true
                else RecordingSyncEngine.synchronize(RecordingsRepository.get(this@RecordingSyncJob))
            } catch(error: CancellationException) {throw error} catch(error: Exception) {RecordingDiagnostics.failure("RecordingSyncJob",error);true}
            withContext(Dispatchers.Main) {jobFinished(params,retry)}
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {work?.cancel();work=null;return true}
    override fun onDestroy() {work?.cancel();super.onDestroy()}
    companion object {
        private const val ID=0x524543
        fun schedule(context: Context) {
            val job=JobInfo.Builder(ID,ComponentName(context,RecordingSyncJob::class.java))
                .setPersisted(true).setMinimumLatency(1_000)
                .setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_LINEAR).build()
            check(context.getSystemService(JobScheduler::class.java).schedule(job)==JobScheduler.RESULT_SUCCESS)
        }
    }
}
