package dev.goodwy.rphone.recordings

import android.content.Context
import android.os.SystemClock
import android.telecom.Call
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.isTelecomConference
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

internal data class CallCaptureState(val callKey: String="",val phase: String="idle",val durationMs: Long=0,val error: Boolean=false) {
    val busy get()=phase in setOf("waiting","starting","recording","saving")
}

/** Explicit privileged capture; readiness never starts audio. The integrated local backend owns the shell worker. */
internal class AdbCallCapture private constructor(context: Context) {
    private val repo=RecordingsRepository.get(context)
    private val store=CallCaptureStore(repo)
    private val backend=IntegratedCallCapture.get(context)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val stateValue=MutableStateFlow(CallCaptureState())
    val state=stateValue.asStateFlow()
    private val stopping=AtomicBoolean(false)
    private var stream: CaptureChannel?=null
    private var operation: Job?=null
    suspend fun available(): Boolean=withContext(Dispatchers.IO) {
        try {BuildConfig.TEST_DISTRIBUTION && repo.integrationEnabled() && backend.available()}catch(error: Exception){
            captureFailures.report(CaptureFailureStage.AVAILABILITY,error)
            false
        }
    }
    fun recover() {
        if(operation?.isActive==true)return
        operation=scope.launch {try {withContext(Dispatchers.IO){store.recover()}}catch(error: Exception){captureFailures.report(CaptureFailureStage.RECOVER,error);stateValue.value=stateValue.value.copy(error=true)}}
    }
    fun start(call: Call,title: String,key: String,transcribe: Boolean) {
        if(!backend.enabled() || operation?.isActive==true || stateValue.value.busy)return
        val generation=backend.generation()
        android.util.Log.i("CallCapture","request state=${call.currentTelecomState()} transcribe=$transcribe")
        stopping.set(false);stateValue.value=CallCaptureState(key,if(call.currentTelecomState()==Call.STATE_ACTIVE)"starting" else "waiting")
        operation=scope.launch {
            var callback: Call.Callback?=null
            var guard: Job?=null
            var failed=false
            var wake: android.os.PowerManager.WakeLock?=null
            try {
                check(BuildConfig.TEST_DISTRIBUTION && repo.integrationEnabled() && backend.enabled())
                repo.awaitReady();withContext(Dispatchers.IO){store.recover()}
                require(repo.captureAllowed(transcribe))
                val calls=GlobalContext.get().get<ICallRepository>()
                val details=call.details
                val identity="${details?.accountHandle}|${details?.handle}|${details?.creationTimeMillis}"
                fun valid(): Boolean=backend.accepts(generation) && captureCallStateAllowed(call.currentTelecomState()) && !call.isTelecomConference() && call.parent==null && call.children.isEmpty() &&
                    calls.allCalls.value.any {it===call} && calls.currentCallSession.value?.call===call &&
                    "${call.details?.accountHandle}|${call.details?.handle}|${call.details?.creationTimeMillis}"==identity &&
                    repo.captureAllowed(transcribe)
                require(valid());val output=if(transcribe)repo.outputLanguage()else ""
                if(output.isNotEmpty())require(repo.captureAllowed(true))
                callback=object:Call.Callback(){override fun onStateChanged(c: Call,s: Int){android.util.Log.i("CallCapture","state=$s");if(!captureCallStateAllowed(s))stop()}
                    override fun onDetailsChanged(c: Call,d: Call.Details){if(!valid())stop()}
                    override fun onChildrenChanged(c: Call,children: MutableList<Call>){if(!valid())stop()}
                    override fun onParentChanged(c: Call,parent: Call?){if(!valid())stop()}
                    override fun onCallDestroyed(c: Call){stop()}}
                call.registerCallback(callback)
                guard=launch {var beat=0;while(isActive){
                    if(!valid())stop()
                    if(beat++%10==0)try{backend.heartbeat()}catch(error: Exception){captureFailures.report(CaptureFailureStage.HEARTBEAT,error);failed=true;stop()}
                    delay(200)
                }}
                wake=repo.context.getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"dot.dialer:call-capture")
                wake.acquire(CallCaptureStore.MAX_BYTES * 1000L / 64000L + 300_000L)
                withContext(Dispatchers.IO) {
                    val number=details?.handle?.takeIf{it.scheme=="tel"}?.schemeSpecificPart.orEmpty()
                    val recordingIdentity=recordingContactIdentity(repo.context,number)
                    if(stopping.get())return@withContext
                    var out: RandomAccessFile?=null
                    var file: File?=null
                    var bytes=0L;var lastSync=SystemClock.elapsedRealtime();var lastSpace=0L
                    try {
                        captureEarlyCall(
                            connected={call.currentTelecomState()==Call.STATE_ACTIVE},valid=::valid,stopped={stopping.get()},
                            open={backend.open("START").also {stream=it}},
                            waiting={android.util.Log.i("CallCapture","waiting_for_connection");stateValue.value=CallCaptureState(key,"waiting",bytes*1000/64000)},
                            frame={buffer ->
                                require(bytes+buffer.size<=CallCaptureStore.MAX_BYTES)
                                if(out==null) {
                                    android.util.Log.i("CallCapture","first_audio state=${call.currentTelecomState()}")
                                    file=store.begin(title,key,transcribe,output,recordingIdentity).second
                                    out=RandomAccessFile(file,"rw").apply {seek(44)}
                                }
                                val target=checkNotNull(out);val now=SystemClock.elapsedRealtime()
                                if(now-lastSpace>=1000){CallCaptureStore.requireStorage(captureWritableBytes(repo.context,checkNotNull(file)));lastSpace=now}
                                target.write(buffer);bytes+=buffer.size
                                CallCaptureStore.writeHeader(target,bytes);target.seek(44+bytes)
                                if(now-lastSync>=5000){target.fd.sync();lastSync=now}
                                stateValue.value=CallCaptureState(key,"recording",bytes*1000/64000)
                            },
                            closeAttempt={stream?.close();stream=null;try{backend.stop()}catch(error: Exception){captureFailures.report(CaptureFailureStage.STOP_ATTEMPT,error,cleanup=true)}}
                        )
                    } finally {
                        out?.let {target->try{CallCaptureStore.writeHeader(target,bytes);target.fd.sync()}finally{target.close()}}
                    }
                }
            }catch(e: CancellationException){throw e}
            catch(error: Exception){captureFailures.report(CaptureFailureStage.CAPTURE,error);failed=failed || !stopping.get()}
            finally {
                try {
                guard?.cancel();callback?.let {call.unregisterCallback(it)}
                withContext(NonCancellable+Dispatchers.IO) {
                    stream?.close();stream=null
                    // Also stop on local storage failure: never leave a server recorder running.
                    try {backend.stop()}catch(error: Exception){captureFailures.report(CaptureFailureStage.STOP_FINAL,error,cleanup=true)}
                    stateValue.value=stateValue.value.copy(phase="saving")
                    try {store.recover()}catch(error: Exception){captureFailures.report(CaptureFailureStage.FINALIZE,error,cleanup=true);failed=true}
                }
                } finally {
                    if(wake?.isHeld==true)wake.release()
                    stateValue.value=stateValue.value.copy(phase="idle",error=failed)
                }
            }
        }
    }
    fun stop() {
        if(!stateValue.value.busy || !stopping.compareAndSet(false,true))return
        scope.launch(Dispatchers.IO){try {backend.stop()}catch(error: Exception){captureFailures.report(CaptureFailureStage.STOP_REQUEST,error,cleanup=true)}finally{stream?.close()}}
    }
    companion object {
        fun stopIfRunning(){instance?.stop()}
        @Volatile private var instance: AdbCallCapture?=null
        fun get(context: Context)=instance ?: synchronized(this){instance ?: AdbCallCapture(context.applicationContext).also {instance=it}}
    }
}
