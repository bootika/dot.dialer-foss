package dev.goodwy.rphone.controller.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream

/** File-system and content-provider boundary for call-screen background images. */
internal object CallBackgroundStorage {
    private const val TAG = "CallBackgroundStorage"

    fun directory(context: Context, name: String): File? =
        deviceProtectedDirectory(context, name) ?: regularDirectory(context, name)

    fun resolveModel(stored: String, verified: (Boolean) -> Unit): String? {
        if (stored.isBlank()) return null
        if (!stored.startsWith("/")) return stored
        val file = File(stored)
        val usable = file.exists() && file.length() > 0L
        verified(usable)
        return if (usable) Uri.fromFile(file).toString() else null
    }

    fun openInputStream(context: Context, source: Uri): InputStream? {
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(source)?.let { return it }
        } catch (error: Exception) {
            Log.d(TAG, "Direct content stream unavailable; trying an asset descriptor", error)
        }

        return try {
            resolver.openAssetFileDescriptor(source, "r")?.createInputStream()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to open the selected call-background image", error)
            null
        }
    }

    private fun deviceProtectedDirectory(context: Context, name: String): File? {
        return try {
            val base = context.createDeviceProtectedStorageContext() ?: return null
            File(base.filesDir, name).takeIf { it.exists() || it.mkdirs() }
        } catch (error: Exception) {
            Log.w(TAG, "Device-protected background storage is unavailable", error)
            null
        }
    }

    private fun regularDirectory(context: Context, name: String): File? = try {
        File(context.filesDir, name).takeIf { it.exists() || it.mkdirs() }
    } catch (error: Exception) {
        Log.w(TAG, "Application background storage is unavailable", error)
        null
    }
}
