package dev.goodwy.rphone.controller.license

import java.security.MessageDigest
import java.util.UUID

// Preserve the existing server and cached-grant installation identity.
internal fun scopedLicenseInstallationId(androidId: String, packageName: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest("dot-test-access-v1|$packageName|$androidId".toByteArray())
    bytes[6] = ((bytes[6].toInt() and 15) or 64).toByte()
    bytes[8] = ((bytes[8].toInt() and 63) or 128).toByte()
    val buffer = java.nio.ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long).toString()
}
