package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import id.shuyasal.kuromaku.net.Http;

public final class ShinigamiSource {
    public static final class SourceException extends Exception {
        public SourceException(String message) { super(message); }
        public SourceException(String message, Throwable cause) { super(message, cause); }
    }

    public enum Sort {
        LATEST("latest", "Terbaru"), RANK("rank", "Terpopuler");
        public final String wire;
        public final String label;
        Sort(String wire, String label) { this.wire = wire; this.label = label; }
    }

    public static final class Page {
        public final List<ShinigamiManga> items;
        public final int page;
        public final int totalPages;
        public final long totalRecords;
        Page(List<ShinigamiManga> items, int page, int totalPages, long totalRecords) {
            this.items = items; this.page = page; this.totalPages = totalPages;
            this.totalRecords = totalRecords;
        }
    }

    public static final class ChapterPage {
        public final List<ShinigamiChapter> items;
        public final int page;
        public final int totalPages;
        ChapterPage(List<ShinigamiChapter> items, int page, int totalPages) {
            this.items = items; this.page = page; this.totalPages = totalPages;
        }
    }

    public static final class ChapterImages {
        public final String chapterId;
        public final List<String> urls;
        ChapterImages(String chapterId, List<String> urls) {
            this.chapterId = chapterId; this.urls = urls;
        }
    }

    public static final class Genre {
        public final String slug;
        public final String name;
        Genre(String slug, String name) { this.slug = slug; this.name = name; }
    }

