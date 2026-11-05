package id.shuyasal.kuromaku.manga;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MangaAdFilter {

    private static final Set<String> AD_DOMAINS = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "adsterra.com",
            "monetag.com",
            "highperformancegate.com",
            "popads.net",
            "propellerads.com",
            "exoclick.com",
            "histats.com",
            "mgid.com",
            "adnxs.com",
            "adsco.re",
            "tsyndicate.com",
            "inpagepush.com",
            "onclickperformance.com",
            "toprevenuegate.com",
            "creativecdn.com",
            "cpmstar.com",
            "yadro.ru"
    ));

    private static final String[] AD_PATH_HINTS = new String[]{
            "/popunder",
            "/adbanner",
            "/ads/",
            "/advert/",
            "/ads.js",
            "adsbygoogle",
            "ignielad"
    };

    private MangaAdFilter() {
    }

    public static boolean isGoogleAuth(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("accounts.google.com")) return true;
        if (host.equals("oauth2.googleapis.com")) return true;
        String path = uri.getPath();
        if (path != null) {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (host.endsWith(".google.com") && (lowerPath.contains("/o/oauth2/") || lowerPath.contains("/signin/oauth"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEssentialOrWhitelisted(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);

        if (isVoratoonHost(host)) return true;

        if (host.equals("accounts.google.com") || host.equals("oauth2.googleapis.com")) {
            return true;
        }

        if (host.equals("apis.google.com") || host.equals("ssl.gstatic.com") || host.equals("www.gstatic.com")) {
            return true;
        }

        if (host.equals("www.googleapis.com")) {
            String path = uri.getPath();
            if (path != null) {
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.startsWith("/oauth2/") || lower.startsWith("/userinfo") || lower.startsWith("/auth/")) {
                    return true;
                }
            }
            return false;
        }

        if (host.equals("fonts.googleapis.com") || host.equals("fonts.gstatic.com")) {
            return true;
        }

        if (host.equals("challenges.cloudflare.com")
                || host.equals("static.cloudflareinsights.com")
                || host.equals("cloudflareinsights.com")
                || host.endsWith(".cloudflare.com")) {
            return true;
        }

        if (host.equals("i.imgur.com") || host.equals("imgur.com")
                || host.endsWith(".wp.com") || host.endsWith(".bp.blogspot.com")
                || host.equals("blogger.googleusercontent.com")) {
            return true;
        }

        return false;
    }

    public static boolean isVoratoonHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("voratoon.id") || normalized.endsWith(".voratoon.id")
                || normalized.equals("voratoon.com") || normalized.endsWith(".voratoon.com")
                || normalized.equals("voratoon.co") || normalized.endsWith(".voratoon.co");
    }

    public static boolean isBlockedAd(Uri uri) {
        if (uri == null) return false;
        if (isEssentialOrWhitelisted(uri)) {
            return false;
        }
        String host = uri.getHost();
        if (host != null) {
            String lowerHost = host.toLowerCase(Locale.ROOT);
            for (String domain : AD_DOMAINS) {
                if (lowerHost.equals(domain) || lowerHost.endsWith("." + domain)) {
                    return true;
                }
            }
        }
        String path = uri.getPath();
        if (path != null) {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            for (String hint : AD_PATH_HINTS) {
                if (lowerPath.contains(hint)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String formatDisplayHost(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "Sumber: voratoon.id";
        }
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "Sumber: voratoon.id";
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("accounts.google.com")) {
                return "Login Google (accounts.google.com)";
            }
            return "Sumber: " + host;
        } catch (Exception ignored) {
            return "Sumber: voratoon.id";
        }
    }
}
