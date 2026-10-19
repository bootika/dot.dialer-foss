package dev.goodwy.rphone.recordings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** AndroidX uses slash-based canonical roots, unavailable on Windows JVM. Transport fixtures
 * replace only URI creation; real Android FileProvider boundaries are instrumented separately. */
@Implements(FileProvider::class,isInAndroidSdk=false)
class RecordingFileProviderShadow {
    companion object {
        @Implementation @JvmStatic fun getUriForFile(context: Context,authority: String,file: File): Uri {
            require(authority=="${context.packageName}.provider")
            val root=File(context.filesDir,"recordings").canonicalFile.toPath()
            require(file.canonicalFile.toPath().startsWith(root))
            return Uri.Builder().scheme("content").authority(authority).appendPath("recording_audio").appendPath(file.name).build()
        }
    }
}
