package dev.goodwy.rphone.recordings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.RSAPublicKey;
import javax.net.ssl.*;

/** ADB-only trust policy. Never installed in the application's HTTP clients or global SSL defaults.
 * Pairing authenticates with SPAKE2 bound to the TLS exporter. Connect uses a boot-scoped local
 * daemon key pin (TOFU on explicit activation); a same-boot replacement requires successful re-pairing.
 */
public final class CaptureAdbTrustPolicy {
    private static volatile Parameters configured;
    private final Parameters parameters;
    private static final class Parameters {
        final File file; final int boot; final boolean pairing;
        Parameters(File file,int boot,boolean pairing) {
            if(boot<0)throw new IllegalArgumentException("Invalid boot count");
            this.file=file;this.boot=boot;this.pairing=pairing;
        }
    }
    public CaptureAdbTrustPolicy(File file,int boot,boolean pairing) {
        parameters=new Parameters(file,boot,pairing);
    }
    public static void configure(File file,int boot,boolean pairing) {
        configured=new Parameters(file,boot,pairing);
    }
    public static X509TrustManager configured() throws KeyManagementException {
        Parameters p=configured;
        if(p==null)throw new IllegalStateException("Local ADB trust not configured");
        return new CaptureAdbTrustPolicy(p.file,p.boot,p.pairing).trustManager();
    }
    public X509TrustManager trustManager() throws KeyManagementException {
        try {
            KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());
            roots.load(null,null);
            // Use Conscrypt's standard PKIX engine with a local-only trust-anchor store.
            // ADB certificates are self-signed; public website CA roots do not authenticate them.
            org.conscrypt.ConscryptCertStore store=new org.conscrypt.ConscryptCertStore() {
                @Override public X509Certificate getTrustAnchor(X509Certificate cert) {
                    try {validateCertificate(cert);return cert;}
                    catch(GeneralSecurityException failure){return null;}
                }
                @Override public java.util.Set<X509Certificate> findAllIssuers(X509Certificate cert) {
                    return java.util.Collections.emptySet();
                }
            };
            org.conscrypt.CertPinManager pins=(host,chain)-> {
                if(!("127.0.0.1".equals(host)||"::1".equals(host))||chain.size()!=1)
                    throw new CertificateException("Only a single local ADB peer allowed");
                X509Certificate cert=chain.get(0);
                try {
                    // Also check cached anchors on every handshake, before persisting a pin.
                    validateCertificate(cert);
                    if(!parameters.pairing)pin(cert.getPublicKey().getEncoded());
                } catch(GeneralSecurityException|IOException failure) {
                    throw new CertificateException("Local ADB certificate rejected",failure);
                }
            };
            return new org.conscrypt.TrustManagerImpl(roots,pins,store);
        }catch(GeneralSecurityException|IOException failure){throw new KeyManagementException(failure);}
    }
    public static void pairingSucceeded(File file) throws IOException {
        // Called only after the TLS-exporter-bound SPAKE2 exchange succeeds.
        Files.deleteIfExists(file.toPath());
    }
    private static void validateCertificate(X509Certificate cert) throws GeneralSecurityException {
        if(cert==null||!(cert.getPublicKey() instanceof RSAPublicKey)
                ||((RSAPublicKey)cert.getPublicKey()).getModulus().bitLength()<2048
                ||!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())
                ||!"1.2.840.113549.1.1.11".equals(cert.getSigAlgOID()))
            throw new CertificateException("Unexpected local ADB certificate");
        cert.checkValidity();cert.verify(cert.getPublicKey());
    }
    private void pin(byte[] publicKey) throws IOException,GeneralSecurityException {
        String digest=hex(MessageDigest.getInstance("SHA-256").digest(publicKey));
        synchronized(CaptureAdbTrustPolicy.class) {
            File file=parameters.file;
            if(file.exists()) {
                if(file.length()>128)throw new CertificateException("Invalid local ADB pin");
                String[] old=new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).split("\n",-1);
                if(old.length!=2||!old[0].matches("[0-9]{1,10}")||!old[1].matches("[0-9a-f]{64}"))
                    throw new CertificateException("Invalid local ADB pin");
                if(Long.parseLong(old[0])>Integer.MAX_VALUE)throw new CertificateException("Invalid local ADB boot count");
                if(Long.parseLong(old[0])==parameters.boot) {
                    if(!MessageDigest.isEqual(old[1].getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII)))
                        throw new IdentityChangedException();
                    return;
                }
            }
            File parent=file.getParentFile();
            if(parent==null||!parent.isDirectory())throw new IOException("Pin storage unavailable");
            File pending=File.createTempFile("adb-peer-",".tmp",parent);
            try {
                byte[] bytes=(parameters.boot+"\n"+digest).getBytes(StandardCharsets.US_ASCII);
                try(FileOutputStream out=new FileOutputStream(pending)){out.write(bytes);out.getFD().sync();}
                Files.move(pending.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally {Files.deleteIfExists(pending.toPath());}
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    public static final class IdentityChangedException extends CertificateException {
        IdentityChangedException(){super("Local ADB key changed; pair this phone again");}
    }
    public static boolean identityChanged(Throwable error) {
        for(int i=0;error!=null && i<16;i++,error=error.getCause())
            if(error instanceof IdentityChangedException)return true;
        return false;
    }
}
