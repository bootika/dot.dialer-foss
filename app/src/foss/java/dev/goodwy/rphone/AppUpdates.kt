package dev.goodwy.rphone
import android.app.Activity
import android.content.Context
import ro.dotdesign.updates.UpdateHandle
internal object AppUpdates {
    @Suppress("UNUSED_PARAMETER")
    fun checkAboutVersion(context: Context, result: (Int) -> Unit) = result(0)

    fun create(activity: Activity): UpdateHandle = object : UpdateHandle {
        override fun onResume() = Unit
        override fun onPause() = Unit
        override fun onDestroy() = Unit
        override fun onActivityResult(requestCode: Int, resultCode: Int) = false
    }
}
