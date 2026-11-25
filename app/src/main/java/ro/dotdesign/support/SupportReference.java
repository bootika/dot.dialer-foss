package ro.dotdesign.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Product-scoped lookup reference. Never an authentication or recovery credential. */
public final class SupportReference {
    private SupportReference() {}
    public static String code(String product, String identity) {
        if (!("dictate".equals(product) || "dialer".equals(product)) || identity == null
                || !identity.matches("(?:[a-f0-9]{64}|[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})")) return "";
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    ("dot-support-v1|" + product + "|" + identity).getBytes(StandardCharsets.UTF_8));
            String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
            long value = 0;
            for (int i=0;i<5;i++) value = (value << 8) | (bytes[i] & 255L);
            StringBuilder out = new StringBuilder("dictate".equals(product) ? "DIC" : "DIA");
            for (int i=0;i<8;i++) {
                if(i%4==0)out.append('-');
                out.append(alphabet.charAt((int)((value >>> (35-i*5)) & 31)));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String subject(String title, String code, boolean technical) {
        return technical && code != null && code.matches("(?:DIC|DIA)(?:-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}){2}")
                ? title + " [" + code + "]" : title;
    }
}
