package dev.goodwy.rphone.controller.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Avoid Context.applicationContext in DataStore's convenience extension: it can select CE files. */
internal object DevicePreferenceStorage {
    const val RELATIVE_PATH = "datastore/rill_prefs.preferences_pb"

    fun file(context: Context): File {
        val app = context.applicationContext
        val target = File(app.createDeviceProtectedStorageContext().filesDir, RELATIVE_PATH)
        val legacy = File(app.filesDir, RELATIVE_PATH)
        if (!target.exists() && legacy.isFile && target.canonicalPath != legacy.canonicalPath) {
            val bytes = legacy.readBytes()
            val directory = requireNotNull(target.parentFile)
            check(directory.let { it.isDirectory || it.mkdirs() })
            val pending = File.createTempFile("preference_migration_", ".tmp", directory)
            try {
                FileOutputStream(pending).use { it.write(bytes); it.fd.sync() }
                check(pending.renameTo(target)) { "Preference migration commit failed" }
                check(target.readBytes().contentEquals(bytes)) { "Preference migration verification failed" }
                if (!legacy.delete()) android.util.Log.w("Preferences", "Legacy duplicate could not be removed")
            } finally { pending.delete() }
        }
        return target
    }
}
