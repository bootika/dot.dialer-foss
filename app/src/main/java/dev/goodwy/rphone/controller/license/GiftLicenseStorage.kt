package dev.goodwy.rphone.controller.license

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal class GiftLicenseStorage(context: Context, private val alias: String = "dot.dialer.gift-license.v1", filename: String = "gift-license-v1.bin", private val keyProvider: ((Boolean) -> SecretKey)? = null) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, filename))
    private fun key(create: Boolean): SecretKey {
        keyProvider?.let { return it(create) }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    fun read(): JSONObject? {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully(); require(bytes.size in 29..16384)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(alias.toByteArray())
        return JSONObject(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
    }
    fun write(record: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // A successful online redemption may recover from a lost local Keystore key.
        cipher.init(Cipher.ENCRYPT_MODE, key(true)); cipher.updateAAD(alias.toByteArray())
        val bytes = cipher.iv + cipher.doFinal(record.toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (error: Exception) { file.failWrite(out); throw error }
    }
}
