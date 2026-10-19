package dev.goodwy.rphone.recordings

import android.content.*
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.*
import io.github.bootika.dotdialer.core.recordings.DictateCapabilities
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicit, package-specific pairing. Never accepts a signature supplied in Intent extras. */
internal class DictateBridgeClient(private val context: Context) : DictateBridge {
    private val pairing = context.getSharedPreferences("dictate_pairing", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(DictateCapabilities())
    override val capabilities = mutable.asStateFlow()
    private val pending = ConcurrentHashMap<String, Pair<Int, CompletableDeferred<Bundle>>>()
    private val lock = Mutex()
    private var remote: Messenger? = null
    private var connection: ServiceConnection? = null
    private var setupCertificate: String? = null
    private val idleHandler=Handler(Looper.getMainLooper())
    private val idleClose=Runnable {
        if(pending.isEmpty()) {
            connection?.let {try {context.unbindService(it)}catch(error: IllegalArgumentException) {RecordingDiagnostics.failure("idle_unbind",error)}}
            connection=null;remote=null
        }
    }
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if(!trusted() || message.sendingUid!=peerUid()) {
            RecordingDiagnostics.failure("untrusted_reply",SecurityException())
            return@Handler true
        }
        try {
            val data=message.data
            val item=pending[data.getString("requestId")]
            if(item!=null) {
                if(message.what!=item.first+100 || data.getInt("protocolVersion")!=DictateProtocol.VERSION)
                    item.second.completeExceptionally(DictateFailure("INVALID_REPLY"))
                else item.second.complete(data)
            }
        } catch(error: Exception) {
            RecordingDiagnostics.failure("invalid_reply",error)
            pending.values.forEach {it.second.completeExceptionally(DictateFailure("INVALID_REPLY"))}
            pending.clear()
        }
        true
    })
    override fun installed(): Boolean = certificate() != null
    override fun setupIntent(): Intent {
        setupCertificate = certificate() ?: throw DictateFailure("NOT_INSTALLED")
        return Intent().setComponent(ComponentName(DictateProtocol.PACKAGE, DictateProtocol.SETUP))
    }
    override suspend fun approveSetup() {
        val expected = setupCertificate ?: throw DictateFailure("PAIR_REQUIRED")
        setupCertificate = null
        if (certificate() != expected) throw DictateFailure("CERTIFICATE_CHANGED")
        persistPairing(expected)
        refresh()
    }
    // KTX edit(commit=true) discards the Boolean required to verify durable pairing/revocation.
    @SuppressLint("UseKtx")
    private fun persistPairing(certificate: String?) {
        val editor=pairing.edit()
        if(certificate==null)editor.clear()else editor.putString("certificate",certificate)
        check(editor.commit())
    }
    private fun certificate(): String? = try {
        val info = context.packageManager.getPackageInfo(DictateProtocol.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = info.signingInfo?.apkContentsSigners ?: emptyArray()
        if (signatures.size != 1) null else MessageDigest.getInstance("SHA-256")
            .digest(signatures.single().toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (_: PackageManager.NameNotFoundException) { null }
    private fun peerUid(): Int = try { context.packageManager.getApplicationInfo(DictateProtocol.PACKAGE, 0).uid }
        catch (_: PackageManager.NameNotFoundException) { -1 }
    override fun trusted(): Boolean = certificate()?.let { it == pairing.getString("certificate", null) } == true
    private suspend fun bind(): Messenger = lock.withLock {
        idleHandler.removeCallbacks(idleClose)
        if (!trusted()) throw DictateFailure("PAIR_REQUIRED")
        remote?.let { return@withLock it }
        withContext(Dispatchers.Main.immediate) {
            val ready = CompletableDeferred<Messenger>()
            val link = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (!trusted() || name.packageName != DictateProtocol.PACKAGE) ready.completeExceptionally(DictateFailure("UNTRUSTED_SERVICE"))
                    else Messenger(binder).also { remote = it; ready.complete(it) }
                }
                override fun onServiceDisconnected(name: ComponentName) { closeBinding() }
                override fun onBindingDied(name: ComponentName) { closeBinding() }
                override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(DictateFailure("UNSUPPORTED")) }
            }
            connection = link
            try {
                val intent = Intent(DictateProtocol.ACTION).setComponent(ComponentName(DictateProtocol.PACKAGE, DictateProtocol.SERVICE))
                if (!context.bindService(intent, link, Context.BIND_AUTO_CREATE)) throw DictateFailure("UNSUPPORTED")
                withTimeout(10_000) { ready.await() }
            } catch (error: Exception) { closeBinding(); throw error }
        }
    }
    private fun lost() {
        remote = null; mutable.value = DictateCapabilities()
        pending.values.forEach { it.second.completeExceptionally(DictateFailure("DISCONNECTED")) }
        pending.clear()
    }
    private fun closeBinding() {
        connection?.let { try { context.unbindService(it) } catch (error: IllegalArgumentException) { RecordingDiagnostics.failure("unbind",error) } }
        connection = null; lost()
    }
    override suspend fun disconnect() = withContext(Dispatchers.Main.immediate) {
        persistPairing(null); closeBinding()
    }
    override suspend fun request(what: Int, extras: Bundle): Bundle {
        val messenger = bind()
        if (!trusted()) throw DictateFailure("CERTIFICATE_CHANGED")
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<Bundle>()
        pending[id] = what to result
        try {
            val body = Bundle(extras).apply { putInt("protocolVersion", 1); putString("requestId", id) }
            try {messenger.send(Message.obtain(null, what).apply { data = body; replyTo = receiver })}
            catch(error: RemoteException) {RecordingDiagnostics.failure("send_disconnected",error);lost();throw DictateFailure("DISCONNECTED")}
            val reply = withTimeout(15_000) { result.await() }
            if (!reply.getBoolean("ok")) throw DictateFailure(reply.getString("errorCode")?.uppercase(java.util.Locale.ROOT) ?: "PROVIDER_ERROR")
            return reply
        } finally { pending.remove(id);idleHandler.postDelayed(idleClose,30_000) }
    }
    override suspend fun refresh(): DictateCapabilities {
        try {
            val reply = request(DictateProtocol.CAPABILITIES)
            val license = reply.getString("licenseState") ?: "unavailable"
            if (license !in setOf("valid", "required", "unavailable")) throw DictateFailure("INVALID_CAPABILITIES")
            return DictateCapabilities(true, license, reply.getBoolean("apiKeyConfigured"),
                reply.getBoolean("supportsTranscription"), reply.getBoolean("supportsTranslation"),
                reply.getLong("maxInputBytes"), reply.getLong("maxInputSeconds"), reply.getStringArrayList("targetLanguages").orEmpty().filter {it.matches(Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})*"))}.distinct(),reply.getLong("maxChunkBytes"),reply.getInt("maxChunkSeconds").toLong(),reply.getStringArrayList("segmentedAudioMimeTypes").orEmpty()).also { mutable.value = it }
        } catch (error: Exception) { mutable.value = DictateCapabilities(); throw error }
    }
}
