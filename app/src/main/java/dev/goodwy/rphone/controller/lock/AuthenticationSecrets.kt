package dev.goodwy.rphone.controller.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A salted verifier, never a recoverable PIN/password. The whole record is encrypted at rest. */
internal data class AuthenticationRecord(
    val method: String,
    val salt: ByteArray,
    val verifier: ByteArray,
    val failures: Int = 0,
    val retryAfter: Long = 0,
) {
    fun encode(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use {
            it.writeInt(1)
            it.writeUTF(method)
            it.write(salt)
            it.write(verifier)
            it.writeInt(failures)
            it.writeLong(retryAfter)
        }
        bytes.toByteArray()
    }

    companion object {
        fun create(method: String, secret: String): AuthenticationRecord {
            require(method == "pin" || method == "password")
            require(secret.isNotEmpty())
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            return AuthenticationRecord(method, salt, derive(secret, salt))
        }

        fun decode(bytes: ByteArray): AuthenticationRecord = DataInputStream(ByteArrayInputStream(bytes)).use {
            require(it.readInt() == 1)
            val method = it.readUTF()
            require(method == "pin" || method == "password")
            val salt = ByteArray(16).also(it::readFully)
            val verifier = ByteArray(32).also(it::readFully)
            val failures = it.readInt()
            val retryAfter = it.readLong()
            require(failures in 0..30 && retryAfter >= 0 && it.available() == 0)
            AuthenticationRecord(method, salt, verifier, failures, retryAfter)
        }

        fun derive(secret: String, salt: ByteArray): ByteArray {
            val chars = secret.toCharArray()
            val spec = PBEKeySpec(chars, salt, 600_000, 256)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                chars.fill('\u0000')
            }
        }
    }

    fun matches(secret: String): Boolean = secret.isNotEmpty() &&
        MessageDigest.isEqual(verifier, derive(secret, salt))

    fun failed(now: Long): AuthenticationRecord {
        val count = (failures + 1).coerceAtMost(30)
        val delay = if (count < 5) 0L else (30_000L shl (count - 5).coerceAtMost(6)).coerceAtMost(1_800_000L)
        return copy(failures = count, retryAfter = if (delay == 0L) 0L else now + delay)
    }
}

/** Non-exportable key; no APK secret, plaintext preference or backup-portable credential. */
class AuthenticationSecrets(
    context: Context,
    private val keyProvider: ((Boolean) -> SecretKey)? = null,
) {
    private val directory = context.applicationContext.noBackupFilesDir
    private fun file(method: String): AtomicFile {
        require(method == "pin" || method == "password")
        return AtomicFile(java.io.File(directory, "authentication-$method-v1.bin"))
    }

    private fun key(create: Boolean): SecretKey {
        keyProvider?.let { return it(create) }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Authentication key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private fun read(method: String): AuthenticationRecord {
        val bytes = file(method).readFully()
        require(bytes.size in 29..256)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(ALIAS.toByteArray(Charsets.UTF_8))
        return AuthenticationRecord.decode(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    private fun write(record: AuthenticationRecord) {
        val file = file(record.method)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(!file.baseFile.exists()))
        cipher.updateAAD(ALIAS.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.iv + cipher.doFinal(record.encode())
        writeEncrypted(file, encrypted)
    }

    private fun writeEncrypted(file: AtomicFile, encrypted: ByteArray) {
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    suspend fun configure(method: String, secret: String) = withContext(Dispatchers.IO) {
        lock.withLock { write(AuthenticationRecord.create(method, secret)) }
    }

    internal suspend fun configureAndCommit(method: String, secret: String, commit: suspend () -> Unit) = withContext(Dispatchers.IO) {
        lock.withLock {
            val target = file(method)
            val previous = if (target.baseFile.exists()) target.readFully() else null
            try {
                write(AuthenticationRecord.create(method, secret))
                commit()
            } catch (error: Exception) {
                try {
                    if (previous == null) target.delete() else writeEncrypted(target, previous)
                } catch (rollback: Exception) { error.addSuppressed(rollback) }
                throw error
            }
        }
    }

    suspend fun migrate(method: String, secret: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (file(method).baseFile.exists()) {
                val existing = read(method)
                check(existing.method == method && existing.matches(secret))
            } else write(AuthenticationRecord.create(method, secret))
        }
    }

    suspend fun verify(method: String, secret: String): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            // Missing/corrupt file or key is an error, never an empty credential or unlock.
            val record = read(method)
            check(record.method == method)
            val now = System.currentTimeMillis()
            if (record.retryAfter > now) throw AuthenticationRetryException((record.retryAfter - now + 999) / 1000)
            val accepted = record.matches(secret)
            write(if (accepted) record.copy(failures = 0, retryAfter = 0) else record.failed(now))
            accepted
        }
    }

    suspend fun clear(except: String? = null) = withContext(Dispatchers.IO) {
        lock.withLock {
            listOf("pin", "password").filter { it != except }.forEach { file(it).delete() }
        }
    }

    companion object {
        private const val ALIAS = "dot.dialer.authentication.v1"
        private val lock = Mutex()
    }
}

class AuthenticationRetryException(val seconds: Long) : Exception("Authentication temporarily limited")
