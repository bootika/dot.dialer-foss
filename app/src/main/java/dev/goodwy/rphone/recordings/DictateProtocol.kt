package dev.goodwy.rphone.recordings

internal object DictateProtocol {
    const val VERSION = 1
    const val PACKAGE = "ro.dotdesign.dotdictation"
    const val SERVICE = "$PACKAGE.integration.DialerBridgeService"
    const val SETUP = "$PACKAGE.integration.DialerBridgeSetupActivity"
    const val ACTION = "$PACKAGE.action.DIALER_BRIDGE"
    const val CAPABILITIES = 1
    const val SUBMIT = 2
    const val STATUS = 3
    const val CANCEL = 4
    const val RESULT_LIMIT = 4 * 1024 * 1024
    val states = setOf("queued", "running", "completed", "failed", "cancelled")
}
internal class DictateFailure(val code: String) : Exception(code)

internal interface DictateBridge {
    val capabilities: kotlinx.coroutines.flow.StateFlow<io.github.bootika.dotdialer.core.recordings.DictateCapabilities>
    fun installed(): Boolean
    fun setupIntent(): android.content.Intent
    suspend fun approveSetup()
    fun trusted(): Boolean
    suspend fun disconnect()
    suspend fun refresh(): io.github.bootika.dotdialer.core.recordings.DictateCapabilities
    suspend fun request(what: Int, extras: android.os.Bundle = android.os.Bundle()): android.os.Bundle
}
