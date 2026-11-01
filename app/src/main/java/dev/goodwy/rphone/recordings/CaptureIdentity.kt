package dev.goodwy.rphone.recordings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.*
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

/** ADB identity stays on this installation: no backup, encrypted with a non-exportable Keystore key. */
internal class CaptureIdentity(context: Context) {
    val root=File(context.noBackupFilesDir,"call-capture").apply{mkdirs()}
    val privateKey: PrivateKey
    val certificate: java.security.cert.Certificate
    init {
        val keyStore=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val alias="dot.call.capture.identity.v1"
        val wrapping=if(keyStore.containsAlias(alias))keyStore.getKey(alias,null) as SecretKey else
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        val file=AtomicFile(File(root,"identity.enc"))
        if(file.baseFile.exists()) {
            val bytes=file.openRead().use{require(it.available() in 29..16384);it.readBytes()}
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,wrapping,GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
            val data=JSONObject(cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8))
            privateKey=KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(data.getString("key"))))
            certificate=CertificateFactory.getInstance("X.509").generateCertificate(Base64.getDecoder().decode(data.getString("cert")).inputStream())
        } else {
            val keys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
            certificate=captureCertificate(keys)
            privateKey=keys.private
            val data=JSONObject().put("key",Base64.getEncoder().encodeToString(privateKey.encoded)).put("cert",Base64.getEncoder().encodeToString(certificate.encoded)).toString().toByteArray()
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,wrapping)}
            val out=file.startWrite()
            try{out.write(cipher.iv);out.write(cipher.doFinal(data));file.finishWrite(out)}catch(e: Exception){file.failWrite(out);throw e}
            data.fill(0)
        }
    }
}
