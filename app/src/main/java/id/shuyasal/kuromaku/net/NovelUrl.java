package id.shuyasal.kuromaku.net;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Normalisation of a novel catalogue URL typed by the user.
 *
 * <p>The library is keyed by this value, so the same page can never be stored
 * twice under two spellings. Everything that does not change which page is being
 * addressed is removed: the mobile query parameter {@code ?m=1} that Blogger
 * appends on phones, tracking parameters, the fragment, a trailing slash, and the
 * {@code http} vs {@code https} difference.
 *
 * <p>Only Kaori Translation article pages are accepted: a link to a search page,
 * a label page, a feed, or a completely different site is refused with a message
 * the user can act on, instead of being fetched and parsed into an empty shelf.
 */
public final class NovelUrl {

    /** The only source this reader understands. */
    public static final String HOST = "kaoritranslation.blogspot.com";

    /** Raised for anything that is not a usable catalogue page. */
    public static final class Invalid extends Exception {
        public Invalid(String message) {
            super(message);
        }
    }

    private NovelUrl() {
    }

    /**
     * Canonical form of the URL, e.g.
     * {@code https://kaoritranslation.blogspot.com/2025/09/some-novel.html}.
     *
     * @throws Invalid when the text is not a Kaori Translation article page
     */
    public static String normalize(String raw) throws Invalid {
        if (raw == null || raw.trim().isEmpty()) {
            throw new Invalid("URL belum diisi.");
        }
        String text = raw.trim();
        if (!text.contains("://")) text = "https://" + text;

        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException error) {
            throw new Invalid("URL tidak valid. Contoh: https://kaoritranslation.blogspot.com/"
                    + "2025/09/judul-novel.html");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new Invalid("Hanya tautan http atau https yang bisa dipakai.");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        if (!HOST.equals(host)) {
            throw new Invalid("Sumber belum didukung. Hanya halaman katalog dari "
                    + HOST + " yang bisa ditambahkan.");
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.isEmpty() || "/".equals(path)) {
            throw new Invalid("Itu halaman depan blog, bukan halaman katalog novel.");
        }
        // A pasted link often carries a trailing slash (".../judul.html/"). Removing
        // it before the extension check means that spelling resolves to the same
        // novel instead of being refused as "not an article".
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/search") || lower.startsWith("/feeds") || lower.startsWith("/p/")) {
            throw new Invalid("Itu bukan halaman artikel. Buka halaman novelnya dulu, "
                    + "lalu salin URL artikel tersebut.");
        }
        if (!lower.endsWith(".html")) {
            throw new Invalid("URL harus menunjuk ke halaman artikel (.html).");
        }

        return "https://" + HOST + path;
    }

    /**
     * Stable id for a normalised URL: its slug.
     *
     * <p>{@code https://kaoritranslation.blogspot.com/2025/09/ln-foo.html} becomes
     * {@code ln-foo}. The id is what preferences, offline storage and the reader
     * use to keep one novel's data apart from another's, so it only uses plain
     * lowercase characters.
     */
    public static String id(String normalized) {
        if (normalized == null) return "";
        String path = normalized;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".html")) name = name.substring(0, name.length() - 5);
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]", "-")
                .replaceAll("-{2,}", "-");
        while (slug.startsWith("-")) slug = slug.substring(1);
        while (slug.endsWith("-")) slug = slug.substring(0, slug.length() - 1);
        return slug;
    }

    /** True when the text already looks like a normalised URL. */
    public static boolean isNormalized(String url) {
        try {
            return url != null && url.equals(normalize(url));
        } catch (Invalid error) {
            return false;
        }
    }
}
