package dev.goodwy.rphone.recordings

import android.app.Application
import java.io.*
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CallCaptureConnectionTest {
    private val token=ByteArray(32){it.toByte()}
    private fun mac(value: String)=Mac.getInstance("HmacSHA256").run {init(SecretKeySpec(token,"HmacSHA256"));doFinal(value.toByteArray())}
    private fun exercise(mode: String) {
        var listener: ServerSocket
        do {listener=ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));if(listener.localPort !in 32000..60000)listener.close()}while(listener.isClosed)
        val config=File.createTempFile("capture-pairing-",".json")
        val data=JSONObject().put("port",listener.localPort).put("token",Base64.getEncoder().encodeToString(token)).put("expiresAt",System.currentTimeMillis()+60000)
        if(mode=="integrated")data.put("backend","integrated").put("bootCount",37)
        config.writeText(data.toString())
        val worker=Executors.newSingleThreadExecutor()
        val result=worker.submit<Boolean> {
            listener.accept().use {socket ->socket.soTimeout=4000
                val input=DataInputStream(socket.getInputStream());val out=DataOutputStream(socket.getOutputStream())
                val nonce=input.readUTF();val command=input.readUTF();assertEquals("CAPS",command)
                out.writeUTF(Base64.getEncoder().encodeToString(if(mode=="impostor")ByteArray(32)else mac("server|$nonce|$command")));out.flush()
                if(mode=="impostor")return@submit input.read()==-1
                assertTrue(MessageDigest.isEqual(Base64.getDecoder().decode(input.readUTF()),mac("client|$nonce|$command")))
                if(mode=="oversize"){out.writeInt(65553);out.flush();return@submit true}
                val iv=ByteBuffer.allocate(12).put(mac("iv|$nonce|$command"),0,8).putInt(0).array()
                val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(mac("audio|$nonce|$command"),"AES"),GCMParameterSpec(128,iv))
                val bytes=cipher.doFinal("READY|1|16000|2|60".toByteArray())
                if(mode=="tampered")bytes[0]=(bytes[0].toInt() xor 1).toByte()
                out.writeInt(bytes.size);out.write(bytes);out.flush();true
            }
        }
        try {
            if(mode in setOf("valid","integrated"))CallCaptureConnection(config,"CAPS",if(mode=="integrated")37 else null).use {assertEquals("READY|1|16000|2|60",it.status())}
            else assertThrows(Exception::class.java){CallCaptureConnection(config,"CAPS").use {it.status()}}
            assertTrue(result.get(5,TimeUnit.SECONDS))
        }finally {listener.close();worker.shutdownNow();config.delete()}
    }
    @Test fun authenticatedEncryptedCapabilities(){exercise("valid")}
    @Test fun integratedSessionHasNoPerActivationExpiryButIsBootBound(){exercise("integrated")}
    @Test fun rebootedSessionAndMissingBootIdentityAreRejectedBeforeConnecting() {
        val file=File.createTempFile("capture-boot-",".json")
        try {file.writeText(JSONObject().put("backend","integrated").put("bootCount",37).toString())
            assertThrows(IllegalArgumentException::class.java){CallCaptureConnection(file,"CAPS",38)}
            assertThrows(IllegalArgumentException::class.java){CallCaptureConnection(file,"CAPS")}
            assertThrows(IllegalArgumentException::class.java){CallCaptureConnection(file,"CAPS",-1)}
        }finally{file.delete()}
    }
    @Test fun pairingOnlyAcceptsSixAsciiDigitsAndValidLocalPort() {
        assertTrue(CaptureSetupRules.validPairing("123456",37000))
        for(code in listOf("12345","1234567","１２３４５６","12345;","$(id)"))assertFalse(CaptureSetupRules.validPairing(code,37000))
        assertFalse(CaptureSetupRules.validPairing("123456",0));assertFalse(CaptureSetupRules.validPairing("123456",65536))
    }
    @Test fun impostorCannotObtainClientProof(){exercise("impostor")}
    @Test fun tamperedAudioFrameIsRejected(){exercise("tampered")}
    @Test fun oversizedFrameIsRejectedBeforeAllocation(){exercise("oversize")}
    @Test fun expiredPairingIsRejected() {
        val file=File.createTempFile("capture-expired-",".json")
        try {file.writeText(JSONObject().put("expiresAt",System.currentTimeMillis()-1).toString())
            assertThrows(IllegalArgumentException::class.java){CallCaptureConnection(file,"CAPS")}
        }finally{file.delete()}
    }
}
