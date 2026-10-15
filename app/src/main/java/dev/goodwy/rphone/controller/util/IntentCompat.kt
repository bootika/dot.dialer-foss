package dev.goodwy.rphone.controller.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable

/** Reads a parcelable extra without spreading the API 33 signature split across callers. */
inline fun <reified T : Parcelable> Intent.parcelableExtra(name: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
}
