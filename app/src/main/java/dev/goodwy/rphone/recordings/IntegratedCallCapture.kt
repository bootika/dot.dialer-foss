package dev.goodwy.rphone.recordings

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit

internal data class CaptureSetupState(val phase: String="idle",val pairingPort: Int=0,val connectPort: Int=0,val error: String="")

/** Local activation only. No terminal, network ADB target, downloaded code or automatic recording. */
internal class IntegratedCallCapture private constructor(private val context: android.app.Application) {
    private val root=File(context.noBackupFilesDir,"call-capture").apply{mkdirs()}
    private val config=File(root,"session.json")
    private val optIn=CaptureOptIn(root)
    val optedIn=optIn.state
    private val wirelessPendingFile=File(root,"wireless-off-pending")
    private val wirelessPending=MutableStateFlow(wirelessPendingFile.exists() || (!enabled() && File(root,"feature-enabled").exists() && runCatching {Settings.Global.getInt(context.contentResolver,"adb_wifi_enabled",-1)==1}.getOrDefault(false)))
    val wirelessShutdownPending=wirelessPending.asStateFlow()
    fun enable(){optIn.set(true);wirelessPendingFile.delete();wirelessPending.value=false}
    fun refreshWirelessOffStatus(){
        if(enabled() || !wirelessPending.value && !File(root,"feature-enabled").exists())return
        val status=runCatching {Settings.Global.getInt(context.contentResolver,"adb_wifi_enabled",-1)}.getOrDefault(-1)
        if(status==0){wirelessPendingFile.delete();wirelessPending.value=false}
        else if(status==1){if(!wirelessPendingFile.exists())wirelessPendingFile.writeText("1");wirelessPending.value=true}
    }
    private fun wirelessOffKnown()=runCatching {Settings.Global.getInt(context.contentResolver,"adb_wifi_enabled",-1)==0}.getOrDefault(false)
    fun generation()=optIn.ticket()
    fun accepts(ticket: Long)=optIn.accepts(ticket)
    private val lock=Mutex()
    private val mutable=MutableStateFlow(CaptureSetupState())
    val state=mutable.asStateFlow()
    private var pairingDiscovery: AdbMdns?=null
    private var connectDiscovery: AdbMdns?=null
    private fun boot()=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
    fun enabled()=optedIn.value
    fun open(command: String): CallCaptureConnection {
        check(command !in setOf("START","CAPS","BEAT") || enabled())
        return CallCaptureConnection(config,command,boot())
    }
    private suspend fun probe(): Boolean=withContext(Dispatchers.IO){
        if(!enabled())return@withContext false
        val ready=try {open("CAPS").use{it.status()=="READY|2|16000|2|0"}}catch(error: Exception){captureFailures.report(CaptureFailureStage.PROBE,error);false}
        if(ready)CaptureSetupNotifications.cancel(context)
        ready
    }
    suspend fun available(): Boolean=wirelessOffKnown() && probe()
    fun previouslyActivated()=File(root,"enabled").exists()
    internal fun reminderSession()="${boot()}:${config.lastModified()}"
    private suspend fun finishActivation(ticket: Long) = withContext(NonCancellable+Dispatchers.IO) {
        check(optIn.accepts(ticket))
        mutable.update{it.copy(phase="finishing",error="")}
        endDiscovery()
        context.stopService(android.content.Intent(context,CapturePairingService::class.java))
        context.getSystemService(android.app.NotificationManager::class.java).cancel(CaptureSetupNotifications.PAIR)
        val stopped=wirelessOffKnown() || try {open("QUIET").use{it.status()=="WIFI_OFF"}}catch(error: Exception){captureFailures.report(CaptureFailureStage.QUIET,error,cleanup=true);false}
        if(!stopped || !wirelessOffKnown()) {
            mutable.update{it.copy(phase="idle",error="wireless")}
            return@withContext
        }
        // Recheck after adbd shutdown, not just before requesting it.
        delay(700)
        check(optIn.accepts(ticket))
        check(probe())
        File(root,"enabled").writeText("1")
        CaptureSetupNotifications.cancel(context)
        mutable.update{it.copy(phase="ready",error="")}
    }
    suspend fun heartbeat()=withContext(Dispatchers.IO){open("BEAT").use{check(it.status()=="OK")}}
    suspend fun stop()=withContext(Dispatchers.IO){open("STOP").use{it.status()};Unit}
    @Synchronized fun discover() {
        val ticket=optIn.ticket()
        if(!enabled() || Build.VERSION.SDK_INT<30)return
        if(Build.VERSION.SDK_INT>=37 && androidx.core.content.ContextCompat.checkSelfPermission(context,"android.permission.ACCESS_LOCAL_NETWORK")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        if(pairingDiscovery==null)pairingDiscovery=AdbMdns(context,AdbMdns.SERVICE_TYPE_TLS_PAIRING){_,port ->if(optIn.accepts(ticket))mutable.update{it.copy(pairingPort=port.coerceAtLeast(0))}}.also{it.start()}
        if(connectDiscovery==null)connectDiscovery=AdbMdns(context,AdbMdns.SERVICE_TYPE_TLS_CONNECT){_,port ->if(optIn.accepts(ticket))mutable.update{it.copy(connectPort=port.coerceAtLeast(0))}}.also{it.start()}
    }
    @Synchronized fun endDiscovery(){pairingDiscovery?.stop();connectDiscovery?.stop();pairingDiscovery=null;connectDiscovery=null}
    private fun manager(pairing: Boolean=false): AbsAdbConnectionManager {
        val identity=CaptureIdentity(context)
        CaptureAdbTrustPolicy.configure(File(root,"adb-peer.pin"),boot(),pairing)
        return object: AbsAdbConnectionManager() {
            override fun getPrivateKey()=identity.privateKey
            override fun getCertificate()=identity.certificate
            override fun getDeviceName()="dot.dialer"
        }.apply{setApi(Build.VERSION.SDK_INT);setHostAddress("127.0.0.1");setTimeout(8,TimeUnit.SECONDS)}
    }
    private fun setupFailure(stage: CaptureFailureStage,error: Exception,code: String) {
        mutable.update{it.copy(phase="idle",error=if(error is CancellationException)"" else if(CaptureAdbTrustPolicy.identityChanged(error))"tls_identity" else code)}
        captureFailures.report(stage,error)
    }
    suspend fun pair(code: String,port: Int=state.value.pairingPort,ticket: Long=optIn.ticket())=lock.withLock {
        if(!optIn.accepts(ticket))return@withLock
        withContext(Dispatchers.IO) {
            if(!CaptureSetupRules.validPairing(code,port)){mutable.update{it.copy(error="code")};return@withContext}
            mutable.update{it.copy(phase="pairing",error="")}
            try {check(manager(pairing=true).pair("127.0.0.1",port,code));check(optIn.accepts(ticket));CaptureAdbTrustPolicy.pairingSucceeded(File(root,"adb-peer.pin"));mutable.update{it.copy(phase="paired",pairingPort=0,error="")}}
            catch(error: Exception){setupFailure(CaptureFailureStage.PAIR,error,"pair")}
        }
    }
    suspend fun activate(portOverride: Int=0,ticket: Long=optIn.ticket())=lock.withLock {
        if(!optIn.accepts(ticket))return@withLock
        if(probe()){try{finishActivation(ticket)}catch(error: Exception){setupFailure(CaptureFailureStage.FINISH,error,"start")};return@withLock}
        // Only bootstrap needs Wi-Fi. An already running local helper can finalize without it.
        @Suppress("DEPRECATION")
        val wifi=runCatching {
            val connectivity=context.getSystemService(android.net.ConnectivityManager::class.java)
            connectivity.allNetworks.any {connectivity.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)==true}
        }.getOrDefault(false)
        val wireless=runCatching {Settings.Global.getInt(context.contentResolver,"adb_wifi_enabled",-1)==1}.getOrDefault(false)
        if(!wifi || !wireless){mutable.update{it.copy(phase="idle",error="network")};return@withLock}
        mutable.update{it.copy(phase="starting",error="")}
        withContext(Dispatchers.IO) {
            var adb: AbsAdbConnectionManager?=null
            var launch: io.github.muntashirakon.adb.AdbStream?=null
            var pairFile=""
            var stage=CaptureFailureStage.DISCOVERY
            try {
                check(Build.VERSION.SDK_INT>=30)
                discover()
                val port=if(portOverride in 1..65535)portOverride else captureSetupStep(12000){state.first{it.connectPort in 1..65535}.connectPort}
                stage=CaptureFailureStage.CONNECT;adb=manager();check(adb.connect("127.0.0.1",port))
                val token=Base64.getEncoder().encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)})
                val helperPort=SecureRandom().nextInt(28001)+32000
                pairFile="/data/local/tmp/dot-integrated-pair-${UUID.randomUUID().toString().replace("-","")}"
                val apk=context.applicationInfo.sourceDir
                require(apk.startsWith("/data/app/") && apk.endsWith(".apk") && !apk.contains("'") && !apk.contains('\n'))
                // Deliver the secret through stdin: never expose it in a shell command line or process listing.
                val command="umask 077; head -c 44 > '$pairFile'; CLASSPATH='$apk' nohup setsid -d app_process / dev.goodwy.rphone.recordings.IntegratedCaptureServer $helperPort '$pairFile' '$apk' </dev/null >'$pairFile.log' 2>&1"
                // The stream contains only a fixed bootstrap command; it is never exposed to UI or external intents.
                check(optIn.accepts(ticket))
                stage=CaptureFailureStage.BOOTSTRAP;launch=adb.openStream("shell:$command")
                launch.openOutputStream().apply{write(token.toByteArray(Charsets.US_ASCII));flush()}
                stage=CaptureFailureStage.SESSION;val data=JSONObject().put("backend","integrated").put("port",helperPort).put("token",token).put("bootCount",boot())
                val atomic=AtomicFile(config);val out=atomic.startWrite()
                try{out.write(data.toString().toByteArray());atomic.finishWrite(out)}catch(e: Exception){atomic.failWrite(out);throw e}
                check(optIn.accepts(ticket))
                stage=CaptureFailureStage.HANDSHAKE;captureSetupStep(10000){while(!probe()){check(optIn.accepts(ticket));delay(150)};true}
                check(optIn.accepts(ticket))
                mutable.update{it.copy(phase="finishing",error="")}
            }catch(error: Exception){setupFailure(stage,error,"start")}
            finally{
                try{launch?.close()}catch(error: Exception){captureFailures.report(CaptureFailureStage.CLOSE_LAUNCH,error,cleanup=true)}
                if(pairFile.matches(Regex("/data/local/tmp/dot-integrated-pair-[a-f0-9]{32}")))try {
                    adb?.openStream("shell:rm -f '$pairFile' '$pairFile.log'; printf OK")?.let {cleanup ->
                        val deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                        try{deadline.schedule({try{cleanup.close()}catch(error: Exception){captureFailures.report(CaptureFailureStage.CLOSE_TIMEOUT,error,cleanup=true)}},3,TimeUnit.SECONDS);cleanup.openInputStream().read(ByteArray(2))}
                        finally{cleanup.close();deadline.shutdownNow()}
                    }
                }catch(error: Exception){captureFailures.report(CaptureFailureStage.CLEAN_PAIR,error,cleanup=true)}
                try{adb?.disconnect()}catch(error: Exception){captureFailures.report(CaptureFailureStage.DISCONNECT,error,cleanup=true)}
                endDiscovery()
            }
            if(mutable.value.phase=="finishing" && optIn.accepts(ticket))try{finishActivation(ticket)}catch(error: Exception){setupFailure(CaptureFailureStage.FINISH,error,"start")}
        }
    }
    suspend fun disable(stopWireless: Boolean=false)=withContext(NonCancellable) {
        // Persist Off before waiting for an in-flight activation/pairing operation.
        optIn.set(false)
        CaptureSetupNotifications.cancel(context)
        RecordingSyncJob.cancel(context)
        context.stopService(android.content.Intent(context,CapturePairingService::class.java))
        context.getSystemService(android.app.NotificationManager::class.java).cancel(CaptureSetupNotifications.PAIR)
        AdbCallCapture.stopIfRunning()
        endDiscovery()
        lock.withLock {
            withContext(Dispatchers.IO){
                var quiet=wirelessOffKnown()
                if(stopWireless && !quiet)quiet=try{open("QUIET_QUIT").use{it.status()=="STOPPED|WIFI_OFF"}}catch(error: Exception){captureFailures.report(CaptureFailureStage.QUIET_QUIT,error,cleanup=true);false}
                try{open("QUIT").use{it.status()}}catch(error: Exception){captureFailures.report(CaptureFailureStage.QUIT,error,cleanup=true)}
                if(stopWireless){
                    val pending=!quiet && !wirelessOffKnown()
                    if(pending)wirelessPendingFile.writeText("1")else wirelessPendingFile.delete()
                    wirelessPending.value=pending
                }
                config.delete();File(root,"enabled").delete()}
            mutable.update{CaptureSetupState()}
        }
    }
    companion object {
        @Volatile private var instance: IntegratedCallCapture?=null
        fun get(context: Context)=instance ?: synchronized(this){instance ?: IntegratedCallCapture(context.applicationContext as android.app.Application).also{instance=it}}
    }
}

internal object CaptureSetupRules {
    fun validPairing(code: String,port: Int)=code.matches(Regex("[0-9]{6}")) && port in 1..65535
}
