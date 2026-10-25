package dev.goodwy.rphone.recordings

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Paired localhost transport. Mutual proof prevents an unrelated local listener stealing the key. */
internal class CallCaptureConnection(configFile: File,command: String,bootCount: Int?=null): CaptureChannel {
    private val socket=Socket()
    private val input: DataInputStream
    private val key: ByteArray
    private val prefix: ByteArray
    private var counter=0
    init {
        try {
            require(configFile.length() in 1..2048)
            val config=JSONObject(configFile.readText())
            if(config.optString("backend")=="integrated")require(bootCount!=null && bootCount>=0 && config.getInt("bootCount")==bootCount)
            else {val remaining=config.getLong("expiresAt")-System.currentTimeMillis();require(remaining in 1..900000)}
            val port=config.getInt("port");require(port in 32000..60000)
            val token=Base64.getDecoder().decode(config.getString("token"));require(token.size==32)
            fun mac(value: String): ByteArray=Mac.getInstance("HmacSHA256").run {init(SecretKeySpec(token,"HmacSHA256"));doFinal(value.toByteArray())}
            socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),1000);socket.soTimeout=4000
            input=DataInputStream(socket.getInputStream());val output=DataOutputStream(socket.getOutputStream())
            val nonce=Base64.getEncoder().encodeToString(ByteArray(32).also {SecureRandom().nextBytes(it)})
            output.writeUTF(nonce);output.writeUTF(command);output.flush()
            require(MessageDigest.isEqual(Base64.getDecoder().decode(input.readUTF()),mac("server|$nonce|$command")))
            output.writeUTF(Base64.getEncoder().encodeToString(mac("client|$nonce|$command")));output.flush()
            key=mac("audio|$nonce|$command");prefix=mac("iv|$nonce|$command")
        }catch(e: Exception){socket.close();throw e}
    }
    override fun frame(): ByteArray {
        val size=input.readInt();require(size in 16..65552)
        val bytes=ByteArray(size);input.readFully(bytes)
        val iv=ByteBuffer.allocate(12).put(prefix,0,8).putInt(counter++).array()
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));doFinal(bytes)
        }
    }
    override fun status()=frame().toString(Charsets.UTF_8)
    override fun close(){socket.close()}
}