    interface Transport { String get(String url) throws IOException, SourceException; }
    private static final int PAGE_SIZE = 24;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).followRedirects(false)
            .followSslRedirects(false).retryOnConnectionFailure(false).build();
    private final ShinigamiConfig config;
    private final ShinigamiStore store;
    private final Transport transport;

    public ShinigamiSource(Context context) {
        config = new ShinigamiConfig(context);
        store = new ShinigamiStore(context);
        transport = this::fetch;
    }

    ShinigamiSource(Context context, Transport transport) {
        config = new ShinigamiConfig(context);
        store = new ShinigamiStore(context);
        this.transport = transport;
    }

    public ShinigamiConfig config() { return config; }

    public Page catalog(int page, Sort sort) throws SourceException {
        return list(page, sort, "", null, null);
    }

    public Page search(String keyword, int page) throws SourceException {
        return list(page, Sort.LATEST, keyword == null ? "" : keyword.trim(), null, null);
    }

    public Page search(String keyword, int page, Sort sort, String genreSlug, String status)
            throws SourceException {
        return list(page, sort, keyword == null ? "" : keyword.trim(), genreSlug, status);
    }

    public Page catalog(int page, Sort sort, String genreSlug, String status) throws SourceException {
        return list(page, sort, "", genreSlug, status);
    }

    private Page list(int page, Sort sort, String keyword, String genreSlug, String status)
            throws SourceException {
        StringBuilder url = new StringBuilder(config.apiBase())
                .append("/manga/list?page=").append(Math.max(1, page))
                .append("&page_size=").append(PAGE_SIZE)
                .append("&sort=").append(sort == null ? Sort.LATEST.wire : sort.wire)
                .append("&sort_order=desc");
        if (keyword != null && !keyword.isEmpty()) url.append("&q=").append(encode(keyword));
        if (genreSlug != null && !genreSlug.isEmpty()) url.append("&genre=").append(encode(genreSlug));
        if (status != null && !status.isEmpty()) url.append("&status=").append(encode(status));
        JSONObject root = getJson(url.toString());
        JSONArray data = requireArray(root);
        LinkedHashMap<String, ShinigamiManga> items = new LinkedHashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) throw changedShape();
            ShinigamiManga manga = parseManga(item);
            if (manga.id.isEmpty() || manga.title.isEmpty()) throw changedShape();
            manga = parseManga(store.mergeMangaMetadata(manga.id, item));
            items.put(manga.id, manga);
        }
        JSONObject meta = root.optJSONObject("meta");
        if (meta == null) throw changedShape();
        return new Page(new ArrayList<>(items.values()), Math.max(1, page),
                Math.max(1, meta.optInt("total_page", 1)), meta.optLong("total_record", items.size()));
    }

    public ShinigamiManga detail(String mangaId) throws SourceException {
        requireId(mangaId, "manga");
        JSONObject manga = firstObject(getJson(config.apiBase() + "/manga/detail/" + encode(mangaId)));
        if (manga == null) throw new SourceException("Detail manga tidak ditemukan atau format sumber berubah.");
        ShinigamiManga parsed = parseManga(manga);
        if (!mangaId.equals(parsed.id) || parsed.title.isEmpty()) throw changedShape();
        return parseManga(store.mergeMangaMetadata(mangaId, manga));
    }

    public ChapterPage chapters(String mangaId, int page) throws SourceException {
        requireId(mangaId, "manga");
        JSONObject root = getJson(config.apiBase() + "/chapter/" + encode(mangaId)
                + "/list?page=" + Math.max(1, page) + "&page_size=" + PAGE_SIZE
                + "&sort_by=chapter_number&sort_order=desc");
        JSONArray data = requireArray(root);
        LinkedHashMap<String, ShinigamiChapter> items = new LinkedHashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) throw changedShape();
            String id = item.optString("chapter_id", "");
            String owner = item.optString("manga_id", mangaId);
            if (id.isEmpty() || !owner.equals(mangaId)) throw changedShape();
            item = store.mergeChapterMetadata(id, item);
            items.put(id, new ShinigamiChapter(id, owner, item.optDouble("chapter_number", 0d),
                    item.optString("chapter_title", ""), item.optString("release_date", ""),
                    safeImage(item.optString("thumbnail_image_url", "")),
                    item.optString("updated_at", "")));
        }
        JSONObject meta = root.optJSONObject("meta");
        if (meta == null) throw changedShape();
        return new ChapterPage(new ArrayList<>(items.values()), Math.max(1, page),
                Math.max(1, meta.optInt("total_page", 1)));
    }

    public ChapterImages chapterImages(String chapterId) throws SourceException {
        requireId(chapterId, "chapter");
        JSONObject chapter = firstObject(getJson(config.apiBase() + "/chapter/detail/" + encode(chapterId)));
        if (chapter == null || !chapterId.equals(chapter.optString("chapter_id", ""))) throw changedShape();
        String base = chapter.optString("base_url", "");
        JSONObject inner = chapter.optJSONObject("chapter");
        if (inner == null) throw changedShape();
        String path = inner.optString("path", "");
        JSONArray files = inner.optJSONArray("data");
        if (files == null || files.length() == 0) {
            throw new SourceException("Gambar chapter tidak tersedia. Sumber mungkin berubah atau membatasi akses.");
        }
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < files.length(); i++) {
            if (!(files.opt(i) instanceof String)) throw changedShape();
            String name = files.optString(i, "");
            String url = joinUrl(base, path, name);
            if (name.isEmpty() || name.contains(":") || !ShinigamiConfig.isValidResource(url)) {
                throw new SourceException("Alamat gambar ditolak: HTTPS atau host sumber tidak valid.");
            }
            urls.add(url);
        }
        store.mergeChapterMetadata(chapterId, chapter);
        store.setChapterPages(chapterId, urls);
        android.util.Log.i("ShinigamiSource", "Chapter image metadata validated; pages=" + urls.size());
        return new ChapterImages(chapterId, urls);
    }

    public List<Genre> genres() throws SourceException {
        JSONArray data = requireArray(getJson(config.apiBase() + "/genre/list"));
        List<Genre> genres = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) throw changedShape();
            String slug = item.optString("slug", "");
            if (!slug.isEmpty()) genres.add(new Genre(slug, item.optString("name", slug)));
        }
        return genres;
    }

    static JSONObject firstObject(JSONObject root) {
        if (root == null) return null;
        Object data = root.opt("data");
        if (data instanceof JSONObject) return (JSONObject) data;
        if (data instanceof JSONArray) return ((JSONArray) data).optJSONObject(0);
        return null;
    }

    static ShinigamiManga parseManga(JSONObject item) {
        String id = item.optString("manga_id", "");
        if (id.isEmpty()) id = item.optString("id", "");
        String cover = item.optString("cover_portrait_url", "");
        if (cover.isEmpty()) cover = item.optString("cover_image_url", "");
        if (cover.isEmpty()) cover = item.optString("cover_image", "");
        JSONObject taxonomy = item.optJSONObject("taxonomy");
        return new ShinigamiManga(id, item.optString("title", ""), item.optString("alternative_title", ""),
                safeImage(cover), item.optString("description", ""), item.optInt("status", -1),
                item.optString("release_year", ""), item.optDouble("user_rate", 0d),
                item.optLong("view_count", 0L), item.optDouble("latest_chapter_number", 0d),
                item.optString("latest_chapter_time", ""), taxonomyNames(taxonomy, "Genre"),
                taxonomyNames(taxonomy, "Author"), taxonomyFirst(taxonomy, "Format"),
                item.optString("updated_at", ""));
    }

    private static String safeImage(String url) {
        return ShinigamiConfig.isValidResource(url) ? url : "";
    }

    private static List<String> taxonomyNames(JSONObject taxonomy, String group) {
        List<String> names = new ArrayList<>();
        JSONArray array = taxonomy == null ? null : taxonomy.optJSONArray(group);
        if (array == null) return names;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            String name = item == null ? "" : item.optString("name", "");
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    private static String taxonomyFirst(JSONObject taxonomy, String group) {
        List<String> names = taxonomyNames(taxonomy, group);
        return names.isEmpty() ? "" : names.get(0);
    }

    private JSONObject getJson(String url) throws SourceException {
        if (!ShinigamiConfig.isValidResource(url)) throw new SourceException("Alamat sumber tidak valid atau bukan HTTPS.");
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new SourceException("Permintaan sumber dibatalkan.");
            try {
                return decodeResponse(200, transport.get(url));
            } catch (IOException error) {
                last = error;
            }
        }
        throw new SourceException("Sumber tidak dapat diakses: koneksi gagal atau waktu tunggu habis. Coba lagi nanti.", last);
    }

    private String fetch(String url) throws IOException, SourceException {
        Request request = new Request.Builder().url(url).header("User-Agent", Http.USER_AGENT)
                .header("Accept", "application/json").build();
        try (Response response = CLIENT.newCall(request).execute()) {
            int code = response.code();
            android.util.Log.i("ShinigamiSource", "Source response HTTP " + code);
            ResponseBody body = response.body();
            if (code < 200 || code >= 300) throw httpFailure(code);
            if (body == null) throw new SourceException("Respons sumber kosong.");
            if (body.contentLength() > 4 * 1024 * 1024) throw new SourceException("Respons sumber terlalu besar.");
            okio.BufferedSource input = body.source();
            if (input.request(4 * 1024 * 1024 + 1L)) throw new SourceException("Respons sumber terlalu besar.");
            return body.string();
        }
    }

    static JSONObject decodeResponse(int status, String body) throws SourceException {
        if (status < 200 || status >= 300) throw httpFailure(status);
        if (body == null || body.trim().isEmpty()) throw new SourceException("Respons sumber kosong.");
        String lower = body.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("<html") && (lower.contains("cf-chl") || lower.contains("just a moment")
                || lower.contains("captcha") || lower.contains("cloudflare"))) {
            throw new SourceException("Sumber memerlukan verifikasi Cloudflare/CAPTCHA. Aplikasi tidak melewati pembatasan ini.");
        }
        JSONObject root;
        try { root = new JSONObject(body); }
        catch (Exception error) { throw new SourceException("Format respons sumber berubah atau bukan JSON yang didukung.", error); }
        if (!root.has("retcode")) throw changedShape();
        int retcode = root.optInt("retcode", -1);
        if (retcode != 0) throw new SourceException("Sumber menolak permintaan (kode " + retcode
                + "). Akses mungkin dibatasi; aplikasi tidak melewati autentikasi atau paywall.");
        return root;
    }

    private static SourceException httpFailure(int status) {
        if (status == 401 || status == 403) return new SourceException("Sumber membatasi akses (HTTP " + status
                + "). Autentikasi, CAPTCHA, atau izin sumber diperlukan; aplikasi tidak melewati pembatasan ini.");
        if (status == 429) return new SourceException("Batas permintaan sumber tercapai (HTTP 429). Tunggu sebelum mencoba lagi.");
        if (status >= 300 && status < 400) return new SourceException("Sumber mengalihkan permintaan. Periksa domain aktif; pengalihan tidak diikuti otomatis.");
        return new SourceException("Sumber tidak dapat diakses (HTTP " + status + "). Coba lagi nanti.");
    }

    private static JSONArray requireArray(JSONObject root) throws SourceException {
        JSONArray data = root.optJSONArray("data");
        if (data == null) throw changedShape();
        return data;
    }

    private static SourceException changedShape() {
        return new SourceException("Struktur data sumber berubah atau tidak lengkap. Konten tidak dapat dibaca dengan aman.");
    }

    private static void requireId(String id, String kind) throws SourceException {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new SourceException("Identitas " + kind + " tidak valid.");
    }

    static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException error) {
            throw new IllegalStateException(error);
        }
    }

    static String joinUrl(String base, String path, String name) {
        String b = base == null ? "" : base;
        String p = path == null ? "" : path;
        String n = name == null ? "" : name;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return b + "/" + (p.isEmpty() ? "" : p + "/") + n;
    }
}
