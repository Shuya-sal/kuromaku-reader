package id.shuyasal.kuromaku.anime;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Memeriksa satu halaman episode dan memisahkan tiga hal:
 *
 * <ol>
 *   <li>alamat media yang benar-benar bisa dibuka Media3 (mp4/m3u8/mpd) —
 *       hanya ini yang diberikan ke ExoPlayer;</li>
 *   <li>alamat media yang dipublikasikan halaman portofolio mirror milik
 *       sumber. Halaman episode menyebut portofolio itu sebagai teks, dan berkas
 *       di dalamnya terbuka tanpa autentikasi — diperlakukan sebagai izin yang
 *       sama seperti browser: tidak ada pola URL yang ditebak, tidak ada
 *       enkripsi yang dibalik, tidak ada CAPTCHA yang dilewati;</li>
 *   <li>mirror yang hanya berupa pemutar beriklan (vidhide terenkripsi, mega,
 *       ondesu) — alamatnya tidak pernah diambil.</li>
 * </ol>
 *
 * <p>Urutannya disengaja: portofolio diperiksa lebih dulu karena daftar mirror
 * di halaman episode hanya memuat nama penyedia di dalam {@code data-content},
 * bukan alamat media. Berkas yang sama sudah dipublikasikan terbuka di
 * portofolio, jadi tidak perlu memilih mirror beriklan untuk mendapatkannya.
 *
 * <p>Yang tidak pernah dilakukan resolver: menebak pola URL, membuka kunci RSA
 * atau AES milik penyedia, melewati CAPTCHA/DRM/token, atau memakai kredensial
 * pengguna.
 */
public final class OtakudesuMediaResolver {

    /** Yang dianggap media langsung. Query string diperbolehkan. */
    private static final Pattern MEDIA_URL = Pattern.compile(
            "https://[^\"'\\s<>()]+?\\.(m3u8|mpd|mp4|webm|m4v)(\\?[^\"'\\s<>()]*)?");

    /** URL media di dalam berkas JavaScript (escape {@code \/} ditangani). */
    private static final Pattern JS_MEDIA_URL = Pattern.compile(
            "https://[^\"'\\s<>()\\\\]+?\\.(m3u8|mpd|mp4|webm|m4v)(\\?[^\"'\\s<>()\\\\]*)?");

    private static final Pattern MIRROR_BASE64 =
            Pattern.compile("[A-Za-z0-9+/=]{16,}");

    /**
     * Halaman yang memuat daftar portofolio satu judul. Sumber ini memakainya
     * {@code /episode/<slug>/batch/}; halaman itu menyebut berkas media langsung
     * tanpa iframe pemutar. Bila sumber tidak punya halaman seperti itu,
     * daftarnya kosong dan pemutar jatuh ke pilihan lain.
     */
    private static final String PORTFOLIO_PATH = "/batch/";

    /** Batas halaman portofolio yang diperiksa, agar satu episode tidak jadi puluhan request. */
    private static final int MAX_PORTFOLIO_LINKS = 4;

    private OtakudesuMediaResolver() {
    }

    /** Pengambil HTML untuk halaman kedua (portofolio). */
    public interface Fetcher {
        String get(String url) throws IOException, OtakudesuSource.SourceException;
    }

    /** Fetcher asli lewat jaringan; inilah yang dipakai aplikasi. */
    public static Fetcher networkFetcher() {
        return OtakudesuSource::fetchHtml;
    }

    public static OtakudesuPlayback resolve(OtakudesuSource.Transport transport, String episodeUrl)
            throws OtakudesuSource.SourceException {
        return resolve(transport, episodeUrl, null);
    }

