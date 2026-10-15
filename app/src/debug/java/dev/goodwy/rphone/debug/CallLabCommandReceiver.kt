package dev.goodwy.rphone.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

internal class CallLabCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallLabContract.ACTION) return
        val controller = CallLabController(context)
        val command = intent.getStringExtra(CallLabContract.EXTRA_COMMAND).orEmpty()
        runCatching {
            when (command) {
                CallLabContract.COMMAND_REGISTER -> controller.registerAccount()
                CallLabContract.COMMAND_INCOMING -> controller.incoming(
                    number = intent.getStringExtra(CallLabContract.EXTRA_NUMBER)
                        ?: CallLabContract.PRIMARY_NUMBER,
                    name = intent.getStringExtra(CallLabContract.EXTRA_NAME)
                        ?: CallLabContract.PRIMARY_NAME,
                )
                CallLabContract.COMMAND_OUTGOING -> controller.outgoing(
                    number = intent.getStringExtra(CallLabContract.EXTRA_NUMBER)
                        ?: CallLabContract.OUTGOING_NUMBER,
                    name = intent.getStringExtra(CallLabContract.EXTRA_NAME)
                        ?: CallLabContract.OUTGOING_NAME,
                    autoConnect = intent.getBooleanExtra(CallLabContract.EXTRA_AUTO_CONNECT, true),
                    quality = intent.getStringExtra(CallLabContract.EXTRA_QUALITY),
                )
                CallLabContract.COMMAND_ACTIVE -> CallLabRegistry.setLatestActive()
                CallLabContract.COMMAND_HOLD -> CallLabRegistry.holdLatest()
                CallLabContract.COMMAND_RESUME -> CallLabRegistry.resumeLatest()
                CallLabContract.COMMAND_CONFERENCE -> CallLabConnectionService.conferenceAll()
                CallLabContract.COMMAND_DISCONNECT -> CallLabRegistry.disconnectLatest()
                CallLabContract.COMMAND_RESET -> CallLabRegistry.reset()
                else -> error("Unknown Call Lab command: $command")
            }
        }.onFailure { error ->
            Log.e(LOG_TAG, "Call Lab command failed: $command", error)
        }
    }

    companion object {
        private const val LOG_TAG = "DotDialerCallLab"
    }
}
