package dev.goodwy.rphone.recordings

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/** Readiness/disable wins over an older failed check. No timer or persistent background job. */
internal class CaptureReminder {
    private val checks=Mutex()
    private val guard=Any()
    private var revision=0L
    fun clear(action: ()->Unit) = synchronized(guard) {revision++;action()}
    suspend fun reconcile(eligible: ()->Boolean,settingUp: ()->Boolean,ready: suspend ()->Boolean,
        post: ()->Unit,clear: ()->Unit) {
        // Coalesce concurrent resume/update checks instead of extending a broadcast's lifetime.
        if(!checks.tryLock())return
        try {
            val started=synchronized(guard) {revision}
            if(!eligible() || settingUp()) {this.clear(clear);return}
            if(ready()) {this.clear(clear);return}
            delay(300)
            if(ready()) {this.clear(clear);return}
            synchronized(guard) {
                if(revision!=started)return
                if(eligible() && !settingUp())post()else clear()
            }
        }finally {checks.unlock()}
    }
}