    public static OtakudesuPlayback resolve(OtakudesuSource.Transport transport, String episodeUrl,
                                            Fetcher fetcher)
            throws OtakudesuSource.SourceException {
        if (transport == null || episodeUrl == null || episodeUrl.isEmpty()) {
            return new OtakudesuPlayback(null, null);
        }
        if (!episodeUrl.startsWith("https://")) {
            throw new OtakudesuSource.SourceException("Alamat episode harus HTTPS.");
        }
        String html;
        try {
            html = transport.get(episodeUrl);
        } catch (IOException error) {
            throw new OtakudesuSource.SourceException(
                    "Halaman episode tidak dapat diakses: koneksi gagal atau waktu tunggu habis.",
                    error);
        }
        return resolveHtml(html, fetcher == null ? networkFetcher() : fetcher, episodeUrl);
    }

    /** Bagian tanpa jaringan: hanya halaman episode sendiri yang dibaca. */
    public static OtakudesuPlayback resolveHtml(String html) {
        return resolveHtml(html, null);
    }

    /**
     * Pemeriksaan penuh: halaman episode dibaca, lalu portofolio mirror yang
     * disebut di dalamnya ikut diperiksa. Kegagalan satu portofolio tidak
     * membatalkan hasil halaman episode.
     */
    public static OtakudesuPlayback resolveHtml(String html, Fetcher fetcher) {
        return resolveHtml(html, fetcher, null);
    }

    public static OtakudesuPlayback resolveHtml(String html, Fetcher fetcher, String episodeUrl) {
        if (html == null || html.trim().isEmpty()) {
            return new OtakudesuPlayback(null, null);
        }
        Document doc = Jsoup.parse(html);
        LinkedHashMap<String, OtakudesuPlayback.Source> sources = new LinkedHashMap<>();

        collectFromDocument(doc, sources, null);
        collectPortfolioLinks(doc, episodeUrl, sources, fetcher);

        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(doc);
        return new OtakudesuPlayback(new ArrayList<>(sources.values()), mirrors,
                fallbacksFrom(mirrors));
    }

    // --------------------------------------------------------- halaman episode

    /** Semua alamat media yang ditulis satu halaman. */
    static void collectFromDocument(Document doc,
                                    LinkedHashMap<String, OtakudesuPlayback.Source> sources,
                                    String quality) {
        // 1) Tag media eksplisit: <video src>, <video><source>, <audio src>.
        collect(sources, doc.select("video[src]"), "src", quality);
        collect(sources, doc.select("video source[src]"), "src", quality);
        collect(sources, doc.select("audio[src]"), "src", quality);

        // 2) Metadata Open Graph yang menunjuk berkas media.
        collect(sources, doc.select("meta[property=og:video], meta[property=og:video:url],"
                + " meta[property=og:video:secure_url]"), "content", quality);

        // 3) JSON-LD VideoObject.contentUrl.
        for (Element script : doc.select("script[type=application/ld+json]")) {
            Matcher matcher = MEDIA_URL.matcher(script.data());
            while (matcher.find()) add(sources, matcher.group(), quality);
        }

        // 4) Tautan mentah ke berkas media di dalam HTML.
        Matcher matcher = MEDIA_URL.matcher(doc.html());
        while (matcher.find()) add(sources, matcher.group(), quality);

        for (Element anchor : doc.select("a[href]")) {
            String href = anchor.absUrl("href");
            if (href.isEmpty()) href = anchor.attr("href");
            if (!MEDIA_URL.matcher(href).matches()) continue;
            add(sources, href, quality == null ? anchor.text().trim() : quality);
        }

        // 5) Pemutar yang menyimpan alamat berkas di dalam skripnya sendiri.
        collectFromScripts(doc, sources, quality);
    }

    /**
     * Pemutar memuat alamat berkas di dalam JavaScript. Kalau halaman itu memang
     * membawa pemutarnya sendiri — bukan iframe penyedia — alamatnya dipakai apa
     * adanya; tidak ada yang dibalik, didekripsi, atau ditebak.
     */
    static void collectFromScripts(Document doc,
                                   LinkedHashMap<String, OtakudesuPlayback.Source> sources,
                                   String quality) {
        for (Element script : doc.select("script")) {
            String data = script.data();
            if (data == null || data.isEmpty()) continue;
            Matcher matcher = JS_MEDIA_URL.matcher(data);
            while (matcher.find()) add(sources, matcher.group(), quality);
        }
    }

