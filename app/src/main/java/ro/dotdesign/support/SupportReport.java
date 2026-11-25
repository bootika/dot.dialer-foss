package ro.dotdesign.support;

/** Allowlisted report fields; deliberately independent of logs and app storage. */
public final class SupportReport {
    private SupportReport() {}

    public static String recipient(boolean dictate) {
        return dictate ? "dictate@dotdesign.ro" : "dialer@dotdesign.ro";
    }

    public static String provider(String value) {
        if (value == null) return "";
        switch (value) {
            case "openai": case "google_gemini": case "xai_grok":
            case "groq": case "mistral": case "azure_speech": return value;
            default: return "";
        }
    }

    public static String body(String category, String description, boolean technical,
            String version, String androidVersion, String model, String provider) {
        String result = bounded(category, 120) + "\n\n" + bounded(description, 3000);
        if (technical) {
            result += "\n\n---\nApp: " + bounded(version, 80)
                    + "\nAndroid: " + bounded(androidVersion, 40)
                    + "\nDevice: " + bounded(model, 120);
            String selected = provider(provider);
            if (!selected.isEmpty()) result += "\nAI provider: " + selected;
        }
        return result;
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        return value.trim().substring(0, Math.min(value.trim().length(), limit));
    }
}
