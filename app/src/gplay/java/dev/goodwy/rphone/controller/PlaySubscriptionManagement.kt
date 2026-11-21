package dev.goodwy.rphone.controller

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri

internal object PlaySubscriptionManagement {
    fun open(activity: Activity, owned: Set<String>, failure: () -> Unit) {
        val uri = "https://play.google.com/store/account/subscriptions".toUri().buildUpon()
            .appendQueryParameter("package", activity.packageName)
        owned.singleOrNull()?.let { uri.appendQueryParameter("sku", it) }
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, uri.build())) }
        catch (_: RuntimeException) { failure() }
    }
}
