package dev.goodwy.rphone.recordings

import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User intent survives restart/helper loss. Old enabled markers migrate without changing consent. */
internal class CaptureOptIn(root: File) {
    private val file=AtomicFile(File(root,"feature-enabled"))
    private val value=MutableStateFlow(if(file.baseFile.exists())runCatching {file.openRead().use {it.read()==49}}.getOrDefault(false)else File(root,"enabled").exists())
    val state=value.asStateFlow()
    private var revision=0L
    @Synchronized fun set(enabled: Boolean) {
        val out=file.startWrite()
        try {out.write(if(enabled)49 else 48);file.finishWrite(out)}catch(error: Exception){file.failWrite(out);throw error}
        revision++;value.value=enabled
    }
    @Synchronized fun ticket()=revision
    @Synchronized fun accepts(ticket: Long)=value.value && revision==ticket
}
