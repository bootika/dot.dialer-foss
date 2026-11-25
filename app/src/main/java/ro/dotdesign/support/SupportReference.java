package ro.dotdesign.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Product-scoped lookup reference. Never an authentication or recovery credential. */
public final class SupportReference {
    private SupportReference() {}
    public static String code(String product, String identity) {
        if (!("dictate".equals(product) || "dialer".equals(product)) || identity == null
                || !identity.matches("(?:[a-f0-9]{64}|[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})")) return "";
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    ("dot-support-v1|" + product + "|" + identity).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("dictate".equals(product) ? "DCT" : "DLR");
            for (int i=0;i<10;i++) {
                if(i%2==0)out.append('-');
                out.append(String.format(Locale.ROOT,"%02X",bytes[i]&255));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String subject(String title, String code, boolean technical) {
        return technical && code != null && code.matches("(?:DCT|DLR)(?:-[A-F0-9]{4}){5}")
                ? title + " [" + code + "]" : title;
    }
}
