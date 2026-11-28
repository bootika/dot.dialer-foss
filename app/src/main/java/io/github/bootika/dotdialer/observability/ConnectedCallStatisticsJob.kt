package io.github.bootika.dotdialer.observability

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class ConnectedCallStatisticsJob : JobService() {
    private val preferences: PreferenceManager by inject()
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.IO).launch {
            val retry = ProductStatistics.get(this@ConnectedCallStatisticsJob, preferences).retryConnectedCalls()
            withContext(Dispatchers.Main) { jobFinished(params, retry) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        work = null
        return true
    }

    override fun onDestroy() {
        work?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x444453
        private const val RETRY_DELAY_MILLIS = 30_000L

        fun schedule(context: Context): Boolean {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ConnectedCallStatisticsJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(RETRY_DELAY_MILLIS)
                .setBackoffCriteria(RETRY_DELAY_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build()
            val scheduled = runCatching {
                context.getSystemService(JobScheduler::class.java)?.schedule(job) == JobScheduler.RESULT_SUCCESS
            }.getOrDefault(false)
            if (!scheduled) Log.w("DotStatistics", "Unable to schedule connected-call statistics retry")
            return scheduled
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
