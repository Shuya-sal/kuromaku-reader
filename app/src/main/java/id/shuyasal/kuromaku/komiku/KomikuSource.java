package id.shuyasal.kuromaku.komiku;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pembaca halaman Komiku (Manga Server 3).
 *
 * <p>Semua yang dihasilkan berasal dari HTML yang benar-benar dikirim situs:
 * tidak ada endpoint karangan, tidak ada tebakan alamat gambar. Bila struktur
 * halaman berubah, parser mengembalikan daftar kosong atau melempar
 * {@link SourceException} — bukan data palsu.
 */
public final class KomikuSource {

    /** Kegagalan yang aman ditampilkan ke pengguna. */
    public static final class SourceException extends Exception {
        public SourceException(String message) {
            super(message);
        }
    }

    /** Pengambil HTML; dipisah agar bisa diuji tanpa jaringan. */
    public interface Transport {
        String get(String url) throws IOException, SourceException;
    }

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36 Kuromaku/1.0";

    private static final int MAX_BODY = 4 * 1024 * 1024;

    private final Transport transport;

    public KomikuSource() {
        this.transport = KomikuSource::fetch;
    }

    public KomikuSource(Transport transport) {
        this.transport = transport;
    }

    /** Halaman HTML mentah; dipakai tes dan pemanggil yang butuh dokumen. */
    public String html(String url) throws SourceException {
        try {
            return transport.get(url);
        } catch (IOException error) {
            throw new SourceException("Tidak dapat menghubungi Komiku: " + error.getMessage());
        }
    }

