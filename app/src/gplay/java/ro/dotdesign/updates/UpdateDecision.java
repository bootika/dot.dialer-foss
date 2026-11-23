package ro.dotdesign.updates;

/** Pure policy: unknown/unavailable never becomes a denial. */
public final class UpdateDecision {
    public static final long FRESH_MS = 3_600_000L;
    public enum Mode { NONE, OPTIONAL, REQUIRED }
    private UpdateDecision() {}
    public static Mode decide(long installed, long offered, boolean playAvailable,
            boolean immediateAllowed, boolean flexibleAllowed, boolean policyVerified,
            Mode requestedMode, long ageMillis) {
        if (installed <= 0 || offered <= installed || !playAvailable || !policyVerified
                || ageMillis < 0 || ageMillis >= FRESH_MS) return Mode.NONE;
        if (requestedMode == Mode.REQUIRED && immediateAllowed) return Mode.REQUIRED;
        if (requestedMode == Mode.OPTIONAL && flexibleAllowed) return Mode.OPTIONAL;
        return Mode.NONE;
    }
    public static boolean shouldPrompt(long now, long lastShown) {
        return lastShown <= 0 || (now >= lastShown && now - lastShown >= 86_400_000L);
    }
}