    // ------------------------------------------------------------- portofolio

    /**
     * Tautan portofolio mirror di halaman episode (teks "Download / Streaming"),
     * hanya yang berada di domain sumber.
     */
    static List<String> portfolioLinks(Document doc) {
        return portfolioLinks(doc, null);
    }

    /**
     * Alamat halaman portofolio: tautan yang benar-benar ada di halaman episode,
     * ditambah alamat yang diturunkan dari alamat episode itu sendiri
     * ({@code /episode/<slug>/} → {@code /episode/<slug>/batch/}). Yang kedua
     * bukan tebakan endpoint: itu jalur yang sama dengan yang dipakai halaman
     * batch untuk kembali ke episodenya, dan hanya diambil dari alamat episode
     * yang sudah dikenal.
     */
    static List<String> portfolioLinks(Document doc, String episodeUrl) {
        List<String> links = new ArrayList<>();
        if (doc != null) {
            for (Element anchor : doc.select("a[href]")) {
                String href = anchor.absUrl("href");
                if (href.isEmpty()) href = anchor.attr("href");
                if (!isSameSource(href)) continue;
                if (!href.endsWith(PORTFOLIO_PATH)) continue;
                if (!links.contains(href)) links.add(href);
            }
        }
        String derived = batchUrlFor(episodeUrl);
        if (!derived.isEmpty() && !links.contains(derived)) links.add(0, derived);
        return links;
    }

    /** True bila alamat berada di domain sumber yang sama dengan episode. */
    static boolean isSameSource(String url) {
        return url != null && url.startsWith(OtakudesuSource.BASE);
    }

    /** {@code …/episode/<slug>/} → {@code …/episode/<slug>/batch/}. */
    static String batchUrlFor(String episodeUrl) {
        if (episodeUrl == null) return "";
        String url = episodeUrl.trim();
        if (!url.startsWith(OtakudesuSource.BASE)) return "";
        if (!url.contains("/episode/")) return "";
        if (url.contains("?")) url = url.substring(0, url.indexOf('?'));
        if (url.endsWith(PORTFOLIO_PATH)) return url;
        if (!url.endsWith("/")) url = url + "/";
        return url + "batch/";
    }

    private static void collectPortfolioLinks(Document doc, String episodeUrl,
                                              LinkedHashMap<String, OtakudesuPlayback.Source> sources,
                                              Fetcher fetcher) {
        if (fetcher == null) return;
        List<String> links = portfolioLinks(doc, episodeUrl);
        int examined = 0;
        for (String link : links) {
            if (examined++ >= MAX_PORTFOLIO_LINKS) break;
            try {
                String html = fetcher.get(link);
                if (html == null || html.trim().isEmpty()) continue;
                Document portfolio = Jsoup.parse(html);
                collectFromDocument(portfolio, sources, null);
                collectQualityColumns(portfolio, sources);
            } catch (Exception ignored) {
                // Satu portofolio gagal tidak membatalkan yang lain.
            }
        }
    }

    /**
     * Portofolio biasanya menyusun tautan per kolom kualitas (360p/480p/720p).
     * Label diambil dari teks terdekat agar pemilih kualitas hanya menampilkan
     * angka yang benar-benar ada di sumber.
     */
    static void collectQualityColumns(Document doc,
                                      LinkedHashMap<String, OtakudesuPlayback.Source> sources) {
        Pattern quality = Pattern.compile("(?i)\\b(360|480|720|1080|144)p\\b");
        Elements blocks = doc.select("div, section, ul, table");
        for (Element block : blocks) {
            Matcher matcher = quality.matcher(block.ownText());
            if (!matcher.find()) continue;
            String label = matcher.group(1) + "p";
            for (Element anchor : block.select("a[href]")) {
                String href = anchor.absUrl("href");
                if (href.isEmpty()) href = anchor.attr("href");
                add(sources, href, label);
            }
        }
    }