    static String fetch(String url) throws IOException, SourceException {
        if (!KomikuConfig.isAllowed(url)) {
            throw new SourceException("Alamat sumber tidak sah.");
        }
        Request request = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() < 200 || response.code() >= 300) {
                throw new SourceException("Komiku menolak permintaan (HTTP " + response.code() + ").");
            }
            if (body == null) throw new SourceException("Halaman Komiku kosong.");
            String text = body.string();
            if (text.length() > MAX_BODY) throw new SourceException("Halaman terlalu besar.");
            return text;
        }
    }

    // ------------------------------------------------------------- katalog

    /** "Baca Komik Terbaru" di beranda. */
    public List<KomikuManga> latest() throws SourceException {
        return parseList(html(KomikuConfig.BASE + "/"), "#Terbaru article.ls2");
    }

    /** "Peringkat Komiku" (harian). */
    public List<KomikuManga> popular() throws SourceException {
        return parseList(html(KomikuConfig.BASE + "/"), "#Rekomendasi_Komik article.ls4");
    }

    /** "Baru Ditambahkan". */
    public List<KomikuManga> newest() throws SourceException {
        return parseList(html(KomikuConfig.BASE + "/"), "#Baru_Ditambahkan article.ls2");
    }

    /**
     * Pencarian judul.
     *
     * <p>Komiku mengirim hasil pencarian di halaman {@code /?s=}. Bila hasilnya
     * tidak memuat tautan manga (mis. karena situs mengubah cara menampilkan),
     * daftar kosong dikembalikan — layar akan mengatakan tidak ditemukan,
     * bukan menampilkan hasil karangan.
     */
    public List<KomikuManga> search(String query) throws SourceException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        String url = KomikuConfig.BASE + "/?s=" + java.net.URLEncoder.encode(q,
                java.nio.charset.StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(html(url));
        List<KomikuManga> found = parseList(doc, "article.ls2");
        if (found.isEmpty()) found = parseList(doc, "article.ls4");
        if (found.isEmpty()) found = parseAnchors(doc);
        return found;
    }

    /** Halaman perpustakaan (pustaka/page/N) untuk penjelajahan. */
    public List<KomikuManga> library(int page) throws SourceException {
        String url = page <= 1
                ? KomikuConfig.BASE + "/pustaka/"
                : KomikuConfig.BASE + "/pustaka/page/" + page + "/";
        return parseList(html(url), "article.ls4");
    }

    private List<KomikuManga> parseList(String html, String selector) {
        return parseList(Jsoup.parse(html), selector);
    }

    private List<KomikuManga> parseList(Document doc, String selector) {
        List<KomikuManga> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element article : doc.select(selector)) {
            KomikuManga manga = fromArticle(article);
            if (manga == null) continue;
            if (!seen.add(manga.id)) continue;
            out.add(manga);
        }
        return out;
    }

    private KomikuManga fromArticle(Element article) {
        Element link = article.selectFirst("a[href^=/manga/]");
        if (link == null) return null;
        String url = KomikuConfig.absolute(link.attr("href"));
        if (url == null) return null;
        String id = KomikuConfig.mangaId(url);
        if (id.isEmpty()) return null;

        Element titleLink = article.selectFirst("h3 a, h4 a");
        String title = titleLink != null ? titleLink.text() : link.attr("title");
        if (title == null || title.trim().isEmpty()) title = id.replace('-', ' ');

        Element image = article.selectFirst("img[data-src], img[src]");
        String cover = "";
        if (image != null) {
            cover = KomikuConfig.absolute(image.hasAttr("data-src")
                    ? image.attr("data-src") : image.attr("src"));
            if (cover == null) cover = "";
        }

        Element meta = article.selectFirst("span.ls2t, span.ls4s");
        String type = "";
        String updated = "";
        if (meta != null) {
            String text = meta.text();
            String[] parts = text.split("·");
            if (parts.length > 0) type = parts[0].trim();
            if (parts.length > 1) updated = parts[parts.length - 1].trim();
        }

        Element chapter = article.selectFirst("a.ls24");
        String latest = chapter == null ? "" : chapter.text().trim();

        return new KomikuManga(id, title, url, cover, type, latest, updated);
    }

    /** Cadangan: ambil tautan manga apa pun yang punya judul. */
    private List<KomikuManga> parseAnchors(Document doc) {
        List<KomikuManga> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : doc.select("a[href^=/manga/]")) {
            String url = KomikuConfig.absolute(link.attr("href"));
            if (url == null) continue;
            String id = KomikuConfig.mangaId(url);
            if (id.isEmpty() || !seen.add(id)) continue;
            String title = link.attr("title");
            if (title == null || title.trim().isEmpty()) title = link.text();
            if (title == null || title.trim().isEmpty()) continue;
            out.add(new KomikuManga(id, title, url, "", "", "", ""));
        }
        return out;
    }


    // ------------------------------------------------- katalog lengkap

    /** Batas aman jumlah halaman katalog; situs kini berhenti di 807. */
    static final int CATALOG_MAX_PAGES = 1200;

    /**
     * Satu halaman katalog beserta nomor halaman berikutnya.
     *
     * <p>Keduanya dibaca dari satu permintaan: situs menaruh tautan "berikutnya"
     * di ekor fragmen yang sama, jadi meminta halaman dua kali hanya untuk
     * mengetahui nomor berikutnya akan menggandakan beban ke sumber.
     */
    public static final class CatalogPage {
        public final List<KomikuManga> items;
        /** Nomor halaman berikutnya, atau {@code 0} bila sudah halaman terakhir. */
        public final int nextPage;

        CatalogPage(List<KomikuManga> items, int nextPage) {
            this.items = items;
            this.nextPage = nextPage;
        }
    }

    /** Alamat satu halaman katalog; halaman 1 tidak memakai akhiran page. */
    static String catalogPageUrl(int page) {
        int safe = Math.max(1, page);
        return safe == 1
                ? KomikuConfig.API + "/manga/"
                : KomikuConfig.API + "/manga/page/" + safe + "/";
    }

    /**
     * Satu halaman katalog lengkap (10 judul per halaman).
     *
     * <p>Alamat ini adalah bagian dari situs Komiku sendiri (dipakai halaman
     * pustaka untuk memuat berikutnya), bukan endpoint karangan. Fragmen yang
     * datang berupa potongan HTML tanpa {@code <html>}, jadi diurai langsung
     * sebagai fragmen.
     */
    public List<KomikuManga> catalogPage(int page) throws SourceException {
        return catalogPageWithNext(page).items;
    }

    /**
     * Satu halaman katalog sekaligus nomor halaman berikutnya.
     *
     * <p>Fragmen yang datang berupa potongan HTML tanpa {@code <html>}, jadi
     * diurai sebagai fragmen. Alamat ini bagian dari situs Komiku sendiri
     * (dipakai halaman pustaka untuk memuat berikutnya), bukan endpoint
     * karangan.
     */
    public CatalogPage catalogPageWithNext(int page) throws SourceException {
        String fragment = html(catalogPageUrl(page));
        Document doc = Jsoup.parseBodyFragment(fragment);
        List<KomikuManga> parsed = parseCatalogFragment(doc);
        if (parsed.isEmpty()) parsed = parseAnchors(doc);
        return new CatalogPage(parsed, nextPageIn(fragment, page));
    }

    /** Nomor halaman berikutnya di dalam satu fragmen, atau {@code 0}. */
    static int nextPageIn(String fragment, int page) {
        if (fragment == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("manga/page/([0-9]+)/\"").matcher(fragment);
        int highest = 0;
        while (matcher.find()) {
            try {
                int found = Integer.parseInt(matcher.group(1));
                if (found > highest) highest = found;
            } catch (NumberFormatException ignored) {
                // Angka yang tidak terbaca dilewati.
            }
        }
        if (highest <= page) return 0;
        return Math.min(highest, CATALOG_MAX_PAGES);
    }

    /** Kartu katalog: {@code div.bge} berisi tautan judul, cover, dan tipe. */
    private List<KomikuManga> parseCatalogFragment(Document doc) {
        List<KomikuManga> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element card : doc.select("div.bge")) {
            KomikuManga manga = fromCatalogCard(card);
            if (manga == null) continue;
            if (!seen.add(manga.id)) continue;
            out.add(manga);
        }
        return out;
    }

    private KomikuManga fromCatalogCard(Element card) {
        Element link = card.selectFirst("div.kan a[href^=https://komiku.org/manga/], "
                + "div.bgei a[href^=https://komiku.org/manga/]");
        if (link == null) link = card.selectFirst("a[href^=https://komiku.org/manga/]");
        if (link == null) return null;
        String url = KomikuConfig.absolute(link.attr("href"));
        if (url == null) return null;
        String id = KomikuConfig.mangaId(url);
        if (id.isEmpty()) return null;

        Element heading = card.selectFirst("div.kan h3, div.kan h4");
        String title = heading != null ? heading.text() : link.attr("title");
        if (title == null || title.trim().isEmpty()) title = id.replace('-', ' ');

        Element image = card.selectFirst("img[data-src], img[src]");
        String cover = "";
        if (image != null) {
            cover = KomikuConfig.absolute(image.hasAttr("data-src")
                    ? image.attr("data-src") : image.attr("src"));
            if (cover == null) cover = "";
        }

        // "Manhwa Drama" pada tpe1_inf, lalu "Drama | 4 jam lalu | Berwarna".
        String type = "";
        String updated = "";
        Element badge = card.selectFirst("div.tpe1_inf");
        if (badge != null) type = firstWord(badge.text());
        Element meta = card.selectFirst("span.judul2");
        if (meta != null) {
            String[] parts = meta.text().split("\\|");
            if (parts.length > 0 && !parts[0].trim().isEmpty()) type = parts[0].trim();
            if (parts.length > 1) updated = parts[1].trim();
        }

        String latest = "";
        Element newest = card.selectFirst("div.new1 a[title*=Chapter], div.new1 a");
        if (newest != null) latest = newest.text().replace("Terbaru:", "").replace("Awal:", "").trim();

        return new KomikuManga(id, title, url, cover, type, latest, updated);
    }

    private static String firstWord(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    // -------------------------------------------------------------- detail

    /** Detail satu judul beserta daftar chapter (urut seperti di halaman). */
    public KomikuDetail detail(String url) throws SourceException {
        if (!KomikuConfig.isAllowed(url)) throw new SourceException("Alamat detail tidak sah.");
        Document doc = Jsoup.parse(html(url));
        String id = KomikuConfig.mangaId(url);

        String title = cleanTitle(text(doc.selectFirst("h1")));
        if (title.isEmpty()) {
            title = cleanTitle(text(doc.selectFirst("title")).replace(" - Komiku", ""));
        }

        String alternative = "";
        String status = "";
        String type = "";
        List<String> genres = new ArrayList<>();
        for (Element row : doc.select("table.inftable tr")) {
            String label = text(row.selectFirst("td")).toLowerCase(java.util.Locale.ROOT);
            String value = text(row.selectFirst("td:nth-child(2)"));
            if (label.startsWith("judul alternatif") || label.startsWith("alternatif")) {
                alternative = value;
            } else if (label.startsWith("status")) {
                status = value;
            } else if (label.startsWith("tipe")) {
                type = value;
            } else if (label.startsWith("genre")) {
                for (Element genre : row.select("a[href^=/genre/]")) {
                    String name = genre.text().trim();
                    if (!name.isEmpty()) genres.add(name);
                }
            }
        }

        String synopsis = text(doc.selectFirst("p[itemprop=description], p.desc, div.desc"));
        String coverUrl = "";
        Element cover = doc.selectFirst("img[itemprop=image], .ims img, .thumb img");
        if (cover != null) {
            String raw = cover.hasAttr("data-src") ? cover.attr("data-src") : cover.attr("src");
            String absolute = KomikuConfig.absolute(raw);
            if (absolute != null) coverUrl = absolute;
        }
        if (coverUrl.isEmpty()) {
            // Tombol bookmark situs menyimpan alamat cover di atribut data.
            Element bookmark = doc.selectFirst("[data-series-cover]");
            if (bookmark != null) {
                String absolute = KomikuConfig.absolute(bookmark.attr("data-series-cover"));
                if (absolute != null) coverUrl = absolute;
            }
        }

        List<KomikuChapter> chapters = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element row : doc.select("#Daftar_Chapter tr[itemprop=itemListElement]")) {
            Element link = row.selectFirst("a[href]");
            if (link == null) continue;
            String chapterUrl = KomikuConfig.absolute(link.attr("href"));
            if (chapterUrl == null || !seen.add(chapterUrl)) continue;
            String name = text(row.selectFirst("span[itemprop=name]"));
            if (name.isEmpty()) name = link.text();
            String date = text(row.selectFirst("td.tanggalseries"));
            chapters.add(new KomikuChapter(chapterUrl, name, date));
        }

        return new KomikuDetail(id, title, alternative, status, type, genres, synopsis,
                coverUrl, chapters);
    }

    // ------------------------------------------------------------- chapter

    /**
     * Alamat gambar satu chapter, urut sesuai halaman.
     *
     * <p>Hanya gambar dari host aset Komiku yang diambil; banner promosi dan
     * gambar antarmuka situs dibuang. Bila tidak ada satu pun gambar sah,
     * pemanggil menerima daftar kosong dan menampilkan pesan — bukan layar
     * hitam.
     */
    public List<String> images(String chapterUrl) throws SourceException {
        if (!KomikuConfig.isAllowed(chapterUrl)) {
            throw new SourceException("Alamat chapter tidak sah.");
        }
        Document doc = Jsoup.parse(html(chapterUrl));
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Elements images = doc.select("#Baca_Komik img, .chapter-img img, #Baca_Komik img.klazy");
        for (Element image : images) {
            String src = image.hasAttr("data-src") ? image.attr("data-src") : image.attr("src");
            String url = KomikuConfig.absolute(src);
            if (url == null) continue;
            if (!url.contains(KomikuConfig.IMAGE_HOST)) continue;
            if (url.toLowerCase(java.util.Locale.ROOT).contains("promosi")) continue;
            if (!seen.add(url)) continue;
            out.add(url);
        }
        return out;
    }

    /** Buang awalan "Komik " yang dipakai situs pada judul halaman. */
    static String cleanTitle(String raw) {
        if (raw == null) return "";
        String title = raw.trim();
        if (title.toLowerCase(java.util.Locale.ROOT).startsWith("komik ")) {
            title = title.substring("Komik ".length()).trim();
        }
        return title;
    }

    private static String text(Element element) {
        return element == null ? "" : element.text().trim();
    }
}