package dev.goodwy.rphone.recordings

import dev.goodwy.rphone.BuildConfig

internal object DictateProtocol {
    const val VERSION = 1
    private const val COMPONENT_NAMESPACE = "ro.dotdesign.dotdictation"
    val PACKAGE = if (BuildConfig.DICTATE_PLAY_PROVIDER) "ro.dotdesign.dotdictate" else COMPONENT_NAMESPACE
    val PAIRING_PREFERENCES = if (BuildConfig.DICTATE_PLAY_PROVIDER) "dictate_play_pairing" else "dictate_pairing"
    const val SERVICE = "$COMPONENT_NAMESPACE.integration.DialerBridgeService"
    const val SETUP = "$COMPONENT_NAMESPACE.integration.DialerBridgeSetupActivity"
    const val ACTION = "$COMPONENT_NAMESPACE.action.DIALER_BRIDGE"
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
