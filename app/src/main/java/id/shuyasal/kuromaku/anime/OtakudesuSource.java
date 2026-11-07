package id.shuyasal.kuromaku.anime;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pembaca halaman Otakudesu.
 *
 * <p>Semua data diambil dari HTML halaman publik sumber
 * ({@code https://otakudesu.blog/ongoing-anime/} dan turunannya). Tidak ada
 * endpoint API yang dikarang: bila struktur halaman berubah, permintaan gagal
 * dengan pesan jelas alih-alih mengembalikan data palsu. Aplikasi tidak
 * melewati CAPTCHA, autentikasi, DRM, paywall, atau proteksi akses apa pun.
 *
 * <p>Video tidak diekstrak: halaman episode memuat pemutar milik pihak ketiga,
 * jadi aplikasi hanya menawarkan tombol menuju halaman resminya.
 */
public final class OtakudesuSource {

    public static final String BASE = "https://otakudesu.blog";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36 Kuromaku/1.0";
    private static final Pattern EPISODE_NUMBER =
            Pattern.compile("(?i)episode\\s*([0-9]+(?:[.,][0-9]+)?)");

    /** Katalog yang bisa dipilih di beranda. */
    public enum Feed {
        ONGOING("Ongoing", "/ongoing-anime/"),
        COMPLETE("Complete", "/complete-anime/");

        public final String label;
        final String path;
        Feed(String label, String path) { this.label = label; this.path = path; }
    }

    public static final class SourceException extends Exception {
        public SourceException(String message) { super(message); }
        public SourceException(String message, Throwable cause) { super(message, cause); }
    }

    interface Transport {
        String get(String url) throws IOException, SourceException;
    }

    /** Ukuran respons dibatasi agar satu halaman tidak menghabiskan memori. */
    static final int MAX_BODY = 4 * 1024 * 1024;

    private static final OkHttpClient POST_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36 Kuromaku/1.0";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private final Transport transport;

    public OtakudesuSource() {
        this.transport = this::fetch;
    }

    OtakudesuSource(Transport transport) {
        this.transport = transport;
    }

    // ---------------------------------------------------------------- katalog

    public OtakudesuAnime.Page catalog(Feed feed, int page) throws SourceException {
        Feed kind = feed == null ? Feed.ONGOING : feed;
        int wanted = Math.max(1, page);
        String url = BASE + kind.path + (wanted > 1 ? "page/" + wanted + "/" : "");
        return parseCatalog(getHtml(url), wanted);
    }

    /** Satu genre pada halaman sumber, dengan alamatnya sendiri. */
    public static final class Genre {
        public final String slug;
        public final String name;

        public Genre(String slug, String name) {
            this.slug = slug == null ? "" : slug;
            this.name = name == null || name.trim().isEmpty() ? this.slug : name.trim();
        }

        public boolean matches(String other) {
            return other != null && slug.equalsIgnoreCase(other.trim());
        }
    }

    /**
     * Daftar genre yang benar-benar diterbitkan halaman {@code /genre-list/}.
     *
     * <p>Tidak ada daftar genre yang ditanam di aplikasi: kalau sumber
     * menambah atau menghapus genre, menu ikut berubah tanpa pembaruan APK.
     */
    public List<Genre> genres() throws SourceException {
        Document doc = Jsoup.parse(getHtml(BASE + "/genre-list/"));
        java.util.LinkedHashMap<String, Genre> found = new java.util.LinkedHashMap<>();
        for (Element link : doc.select("a[href*=/genres/]")) {
            String href = link.attr("href");
            String slug = genreSlug(href);
            if (slug.isEmpty()) continue;
            String name = link.text().trim();
            if (name.isEmpty()) continue;
            if (name.toLowerCase(java.util.Locale.US).contains("genre")) continue;
            Genre genre = new Genre(slug, name);
            if (!found.containsKey(slug)) found.put(slug, genre);
        }
        if (found.isEmpty()) {
            throw new SourceException("Daftar genre tidak ditemukan pada halaman sumber.");
        }
        List<Genre> list = new ArrayList<>(found.values());
        java.util.Collections.sort(list, (left, right) ->
                left.name.compareToIgnoreCase(right.name));
        return list;
    }

    /** Daftar anime pada satu genre, dengan halaman bernomor seperti katalog. */
    public OtakudesuAnime.Page genre(String slug, int page) throws SourceException {
        String clean = slug == null ? "" : slug.trim().toLowerCase(java.util.Locale.US);
        if (clean.isEmpty()) throw new SourceException("Genre tidak dipilih.");
        int wanted = Math.max(1, page);
        String url = BASE + "/genres/" + clean + "/"
                + (wanted > 1 ? "page/" + wanted + "/" : "");
        return parseGenreList(getHtml(url), wanted);
    }

    /** Ambil slug dari {@code /genres/action/} maupun alamat lengkap. */
    static String genreSlug(String href) {
        if (href == null) return "";
        String text = href.trim();
        int index = text.indexOf("/genres/");
        if (index < 0) return "";
        String rest = text.substring(index + "/genres/".length());
        int slash = rest.indexOf('/');
        if (slash >= 0) rest = rest.substring(0, slash);
        int query = rest.indexOf('?');
        if (query >= 0) rest = rest.substring(0, query);
        return rest.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(java.util.Locale.US);
    }

    /**
     * Urai kartu anime pada halaman genre.
     *
     * <p>Markup halaman genre berbeda dari katalog (kartu memakai
     * {@code .col-anime-con}, bukan blok katalog), jadi diurai sendiri. Judul,
     * poster, studio, jumlah episode, dan genre diambil apa adanya.
     */
    OtakudesuAnime.Page parseGenreList(String html, int page) throws SourceException {
        Document doc = Jsoup.parse(html);
        List<OtakudesuAnime> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Element card : doc.select("div.col-anime-con")) {
            Element link = card.selectFirst(".col-anime-title a[href]");
            if (link == null) continue;
            String href = link.absUrl("href");
            if (href.isEmpty()) href = link.attr("href");
            String id = animeId(href);
            String title = link.text().trim();
            if (id.isEmpty() || title.isEmpty() || !seen.add(id)) continue;

            Element cover = card.selectFirst(".col-anime-cover img");
            String poster = cover == null ? "" : cover.absUrl("src");
            if (poster.isEmpty() && cover != null) poster = cover.attr("src");

            Element studio = card.selectFirst(".col-anime-studio");
            Element eps = card.selectFirst(".col-anime-eps");
            Element date = card.selectFirst(".col-anime-date");
            List<String> genres = new ArrayList<>();
            for (Element tag : card.select(".col-anime-genre a")) {
                String name = tag.text().trim();
                if (!name.isEmpty()) genres.add(name);
            }
            String latest = eps == null ? "" : eps.text().trim();
            if (studio != null && !studio.text().trim().isEmpty()) {
                latest = latest.isEmpty() ? studio.text().trim()
                        : latest + " · " + studio.text().trim();
            }
            String updated = date == null ? "" : date.text().trim();
            items.add(new OtakudesuAnime(id, title, href, poster, latest, "",
                    updated, genres));
        }
        if (items.isEmpty() && doc.select("div.col-anime-con").isEmpty()) {
            throw new SourceException("Genre ini tidak memuat daftar anime.");
        }
        int total = totalPages(doc, page);
        return new OtakudesuAnime.Page(items, page, Math.max(page, total));
    }

    public OtakudesuAnime.Page search(String query, int page) throws SourceException {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) return new OtakudesuAnime.Page(new ArrayList<OtakudesuAnime>(), 1, 1);
        // Sumber hanya melayani pencarian di halaman pertama dan membatasi
        // hasilnya ("Max. hanya sampai 12 hasil"); /page/N/?s= menjawab 403.
        String url = BASE + "/?s=" + encode(keyword) + "&post_type=anime";
        return parseSearch(getHtml(url), 1);
    }

    /** Jadwal rilis mingguan: item dengan {@code day} berisi nama hari. */
    public List<OtakudesuAnime> schedule() throws SourceException {
        Document doc = Jsoup.parse(getHtml(BASE + "/jadwal-rilis/"));
        List<OtakudesuAnime> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Element block : doc.select("div.kglist321")) {
            Element heading = block.selectFirst("h2");
            String day = heading == null ? "" : heading.text().trim();
            for (Element link : block.select("ul li a[href]")) {
                String href = link.absUrl("href");
                if (href.isEmpty()) href = link.attr("href");
                String id = animeId(href);
                if (id.isEmpty() || !seen.add(id)) continue;
                items.add(new OtakudesuAnime(id, link.text().trim(), href, "",
                        "", day, "", new ArrayList<String>()));
            }
        }
        if (items.isEmpty() && doc.select("div.kglist321").isEmpty()) {
            throw new SourceException("Jadwal rilis tidak ditemukan pada halaman sumber.");
        }
        return items;
    }

    OtakudesuAnime.Page parseCatalog(String html, int page) throws SourceException {
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select("div.venz div.detpost");
        if (cards.isEmpty()) {
            if (doc.select("div.venz").isEmpty()) {
                throw new SourceException(
                        "Katalog tidak dikenali: struktur halaman sumber berubah atau akses dibatasi.");
            }
            return new OtakudesuAnime.Page(new ArrayList<OtakudesuAnime>(), page, page);
        }
        LinkedHashMap<String, OtakudesuAnime> unique = new LinkedHashMap<>();
        for (Element card : cards) {
            Element link = card.selectFirst("div.thumb a[href]");
            if (link == null) continue;
            String href = link.absUrl("href");
            if (href.isEmpty()) href = link.attr("href");
            String id = animeId(href);
            if (id.isEmpty() || unique.containsKey(id)) continue;
            Element image = card.selectFirst("div.thumbz img");
            Element title = card.selectFirst("h2.jdlflm");
            Element episode = card.selectFirst("div.epz");
            Element day = card.selectFirst("div.epztipe");
            Element updated = card.selectFirst("div.newnime");
            unique.put(id, new OtakudesuAnime(id,
                    title == null ? "" : title.text().trim(),
                    href,
                    image == null ? "" : absolute(image, "src"),
                    episode == null ? "" : episode.text().trim(),
                    day == null ? "" : day.text().trim(),
                    updated == null ? "" : updated.text().trim(),
                    new ArrayList<String>()));
        }
        return new OtakudesuAnime.Page(new ArrayList<>(unique.values()), page, totalPages(doc, page));
    }

    OtakudesuAnime.Page parseSearch(String html, int page) throws SourceException {
        Document doc = Jsoup.parse(html);
        Elements results = doc.select("ul.chivsrc li");
        LinkedHashMap<String, OtakudesuAnime> unique = new LinkedHashMap<>();
        for (Element item : results) {
            Element link = item.selectFirst("h2 a[href]");
            if (link == null) continue;
            String href = link.absUrl("href");
            if (href.isEmpty()) href = link.attr("href");
            String id = animeId(href);
            if (id.isEmpty() || unique.containsKey(id)) continue;
            Element image = item.selectFirst("img");
            List<String> genres = new ArrayList<>();
            for (Element genre : item.select("div.set a[href]")) {
                String name = genre.text().trim();
                if (!name.isEmpty()) genres.add(name);
            }
            unique.put(id, new OtakudesuAnime(id, link.text().trim(), href,
                    image == null ? "" : absolute(image, "src"),
                    "", "", "", genres));
        }
        if (unique.isEmpty() && doc.select("ul.chivsrc").isEmpty()) {
            throw new SourceException("Hasil pencarian tidak dikenali pada halaman sumber.");
        }
        return new OtakudesuAnime.Page(new ArrayList<>(unique.values()), page, totalPages(doc, page));
    }

    // ----------------------------------------------------------------- detail

    public OtakudesuAnime.Detail detail(String animeUrlOrId) throws SourceException {
        String url = animeUrlOrId == null ? "" : animeUrlOrId.trim();
        if (url.isEmpty()) throw new SourceException("Alamat anime kosong.");
        if (!url.startsWith("http")) url = BASE + "/anime/" + url + "/";
        if (!url.startsWith("https://")) {
            throw new SourceException("Alamat sumber harus HTTPS.");
        }
        if (!url.startsWith(BASE)) throw new SourceException("Alamat anime bukan dari sumber yang dikenal.");
        Document doc = Jsoup.parse(getHtml(url));

        Element titleNode = doc.selectFirst("div.jdlrx h1");
        if (titleNode == null) {
            throw new SourceException("Detail anime tidak dikenali: halaman sumber berubah atau akses dibatasi.");
        }
        Element poster = doc.selectFirst("div.fotoanime img");
        String animeId = animeId(url);

        // Sinopsis ada di div.sinopc (saudara div.infozin), bukan di dalam daftar info.
        String synopsis = paragraphs(doc.select("div.sinopc p"));
        if (synopsis.isEmpty()) {
            Element info = doc.selectFirst("div.infozin");
            if (info != null) {
                StringBuilder text = new StringBuilder();
                for (Element paragraph : info.select("p")) {
                    if (paragraph.parents().stream()
                            .anyMatch(parent -> parent.hasClass("infozingle"))) continue;
                    String line = paragraph.text().trim();
                    if (line.isEmpty()) continue;
                    if (text.length() > 0) text.append("\n\n");
                    text.append(line);
                }
                synopsis = text.toString();
            }
        }

        String status = infoValue(doc, "Status");
        String studio = infoValue(doc, "Studio");
        String total = infoValue(doc, "Total Episode");
        String released = infoValue(doc, "Tanggal Rilis");
        List<String> genres = new ArrayList<>();
        for (Element link : doc.select(".infozingle a[rel=tag]")) {
            String name = link.text().trim();
            if (!name.isEmpty()) genres.add(name);
        }

        OtakudesuAnime summary = new OtakudesuAnime(animeId,
                titleNode.text().replaceFirst("(?i)\\s*subtitle indonesia\\s*$", "").trim(),
                url,
                poster == null ? "" : absolute(poster, "src"),
                "",
                "", released, genres);

        return new OtakudesuAnime.Detail(summary, synopsis, status, studio, total, released,
                parseEpisodes(doc));
    }

    List<OtakudesuAnime.Episode> parseEpisodes(Document doc) {
        LinkedHashMap<String, OtakudesuAnime.Episode> unique = new LinkedHashMap<>();
        for (Element block : doc.select("div.episodelist")) {
            for (Element item : block.select("ul li")) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;
                String href = link.absUrl("href");
                if (href.isEmpty()) href = link.attr("href");
                if (href.isEmpty() || unique.containsKey(href)) continue;
                String label = link.text().trim();
                Element date = item.selectFirst("span.zeebr");
                unique.put(href, new OtakudesuAnime.Episode(episodeId(href), label, href,
                        date == null ? "" : date.text().trim(), episodeNumber(label)));
            }
        }
        return new ArrayList<>(unique.values());
    }

    // ----------------------------------------------------------------- helper

    static double episodeNumber(String label) {
        if (label == null) return -1d;
        Matcher matcher = EPISODE_NUMBER.matcher(label);
        if (!matcher.find()) return -1d;
        try {
            return Double.parseDouble(matcher.group(1).replace(',', '.'));
        } catch (NumberFormatException error) {
            return -1d;
        }
    }

    /** Slug halaman anime sebagai ID stabil; kosong bila URL tidak dikenali. */
    static String animeId(String url) {
        if (url == null) return "";
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int index = path.indexOf("/anime/");
        if (index < 0) return "";
        String slug = path.substring(index + "/anime/".length());
        if (slug.contains("/")) return "";
        return slug.matches("[a-z0-9-]{2,120}") ? slug : "";
    }

    /** Slug halaman episode sebagai ID stabil; jatuh ke URL penuh bila aneh. */
    static String episodeId(String url) {
        if (url == null || url.isEmpty()) return "";
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int index = path.indexOf("/episode/");
        if (index < 0) return url;
        String slug = path.substring(index + "/episode/".length());
        if (slug.isEmpty() || slug.contains("/") || slug.contains("..")) return url;
        return slug;
    }

    private static String paragraphs(Elements nodes) {
        StringBuilder text = new StringBuilder();
        for (Element paragraph : nodes) {
            String line = paragraph.text().trim();
            if (line.isEmpty()) continue;
            if (text.length() > 0) text.append("\n\n");
            text.append(line);
        }
        return text.toString();
    }

    private static String infoValue(Document doc, String label) {
        for (Element span : doc.select("div.infozingle p span")) {
            Element bold = span.selectFirst("b");
            if (bold == null) continue;
            if (!bold.text().replace(":", "").trim().equalsIgnoreCase(label)) continue;
            String whole = span.text();
            int colon = whole.indexOf(':');
            return colon >= 0 ? whole.substring(colon + 1).trim() : "";
        }
        return "";
    }

    private static String absolute(Element element, String attribute) {
        String raw = element.absUrl(attribute);
        if (raw == null || raw.isEmpty()) raw = element.attr(attribute);
        return raw;
    }

    private static int totalPages(Document doc, int current) {
        int max = Math.max(1, current);
        for (Element link : doc.select("div.pagination a.page-numbers")) {
            String text = link.text().trim();
            try {
                max = Math.max(max, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                // "«", "»", atau elipsis: bukan nomor halaman.
            }
        }
        return max;
    }

    /** HTML mentah satu halaman; dipakai pemeriksa sumber video. */
    public String html(String url) throws SourceException {
        return getHtml(url);
    }

    /** HTML halaman apa pun (termasuk halaman penyedia) untuk pemeriksaan. */
    public static String fetchHtml(String url) throws SourceException {
        if (url == null || !url.startsWith("https://")) {
            throw new SourceException("Alamat sumber harus HTTPS.");
        }
        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build();
            try (Response response = POST_CLIENT.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (response.code() < 200 || response.code() >= 300) {
                    throw new SourceException("Sumber menolak permintaan (HTTP "
                            + response.code() + ").");
                }
                if (body == null) throw new SourceException("Respons sumber kosong.");
                String text = body.string();
                if (text.length() > MAX_BODY) throw new SourceException("Halaman terlalu besar.");
                return text;
            }
        } catch (IOException error) {
            throw new SourceException(
                    "Sumber tidak dapat diakses: koneksi gagal atau waktu tunggu habis.", error);
        }
    }

    /**
     * POST form ke halaman sumber, dipakai untuk memilih mirror seperti yang
     * dilakukan halaman episode. Tidak ada token atau kredensial yang dikarang:
     * parameter dan nilainya sama dengan yang dikirim halaman itu sendiri.
     */
    public static String postForm(String url, List<String[]> fields) throws SourceException {
        if (url == null || !url.startsWith("https://")) {
            throw new SourceException("Alamat sumber harus HTTPS.");
        }
        okhttp3.FormBody.Builder form = new okhttp3.FormBody.Builder();
        if (fields != null) {
            for (String[] field : fields) {
                if (field == null || field.length < 2) continue;
                form.add(field[0], field[1] == null ? "" : field[1]);
            }
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_UA)
                .header("Referer", BASE + "/")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(form.build())
                .build();
        try (Response response = POST_CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() < 200 || response.code() >= 300) {
                throw new SourceException("Sumber menolak permintaan (HTTP "
                        + response.code() + ").");
            }
            if (body == null) throw new SourceException("Respons sumber kosong.");
            String text = body.string();
            if (text.length() > MAX_BODY) throw new SourceException("Respons terlalu besar.");
            return text;
        } catch (IOException error) {
            throw new SourceException(
                    "Sumber tidak dapat diakses: koneksi gagal atau waktu tunggu habis.", error);
        }
    }

    private String getHtml(String url) throws SourceException {
        if (url == null || !url.startsWith("https://")) {
            throw new SourceException("Alamat sumber harus HTTPS.");
        }
        try {
            String body = transport.get(url);
            if (body == null || body.trim().isEmpty()) {
                throw new SourceException("Halaman sumber kosong.");
            }
            String lower = body.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("just a moment") || lower.contains("cf-chl")
                    || lower.contains("captcha")) {
                throw new SourceException(
                        "Sumber meminta verifikasi (CAPTCHA/Cloudflare). Aplikasi tidak melewati pembatasan ini.");
            }
            return body;
        } catch (IOException error) {
            throw new SourceException(
                    "Sumber tidak dapat diakses: koneksi gagal atau waktu tunggu habis.", error);
        }
    }

    private String fetch(String url) throws IOException, SourceException {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            int code = response.code();
            ResponseBody body = response.body();
            if (code < 200 || code >= 300) {
                throw new SourceException("Sumber menolak permintaan (HTTP " + code + ").");
            }
            if (body == null) throw new SourceException("Respons sumber kosong.");
            if (body.contentLength() > MAX_BODY) throw new SourceException("Halaman terlalu besar.");
            String html = body.string();
            if (html.length() > MAX_BODY) throw new SourceException("Halaman terlalu besar.");
            return html;
        }
    }

    static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException error) {
            throw new IllegalStateException(error);
        }
    }
}