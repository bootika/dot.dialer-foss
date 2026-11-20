package ro.dotdesign.updates;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Process-scoped, coalesced asynchronous checks; never called synchronously by capture/upload. */
public final class UpdateRuntime {
    // The singleton retains only getApplicationContext(), never an Activity or IME.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static UpdateRuntime instance;
    public static synchronized UpdateRuntime get(Context context, String endpoint, String audience, String key) {
        if (instance == null) instance = new UpdateRuntime(context.getApplicationContext(),endpoint,audience,key);
        return instance;
    }
    final Context context;
    final AppUpdateManager manager;
    private final String endpoint, audience, key;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"app-update-policy");t.setPriority(Thread.MIN_PRIORITY);return t;});
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean checking, attempted;
    private long lastAttempt;
    private long installed;
    private Snapshot snapshot;
    public static final class Snapshot {
        final AppUpdateInfo info;
        final boolean requiredBuild;
        final long received;
        final String[] notes;
        Snapshot(AppUpdateInfo info, Policy policy, long received) {this.info=info;this.requiredBuild=policy.required;this.received=received;this.notes=policy.notes;}
    }
    private UpdateRuntime(Context context,String endpoint,String audience,String key) {
        this(context,endpoint,audience,key,AppUpdateManagerFactory.create(context));
    }
    UpdateRuntime(Context context,String endpoint,String audience,String key,AppUpdateManager manager) {
        this.context=context;this.endpoint=endpoint;this.audience=audience;this.key=key;
        this.manager=manager;
        try {installed=context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode();}
        catch (Exception ignored) {installed=0;}
    }
    public boolean online() {
        try {
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities n=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
            return n!=null && n.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException ignored) {return false;}
    }
    public UpdateDecision.Mode mode() {
        Snapshot s=snapshot;
        if(s==null||!online())return UpdateDecision.Mode.NONE;
        boolean available=s.info.updateAvailability()==UpdateAvailability.UPDATE_AVAILABLE
                || s.info.updateAvailability()==UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
        return UpdateDecision.decide(installed,s.info.availableVersionCode(),available,
                s.info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),s.info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                true,s.requiredBuild,SystemClock.elapsedRealtime()-s.received);
    }
    public boolean required() {return mode()==UpdateDecision.Mode.REQUIRED;}
    public boolean available() {return mode()!=UpdateDecision.Mode.NONE;}
    public boolean enabled() {return context.getPackageName().equals(audience);}
    Snapshot snapshot() {return snapshot;}
    void unavailable() {snapshot=null;}
    public void refresh() {refresh(false);}
    void refresh(boolean userRequested) {
        if(!enabled()||checking)return;
        long now=SystemClock.elapsedRealtime();
        if(!userRequested&&attempted&&now-lastAttempt<UpdateDecision.FRESH_MS)return;
        if(!online()){snapshot=null;return;}
        attempted=true;lastAttempt=now;checking=true;
        // Fetch Play first. No policy HTTP call is needed for an ineligible/latest installation.
        manager.getAppUpdateInfo().addOnSuccessListener(info -> {
            int availability=info.updateAvailability();
            if((availability!=UpdateAvailability.UPDATE_AVAILABLE && availability!=UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                    ||info.availableVersionCode()<=installed){snapshot=null;checking=false;return;}
            executor.execute(() -> {
                Policy policy=null;
                try {policy=fetchPolicy(info.availableVersionCode());}catch(Exception ignored) { /* No transport error is a denial. */ }
                Policy result=policy;
                main.post(() -> {snapshot=result==null?null:new Snapshot(info,result,SystemClock.elapsedRealtime());checking=false;});
            });
        }).addOnFailureListener(error -> {snapshot=null;checking=false;});
    }
    private Policy fetchPolicy(int target) throws Exception {
        byte[] random=new byte[16];new SecureRandom().nextBytes(random);
        StringBuilder nonce=new StringBuilder(32);for(byte b:random)nonce.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        String language=context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        if(language.equals("in"))language="id";
        if(!language.matches("[a-z]{2}"))language="en";
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint+"?nonce="+nonce+"&target="+target+"&locale="+language).openConnection();
        connection.setConnectTimeout(3000);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        try {
            if(connection.getResponseCode()!=200)throw new java.io.IOException("update_policy_unavailable");
            byte[] response;
            try(java.io.InputStream stream=connection.getInputStream();java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=stream.read(buffer))!=-1){if(bytes.size()+count>65536)throw new SecurityException("update_policy_size");bytes.write(buffer,0,count);}
                response=bytes.toByteArray();
            }
            if(response.length>65536)throw new SecurityException("update_policy_size");
            JSONObject envelope=new JSONObject(new String(response,StandardCharsets.UTF_8));
            return verify(envelope.getString("payload"),envelope.getString("signature"),key,audience,nonce.toString(),installed,target,
                    context.getResources().getConfiguration().getLocales().get(0).toLanguageTag());
        } finally {connection.disconnect();}
    }
    static final class Policy {
        final boolean required; final String[] notes;
        Policy(boolean required,String[] notes){this.required=required;this.notes=notes;}
    }
    static Policy verify(String payload,String signature,String key,String audience,String nonce,long installed,long target,String locale) throws Exception {
        if(payload.length()>60000||signature.length()>1024)throw new SecurityException("update_policy_size");
        Signature verifier=Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))));
        verifier.update(payload.getBytes(StandardCharsets.US_ASCII));
        if(!verifier.verify(Base64.getUrlDecoder().decode(signature)))throw new SecurityException("update_policy_signature");
        JSONObject body=new JSONObject(new String(Base64.getUrlDecoder().decode(payload),StandardCharsets.UTF_8));
        if(body.getInt("v")!=1||!body.getString("purpose").equals("app_update_policy")
                ||!body.getString("package").equals(audience)||!body.getString("nonce").equals(nonce)
                ||body.getLong("expires_at")-body.getLong("issued_at")!=3600||body.getLong("issued_at")<=0)
            throw new SecurityException("update_policy_scope");
        JSONArray builds=body.getJSONArray("required_builds");
        if(builds.length()>500)throw new SecurityException("update_policy_size");
        boolean required=false;
        for(int i=0;i<builds.length();i++){
            Object value=builds.get(i);
            if(!(value instanceof Integer)&&!(value instanceof Long))throw new SecurityException("update_policy_build");
            long build=((Number)value).longValue();if(build<1||build>2100000000)throw new SecurityException("update_policy_build");
            if(build==installed)required=true;
        }
        JSONObject release=body.getJSONObject("release");
        if(release.getLong("build")!=target)throw new SecurityException("update_policy_target");
        JSONObject translations=release.getJSONObject("notes");
        locale=locale.replaceFirst("^in(?=-|$)","id");
        JSONArray selected=translations.optJSONArray(locale);
        if(selected==null)selected=translations.optJSONArray(locale.split("-")[0]);
        if(selected==null)selected=translations.optJSONArray("en");
        String[] notes=new String[selected==null?0:selected.length()];
        if(notes.length>30)throw new SecurityException("update_notes_size");
        for(int i=0;i<notes.length;i++){
            notes[i]=selected.getString(i);
            if(notes[i].length()>500)throw new SecurityException("update_notes_size");
        }
        return new Policy(required,notes);
    }
}
