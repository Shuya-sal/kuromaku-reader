package id.shuyasal.kuromaku.komiku;

import java.net.URI;
import java.util.Locale;

/**
 * Alamat sumber Manga Server 3 (Komiku).
 *
 * <p>Domain tunggal dan tetap: tidak ada pengaturan domain di UI, dan semua
 * tautan yang dipakai parser wajib HTTPS pada host ini (atau host aset gambar
 * yang memang dipakai situs). Tautan di luar itu ditolak supaya halaman error
 * atau pengalihan asing tidak pernah dibuka sebagai gambar.
 */
public final class KomikuConfig {

    public static final String HOST = "komiku.org";
    public static final String BASE = "https://komiku.org";
    public static final String IMAGE_HOST = "img.komiku.org";
    public static final String THUMB_HOST = "thumbnail.komiku.to";

    private KomikuConfig() {
    }

    /** Benar bila alamat memakai HTTPS dan host termasuk yang diizinkan. */
    public static boolean isAllowed(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://")) return false;
        try {
            String host = URI.create(lower).getHost();
            if (host == null) return false;
            return host.equals(HOST) || host.equals(IMAGE_HOST) || host.equals(THUMB_HOST);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /** Alamat absolut dari tautan relatif halaman (atau {@code null}). */
    static String absolute(String href) {
        if (href == null) return null;
        String trimmed = href.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("https://")) return isAllowed(trimmed) ? trimmed : null;
        if (trimmed.startsWith("//")) return "https:" + trimmed;
        if (trimmed.startsWith("/")) return BASE + trimmed;
        if (trimmed.startsWith("http://")) {
            String upgraded = "https://" + trimmed.substring("http://".length());
            return isAllowed(upgraded) ? upgraded : null;
        }
        return null;
    }

    /** Slug manga dari alamat detail, mis. {@code black-clover-indonesia}. */
    static String mangaId(String url) {
        if (url == null) return "";
        String path = url;
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            int before = slash <= 0 ? -1 : path.lastIndexOf('/', slash - 1);
            if (before < 0) return "";
            return path.substring(before + 1, slash);
        }
        return path.substring(slash + 1);
    }
}