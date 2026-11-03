package id.shuyasal.kuromaku.net;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * Normalisation of a novel address typed by the user.
 *
 * <p>The library is keyed by this value, so the same page can never be stored
 * twice under two spellings. Everything that does not change which page is being
 * addressed is removed: the mobile query parameter {@code ?m=1} that Blogger
 * appends on phones, tracking parameters, the fragment, a trailing slash, and the
 * {@code http} vs {@code https} difference.
 *
 * <p>Two shapes are accepted, because the source publishes novels in both:
 *
 * <ul>
 *   <li><b>article</b> — {@code /2025/09/judul-novel.html}, the post that holds
 *       the metadata and usually the volume list;</li>
 *   <li><b>label</b> — {@code /search/label/Nama%20Novel}, the page Blogger builds
 *       from every post carrying that label. Refusing it, as an earlier version
 *       did, threw away the most reliable catalogue a novel can have: the label
 *       page's own pagination is irrelevant because its JSON feed is read
 *       instead, and that feed lists every chapter of that novel and nothing
 *       else.</li>
 * </ul>
 *
 * <p>Everything else — the front page, {@code /feeds/}, a static page under
 * {@code /p/}, a post without a date, another site — is refused with a message
 * the user can act on, instead of being fetched and parsed into an empty shelf.
 */
public final class NovelUrl {

    /** The only source this reader understands. */
    public static final String HOST = "kaoritranslation.blogspot.com";

    /** What kind of page a normalised address points at. */
    public enum Kind {
        /** A dated post: {@code /2026/03/some-novel.html}. */
        ARTICLE,
        /** A Blogger label listing: {@code /search/label/Some%20Novel}. */
        LABEL
    }

    /** Raised for anything that is not a usable catalogue address. */
    public static final class Invalid extends Exception {
        public Invalid(String message) {
            super(message);
        }
    }

    private NovelUrl() {
    }

    /**
     * Canonical form of the address, e.g.
     * {@code https://kaoritranslation.blogspot.com/2025/09/some-novel.html} or
     * {@code https://kaoritranslation.blogspot.com/search/label/Some%20Novel}.
     *
     * @throws Invalid when the text is not a Kaori Translation article or label
     */
    public static String normalize(String raw) throws Invalid {
        if (raw == null || raw.trim().isEmpty()) {
            throw new Invalid("URL belum diisi.");
        }
        String text = raw.trim();
        if (!text.contains("://")) text = "https://" + text;
        // A URL pasted out of a chat client often carries literal spaces where the
        // browser would have sent %20 — a label named "Yarikonde ita Game Sekai…"
        // arrives exactly like that. Refusing it as "not a valid URL" would blame
        // the user for something the normaliser can fix, so spaces are escaped
        // before the URI is parsed.
        text = text.replace(" ", "%20");

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

        if (lower.startsWith("/feeds")) {
            throw new Invalid("Itu alamat feed, bukan halaman yang bisa dibuka. "
                    + "Pakai URL artikelnya atau halaman labelnya.");
        }
        if (lower.startsWith("/p/")) {
            throw new Invalid("Itu halaman statis blog, bukan daftar novel. "
                    + "Buka halaman katalognya dulu, lalu salin URL tersebut.");
        }
        if (lower.startsWith("/search/label/")) {
            return normalizeLabel(path);
        }
        if (lower.startsWith("/search")) {
            throw new Invalid("Halaman pencarian tidak bisa dipakai. "
                    + "Buka halaman label atau artikel novelnya.");
        }
        if (!lower.endsWith(".html")) {
            throw new Invalid("URL harus menunjuk ke halaman artikel (.html) atau halaman label "
                    + "(/search/label/...).");
        }

        return "https://" + HOST + path;
    }

    /**
     * Canonical label address: {@code /search/label/<satu spasi jadi %20>}.
     *
     * <p>Pagination and sorting parameters are dropped: the catalogue is read from
     * the label's JSON feed, which is paged by the app itself, so
     * {@code ?updated-max=...&start=20} is noise, not identity.
     */
    private static String normalizeLabel(String path) throws Invalid {
        String rest = path.substring("/search/label/".length());
        while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
        String name = decode(rest).replace('+', ' ').trim();
        if (name.isEmpty()) {
            throw new Invalid("Nama label kosong di URL itu.");
        }
        if (name.length() > 120) {
            throw new Invalid("Nama label terlalu panjang untuk dibaca.");
        }
        return "https://" + HOST + "/search/label/" + encode(name);
    }

