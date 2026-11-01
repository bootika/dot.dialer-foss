package dev.goodwy.rphone.recordings

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One durable receipt per activation, outside backup; legacy preferences remain read-only. */
internal class CaptureReminderDelivery(private val receipt: File,private val legacySession: ()->String?={null}) {
    @Synchronized fun postOnce(session: String,post: ()->Boolean) {
        val previous=if(receipt.exists())receipt.readText()else legacySession()
        if(previous==session || !post())return
        val parent=checkNotNull(receipt.parentFile)
        check(parent.isDirectory || parent.mkdirs())
        val pending=File.createTempFile("activation-",".tmp",parent)
        try {
            // Do not acknowledge delivery until the receipt has reached disk.
            FileOutputStream(pending).use{it.write(session.toByteArray(Charsets.UTF_8));it.fd.sync()}
            Files.move(pending.toPath(),receipt.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }finally {Files.deleteIfExists(pending.toPath())}
    }
}
