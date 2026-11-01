// SPDX-License-Identifier: Apache-2.0
// Based on libadb-android 3.1.1, copyright Muntashir Al-Islam.
// Local replacement: no accepting-all trust manager or globally cached TLS context.
package io.github.muntashirakon.adb;

import java.net.Socket;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import dev.goodwy.rphone.recordings.CaptureAdbTrustPolicy;

final class SslUtils {
    private static volatile boolean customConscrypt;
    public static boolean isCustomConscrypt(){return customConscrypt;}
    public static SSLContext getSslContext(KeyPair keys) throws NoSuchAlgorithmException,KeyManagementException {
        SSLContext context=SSLContext.getInstance("TLSv1.3",new org.conscrypt.OpenSSLProvider());
        customConscrypt=true;
        context.init(new KeyManager[]{manager(keys)},new TrustManager[]{CaptureAdbTrustPolicy.configured()},new SecureRandom());
        return context;
    }
    private static KeyManager manager(KeyPair keys) {
        return new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String type,Principal[] issuers){return "RSA".equals(type)?new String[]{"key"}:null;}
            @Override public String chooseClientAlias(String[] types,Principal[] issuers,Socket socket){
                if(types!=null)for(String type:types)if("RSA".equals(type))return "key";return null;
            }
            @Override public String[] getServerAliases(String type,Principal[] issuers){return null;}
            @Override public String chooseServerAlias(String type,Principal[] issuers,Socket socket){return null;}
            @Override public X509Certificate[] getCertificateChain(String alias){return "key".equals(alias)?new X509Certificate[]{(X509Certificate)keys.getCertificate()}:null;}
            @Override public PrivateKey getPrivateKey(String alias){return "key".equals(alias)?keys.getPrivateKey():null;}
        };
    }
}
