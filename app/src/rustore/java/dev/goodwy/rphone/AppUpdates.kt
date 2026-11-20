package dev.goodwy.rphone
import android.app.Activity
import ro.dotdesign.updates.UpdateHandle
internal object AppUpdates {
    fun create(activity: Activity): UpdateHandle = object : UpdateHandle {
        override fun onResume() = Unit
        override fun onPause() = Unit
        override fun onDestroy() = Unit
        override fun onActivityResult(requestCode: Int, resultCode: Int) = false
    }
}
