package dev.goodwy.rphone.recordings

internal interface CaptureChannel: AutoCloseable {
    fun status(): String
    fun frame(): ByteArray
}
