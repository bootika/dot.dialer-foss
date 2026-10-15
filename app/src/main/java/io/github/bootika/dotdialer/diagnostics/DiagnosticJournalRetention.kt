package io.github.bootika.dotdialer.diagnostics

import java.io.File
import java.io.RandomAccessFile

/** Runs only on the journal writer. Keep whole UTF-8 records and bound allocation to half the cap. */
internal fun trimDiagnosticJournal(file: File, maximumBytes: Long) {
    require(maximumBytes >= 2)
    if(file.length() <= maximumBytes) return
    val retained = RandomAccessFile(file,"r").use { source ->
        val count = minOf(maximumBytes / 2, source.length()).toInt()
        source.seek(source.length() - count)
        ByteArray(count).also(source::readFully)
    }
    val firstBoundary = retained.indexOf(10.toByte())
    file.writeBytes(if(firstBoundary < 0) byteArrayOf() else retained.copyOfRange(firstBoundary + 1,retained.size))
}
