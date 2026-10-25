package dev.goodwy.rphone.recordings;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

/** Embedded shell capture. Narrow authenticated local protocol, no terminal or arbitrary command interface. */
@androidx.annotation.Keep
public final class IntegratedCaptureServer {
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static volatile AudioRecord[] active=new AudioRecord[0];
    private static final AtomicBoolean stop=new AtomicBoolean();
    private static byte[] token;
    private static volatile long heartbeat;
    private static volatile Socket audioClient;
    private static void halt(){stop.set(true);Socket client=audioClient;if(client!=null)try{client.close();}catch(Exception ignored){}for(AudioRecord r:active)try{r.stop();}catch(Exception ignored){}}
    private static byte[] mac(String value)throws Exception {
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(token,"HmacSHA256"));return m.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=2000 || args.length!=3)throw new SecurityException("ADB activation required");
        int port=Integer.parseInt(args[0]);if(port<32000||port>60000)throw new SecurityException("Invalid port");
        File secret=new File(args[1]).getCanonicalFile();
        if(!secret.getPath().matches("/data/local/tmp/dot-integrated-pair-[a-f0-9]{32}"))throw new SecurityException("Invalid pairing file");
        String encoded=new String(Files.readAllBytes(secret.toPath()),StandardCharsets.UTF_8).trim();
        token=Base64.getDecoder().decode(encoded);if(token.length!=32)throw new SecurityException("Invalid pairing token");
        if(!secret.delete())throw new IOException("Cannot remove pairing file");
        new File(args[1]+".log").delete();
        ServerSocket server=new ServerSocket(port,4,InetAddress.getByName("127.0.0.1"));
        ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();
        ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2));
        deadlines.scheduleAtFixedRate(()->{if(busy.get() && SystemClock.elapsedRealtime()-heartbeat>10000)halt();
            if(!new File(args[2]).exists()){halt();try{server.close();}catch(Exception ignored){}}},2,2,TimeUnit.SECONDS);
        System.out.println("READY integrated=true stereo=2 loopbackEncrypted=true");System.out.flush();
        android.util.Log.i("CallCaptureWorker","started");
        try{while(true){Socket socket=server.accept();try{workers.execute(()->serve(socket,deadlines));}catch(RejectedExecutionException e){socket.close();}}}
        catch(IOException closed){/* finite lifetime */}
        finally{android.util.Log.i("CallCaptureWorker","exiting");halt();server.close();workers.shutdownNow();deadlines.shutdownNow();}
    }
    private static final class Frames {
        final DataOutputStream out;final byte[] key,prefix;int counter;
        Frames(DataOutputStream out,String nonce,String command)throws Exception{this.out=out;key=mac("audio|"+nonce+"|"+command);prefix=mac("iv|"+nonce+"|"+command);}
        void send(byte[] bytes)throws Exception {
            byte[] iv=ByteBuffer.allocate(12).put(prefix,0,8).putInt(counter++).array();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(bytes);out.writeInt(encrypted.length);out.write(encrypted);out.flush();
        }
        void status(String text)throws Exception{send(text.getBytes(StandardCharsets.UTF_8));}
    }
    private static void serve(Socket socket,ScheduledExecutorService deadlines){
        try(Socket client=socket){
            client.setSoTimeout(3000);
            DataInputStream in=new DataInputStream(client.getInputStream());DataOutputStream out=new DataOutputStream(client.getOutputStream());
            String nonce=in.readUTF(),command=in.readUTF();
            if(nonce.length()>64 || Base64.getDecoder().decode(nonce).length!=32 || !Arrays.asList("CAPS","START","STOP","BEAT","QUIT","QUIET","QUIET_QUIT").contains(command))return;
            out.writeUTF(Base64.getEncoder().encodeToString(mac("server|"+nonce+"|"+command)));out.flush();
            if(!MessageDigest.isEqual(Base64.getDecoder().decode(in.readUTF()),mac("client|"+nonce+"|"+command)))return;
            Frames frames=new Frames(out,nonce,command);
            if(command.equals("CAPS")){frames.status("READY|2|16000|2|0");return;}
            if(command.equals("STOP")){halt();frames.status("STOPPED");return;}
            if(command.equals("BEAT")){heartbeat=SystemClock.elapsedRealtime();frames.status("OK");return;}
            if(command.equals("QUIET")){boolean quiet=WirelessDebuggingStopper.disable();frames.status(quiet?"WIFI_OFF":"WIFI_UNVERIFIED");return;}
            if(command.equals("QUIET_QUIT")){halt();boolean quiet=WirelessDebuggingStopper.disable();frames.status(quiet?"STOPPED|WIFI_OFF":"STOPPED|WIFI_UNVERIFIED");System.exit(0);return;}
            if(command.equals("QUIT")){halt();frames.status("STOPPED");System.exit(0);return;}
            if(!busy.compareAndSet(false,true)){frames.status("BUSY");return;}
            AudioRecord up=null,down=null;ScheduledFuture<?> deadline=null;
            try{
                stop.set(false);heartbeat=SystemClock.elapsedRealtime();audioClient=client;int size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(size<=0)throw new IOException("Unsupported PCM");
                up=new AudioRecord(2,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,32000));
                down=new AudioRecord(3,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,32000));
                active=new AudioRecord[]{up,down};
                if(up.getState()!=AudioRecord.STATE_INITIALIZED||down.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Unavailable");
                up.startRecording();down.startRecording();
                if(up.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING||down.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Not recording");
                frames.status("STARTED");
                short[] tx=new short[1600],rx=new short[1600];long bytes=0,start=SystemClock.elapsedRealtime();
                while(!stop.get()){
                    int n=up.read(tx,0,tx.length,AudioRecord.READ_BLOCKING);
                    int m=stop.get()?0:down.read(rx,0,rx.length,AudioRecord.READ_BLOCKING);
                    if(stop.get())break;
                    if(n<=0||m!=n)throw new IOException("Unaligned independent voice paths");
                    byte[] stereo=new byte[n*4];
                    for(int i=0;i<n;i++){stereo[i*4]=(byte)tx[i];stereo[i*4+1]=(byte)(tx[i]>>>8);stereo[i*4+2]=(byte)rx[i];stereo[i*4+3]=(byte)(rx[i]>>>8);}
                    frames.send(stereo);bytes+=stereo.length;
                }
                frames.send(new byte[0]);
            }finally{if(deadline!=null)deadline.cancel(false);active=new AudioRecord[0];audioClient=null;
                for(AudioRecord r:new AudioRecord[]{up,down})if(r!=null){try{r.stop();}catch(Exception ignored){}r.release();}busy.set(false);}

        }catch(Exception ignored){/* EOF/error aborts capture; client retains committed frames */}
    }
}
