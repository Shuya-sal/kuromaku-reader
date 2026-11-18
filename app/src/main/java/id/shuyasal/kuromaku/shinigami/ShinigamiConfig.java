package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ShinigamiConfig {
    public static final String FILE_NAME = "kuromaku_shinigami_config";
    public static final String DEFAULT_API_BASE = "https://api.shinigami.io/v1";
    public static final String DEFAULT_SITE_BASE = "https://s1.shinigami.mx";
    private static final String KEY_API_BASE = "api_base";
    private static final String KEY_SITE_BASE = "site_base";
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");
    private static final String[] ALLOWED_ROOTS = {
            "shinigami.io", "shinigami.mx", "shinigami.gg",
            "shinigami.asia", "shinigami.to", "shngm.io", "shngm.id"
    };
    private final SharedPreferences prefs;

    public ShinigamiConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public String apiBase() {
        String stored = prefs.getString(KEY_API_BASE, "");
        if (!isValidBase(stored)) return DEFAULT_API_BASE;
        if (isRetiredHost(stored)) {
            // Aplikasi versi lama menyimpan alamat API yang sekarang tidak lagi
            // menjawab. Alamat itu diganti ke alamat baru sekali, tanpa menyentuh
            // pengaturan lain; kalau pengguna memang memakai cermin lain yang
            // masih hidup, cermin itu tetap dipakai.
            prefs.edit().putString(KEY_API_BASE, DEFAULT_API_BASE).apply();
            return DEFAULT_API_BASE;
        }
        return normalize(stored);
    }

    public String siteBase() {
        String stored = prefs.getString(KEY_SITE_BASE, "");
        if (!isValidBase(stored)) return DEFAULT_SITE_BASE;
        if (isRetiredHost(stored)) {
            prefs.edit().putString(KEY_SITE_BASE, DEFAULT_SITE_BASE).apply();
            return DEFAULT_SITE_BASE;
        }
        return normalize(stored);
    }

    /** Alamat lama yang sudah tidak menjawab (server 502 domain mati). */
    static boolean isRetiredHost(String value) {
        try {
            String host = new URI(value.trim()).getHost();
            if (host == null) return false;
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.equals("api.shngm.io") || lower.equals("shngm.io")
                    || lower.endsWith(".shngm.io")
                    || lower.equals("shinigami.asia") || lower.endsWith(".shinigami.asia")
                    || lower.equals("shinigami.to") || lower.endsWith(".shinigami.to")
                    || lower.equals("shinigami.gg") || lower.endsWith(".shinigami.gg");
        } catch (Exception error) {
            return false;
        }
    }

    public boolean setApiBase(String value) {
        if (!isValidBase(value)) return false;
        prefs.edit().putString(KEY_API_BASE, normalize(value)).apply();
        return true;
    }

    public boolean setSiteBase(String value) {
        if (!isValidBase(value)) return false;
        prefs.edit().putString(KEY_SITE_BASE, normalize(value)).apply();
        return true;
    }

    public boolean setBases(String api, String site) {
        if (!isValidBase(api) || !isValidBase(site)) return false;
        prefs.edit().putString(KEY_API_BASE, normalize(api))
                .putString(KEY_SITE_BASE, normalize(site)).apply();
        return true;
    }

    public static boolean isValidBase(String value) {
        if (!isValidResource(value)) return false;
        try {
            URI uri = new URI(value.trim());
            return uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (Exception error) {
            return false;
        }
    }

    public static boolean isValidResource(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 4096) return false;
        String input = value.trim();
        for (int i = 0; i < input.length(); i++) {
            if (Character.isWhitespace(input.charAt(i)) || Character.isISOControl(input.charAt(i))) return false;
        }
        try {
            URI uri = new URI(input);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || uri.getRawFragment() != null) return false;
            if (!HOST_PATTERN.matcher(host.toLowerCase(Locale.ROOT)).matches()
                    || !underAllowedRoot(host)) return false;
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().toLowerCase(Locale.ROOT);
            return !path.contains("%") && !path.contains("\\")
                    && !path.contains("//") && !path.matches(".*(?:^|/)\\.\\.?(?:/|$).*");
        } catch (Exception error) {
            return false;
        }
    }

    public static boolean underAllowedRoot(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        if (!HOST_PATTERN.matcher(lower).matches()) return false;
        for (String root : ALLOWED_ROOTS) {
            if (lower.equals(root) || lower.endsWith("." + root)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
