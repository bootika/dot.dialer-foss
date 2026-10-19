package dev.goodwy.rphone.view.screen.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.RecordingJob
import io.github.bootika.dotdialer.core.recordings.RecordingPolicy
import java.util.Locale

@Composable
internal fun RecordingLanguageDialog(codes: List<String>,onDismiss: ()->Unit,choose: (String)->Unit) {
    val display=LocalConfiguration.current.locales[0]
    val languages=remember(display,codes) {codes.map {it to Locale.forLanguageTag(it).getDisplayName(display)}.sortedBy {it.second}}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.recordings_language))},text={
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)) {items(languages,key={it.first}) {language ->
            TextButton(onClick={choose(language.first)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(language.second,style=MaterialTheme.typography.bodyLarge)}
        }}
    },confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.cancel))}})
}
@Composable
internal fun RecordingJobStatus(job: RecordingJob) {
    val label=when(job.state) {
        "queued"->R.string.recordings_job_queued;"running"->R.string.recordings_job_running
        "completed"->R.string.recordings_job_completed;"cancelled"->R.string.recordings_job_cancelled
        "cancelling"->R.string.recordings_job_cancelling;"blocked"->R.string.recordings_job_blocked
        else->R.string.recordings_job_failed
    }
    Text(stringResource(label),style=MaterialTheme.typography.bodyMedium)
    RecordingPolicy.progress(job.completedParts,job.totalParts)?.let {Text(pluralStringResource(R.plurals.recordings_parts,it.second,it.first,it.second),style=MaterialTheme.typography.labelLarge)}
}