    // -------------------------------------------------------------- penyaring

    private static void collect(LinkedHashMap<String, OtakudesuPlayback.Source> sources,
                                Elements nodes, String attribute, String quality) {
        for (Element node : nodes) {
            String raw = node.absUrl(attribute);
            if (raw.isEmpty()) raw = node.attr(attribute);
            add(sources, raw, quality);
        }
    }

    private static void add(LinkedHashMap<String, OtakudesuPlayback.Source> sources,
                            String url, String quality) {
        String clean = normalize(url);
        if (clean.isEmpty()) return;
        if (looksLikeProviderPage(clean)) return;
        OtakudesuPlayback.Kind kind = kindOf(clean);
        if (kind == null) return;
        String label = quality == null ? "" : quality.trim();
        if (sources.containsKey(clean)) return;
        sources.put(clean, new OtakudesuPlayback.Source(clean, kind, label));
    }

    /** Hanya https, tanpa fragmen, tanpa kredensial di URL. */
    static String normalize(String url) {
        if (url == null) return "";
        String value = url.trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("https://")) return "";
        value = value.replace("\\/", "/");
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        if (value.contains("@")) return "";
        if (value.contains("..")) return "";
        return value;
    }

    static OtakudesuPlayback.Kind kindOf(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String path = lower;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (path.endsWith(".m3u8")) return OtakudesuPlayback.Kind.HLS;
        if (path.endsWith(".mpd")) return OtakudesuPlayback.Kind.DASH;
        if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v")) {
            return OtakudesuPlayback.Kind.PROGRESSIVE;
        }
        return null;
    }

    /**
     * Alamat pemutar penyedia, bukan berkas media. Ditolak walaupun berakhiran
     * .mp4 di query, karena memuatnya berarti menjalankan halaman, bukan video.
     */
    static boolean looksLikeProviderPage(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        String[] providers = {"filedon", "vidhide", "vidhidepro", "mega.nz", "mega.co",
                "desustream", "desudrive", "ondesuhd", "odstream", "link.desustream",
                "bit.ly", "shrinkme", "anichin", "player."};
        for (String provider : providers) {
            if (lower.contains(provider)) return true;
        }
        return false;
    }

    /**
     * Kandidat cadangan dari daftar mirror: pilih kualitas tertinggi yang
     * providernya bukan pemutar beriklan (bukan vidhide, bukan mega), lalu
     * ambil dua pertamanya. Kualitas lain diabaikan agar tidak menumpuk
     * permintaan ke penyedia.
     */
    static List<OtakudesuPlayback.Fallback> fallbacksFrom(List<OtakudesuPlayback.Mirror> mirrors) {
        List<OtakudesuPlayback.Fallback> open = new ArrayList<>();
        int best = -1;
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            int quality = qualityRank(mirror.quality);
            if (quality < 0 || isAdGated(mirror.provider)) continue;
            if (quality > best) best = quality;
        }
        if (best < 0) return open;
        for (int i = 0; i < mirrors.size(); i++) {
            OtakudesuPlayback.Mirror mirror = mirrors.get(i);
            if (qualityRank(mirror.quality) != best) continue;
            if (isAdGated(mirror.provider)) continue;
            open.add(new OtakudesuPlayback.Fallback(mirror.quality, mirror.provider, i));
            if (open.size() >= 2) break;
        }
        return open;
    }

    /** 360 -> 1, 480 -> 2, 720 -> 3, 1080 -> 4; -1 bila tidak disebut. */
    static int qualityRank(String quality) {
        if (quality == null) return -1;
        String value = quality.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("p")) value = value.substring(0, value.length() - 1);
        try {
            int height = Integer.parseInt(value);
            if (height <= 0) return -1;
            return height;
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /** Provider yang pemutarnya sendiri beriklan dan menutup berkasnya. */
    static boolean isAdGated(String provider) {
        if (provider == null) return true;
        String lower = provider.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return true;
        return lower.contains("vidhide") || lower.contains("mega");
    }

}