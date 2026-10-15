package dev.goodwy.rphone.debug

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

internal class CallLabController(context: Context) {
    private val applicationContext = context.applicationContext
    private val telecomManager = applicationContext.getSystemService(TelecomManager::class.java)
    private val handle get() = CallLabContract.phoneAccountHandle(applicationContext)

    fun registerAccount() {
        val account = PhoneAccount.builder(handle, "Call Lab")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
            .setHighlightColor(Color.rgb(0, 187, 119))
            .build()
        telecomManager.registerPhoneAccount(account)
    }

    fun isAccountEnabled(): Boolean = telecomManager.getPhoneAccount(handle)?.isEnabled == true

    fun openAccountSettings() {
        applicationContext.startActivity(
            Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun incoming(
        number: String = CallLabContract.PRIMARY_NUMBER,
        name: String = CallLabContract.PRIMARY_NAME,
    ) {
        registerAccount()
        telecomManager.addNewIncomingCall(
            handle,
            Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null),
                )
                putString(CallLabContract.EXTRA_NAME, name)
                putBoolean(CallLabContract.EXTRA_AUTO_CONNECT, false)
            },
        )
    }

    fun outgoing(
        number: String = CallLabContract.OUTGOING_NUMBER,
        name: String = CallLabContract.OUTGOING_NAME,
        autoConnect: Boolean = true,
        quality: String? = null,
    ) {
        registerAccount()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            telecomManager.placeCall(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null),
                Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                    putBundle(
                        TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                        Bundle().apply {
                            putString(CallLabContract.EXTRA_NAME, name)
                            putBoolean(CallLabContract.EXTRA_AUTO_CONNECT, autoConnect)
                            quality?.let { putString(CallLabContract.EXTRA_QUALITY, it) }
                        },
                    )
                },
            )
        }
    }
}