    /** True when the normalised address is a label listing. */
    public static boolean isLabel(String normalized) {
        return normalized != null
                && normalized.toLowerCase(Locale.ROOT).contains("/search/label/");
    }

    /** True when the normalised address is a dated article. */
    public static boolean isArticle(String normalized) {
        return normalized != null && !isLabel(normalized) && normalized.contains("/20");
    }

    /** What kind of page the address points at, or {@code null} when unknown. */
    public static Kind kindOf(String normalized) {
        if (isLabel(normalized)) return Kind.LABEL;
        if (isArticle(normalized)) return Kind.ARTICLE;
        return null;
    }

    /**
     * Label name as printed by the source, e.g. {@code Monogatari no Kuromaku}.
     *
     * @throws Invalid when the address is not a label listing
     */
    public static String label(String normalized) throws Invalid {
        if (!isLabel(normalized)) {
            throw new Invalid("Alamat itu bukan halaman label.");
        }
        String marker = "/search/label/";
        int at = normalized.toLowerCase(Locale.ROOT).indexOf(marker);
        String raw = normalized.substring(at + marker.length());
        int query = raw.indexOf('?');
        if (query >= 0) raw = raw.substring(0, query);
        return decode(raw).replace('+', ' ').trim();
    }

    /**
     * Stable id for a normalised address.
     *
     * <p>{@code https://kaoritranslation.blogspot.com/2025/09/ln-foo.html} becomes
     * {@code ln-foo}, and a label page becomes {@code label-yarikonde-ita-...}. The
     * id is what preferences, offline storage and the reader use to keep one
     * novel's data apart from another's, so it only uses plain lowercase
     * characters — and the prefix keeps a label and an article of the same name
     * from colliding.
     */
    public static String id(String normalized) {
        if (normalized == null) return "";
        if (isLabel(normalized)) {
            String name;
            try {
                name = label(normalized);
            } catch (Invalid error) {
                return "";
            }
            String slug = slug(name);
            return slug.isEmpty() ? "" : "label-" + slug;
        }
        String path = normalized;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".html")) name = name.substring(0, name.length() - 5);
        return slug(name);
    }

    private static String slug(String name) {
        String slug = (name == null ? "" : name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-_]", "-")
                .replaceAll("-{2,}", "-");
        while (slug.startsWith("-")) slug = slug.substring(1);
        while (slug.endsWith("-")) slug = slug.substring(0, slug.length() - 1);
        if (slug.length() > 64) slug = slug.substring(0, 64);
        while (slug.endsWith("-")) slug = slug.substring(0, slug.length() - 1);
        return slug;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException error) {
            return value;
        }
    }

    /** Percent-encodes a label the way Blogger does: spaces as {@code %20}. */
    static String encode(String name) {
        StringBuilder out = new StringBuilder();
        for (byte raw : name.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int value = raw & 0xff;
            char character = (char) value;
            boolean plain = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '_' || character == '.' || character == '~';
            if (plain) {
                out.append(character);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", value));
            }
        }
        return out.toString();
    }

    /**
     * Address of the JSON feed that lists every post carrying this label.
     *
     * <p>This is why a label page is the better catalogue: the HTML listing shows
     * twenty posts and hides the rest behind pagination, while the feed can be
     * paged deterministically and returns the label's posts only — never another
     * novel's chapters, never a sidebar link.
     */
    public static String labelFeed(String normalized) throws Invalid {
        String name = label(normalized);
        return "https://" + HOST + "/feeds/posts/summary/-/" + encode(name) + "?alt=json";
    }

    /** True when the text already looks like a normalised address. */
    public static boolean isNormalized(String url) {
        try {
            return url != null && url.equals(normalize(url));
        } catch (Invalid error) {
            return false;
        }
    }
}
