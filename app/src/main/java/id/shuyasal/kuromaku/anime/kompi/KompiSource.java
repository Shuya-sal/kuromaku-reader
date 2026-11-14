package id.shuyasal.kuromaku.anime.kompi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pembaca Animekompi.
 *
 * <p>Situsnya aplikasi satu halaman: seluruh isi datang dari {@code /api/...}
 * berbentuk JSON, jadi tidak ada HTML yang perlu diurai. Data diambil apa
 * adanya — bila balasan berubah bentuk, permintaan gagal dengan pesan jelas
 * alih-alih mengembalikan daftar kosong yang tampak wajar.
 *
 * <p>Hanya dua jenis server yang memberi berkas langsung: Pixel HD (pixeldrain,
 * punya API berkas resmi) dan Mp4upload (alamat video ada di halaman
 * pemutarnya). Sisanya halaman pemutar pihak ketiga dan tidak pernah dianggap
 * sebagai video. Tidak ada DRM, token, atau proteksi yang dilewati.
 */
public class KompiSource {

    public static final String BASE = "https://v11.animekompi.sbs";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36 Kuromaku/1.0";

    /** Batas ukuran balasan agar satu halaman tidak menghabiskan memori. */
    static final int MAX_BODY = 4 * 1024 * 1024;

    private static final Pattern MP4UPLOAD_FILE =
            Pattern.compile("src\\s*:\\s*[\"'](https://[^\"']+\\.mp4[^\"']*)[\"']");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public static final class SourceException extends Exception {
        public SourceException(String message) { super(message); }
        public SourceException(String message, Throwable cause) { super(message, cause); }
    }

    /** Pengambil halaman; pengujian menggantinya dengan data luring. */
    interface Transport {
        String get(String url, String referer) throws IOException, SourceException;
    }

    public static final class Item {
        public final String slug;
        public final String title;
        public final String cover;
        public final String note;

        Item(String slug, String title, String cover, String note) {
            this.slug = slug;
            this.title = title;
            this.cover = cover;
            this.note = note;
        }
    }

    public static final class Section {
        public final String name;
        public final List<Item> items;

        Section(String name, List<Item> items) {
            this.name = name;
            this.items = items;
        }
    }

    public static final class Episode {
        public final String slug;
        public final String name;
        public final int number;

        Episode(String slug, String name, int number) {
            this.slug = slug;
            this.name = name;
            this.number = number;
        }
    }

    public static final class Detail {
        public final String slug;
        public final String title;
        public final String cover;
        public final String synopsis;
        public final String info;
        public final List<Episode> episodes;

        Detail(String slug, String title, String cover, String synopsis, String info,
               List<Episode> episodes) {
            this.slug = slug;
            this.title = title;
            this.cover = cover;
            this.synopsis = synopsis;
            this.info = info;
            this.episodes = episodes;
        }
    }

    public static final class Server {
        public final String label;
        public final String url;
        /** Server ini memberi berkas video langsung, bukan halaman pemutar. */
        public final boolean file;

        Server(String label, String url, boolean file) {
            this.label = label;
            this.url = url;
            this.file = file;
        }
    }

    public static final class EpisodeInfo {
        public final String title;
        public final String number;
        public final List<Server> servers;

        EpisodeInfo(String title, String number, List<Server> servers) {
            this.title = title;
            this.number = number;
            this.servers = servers;
        }
    }

    private final Transport transport;

    public KompiSource() {
        this(new Transport() {
            @Override public String get(String url, String referer) throws IOException {
                return fetch(url, referer);
            }
        });
    }

    KompiSource(Transport transport) {
        this.transport = transport;
    }

    private static String fetch(String url, String referer) throws IOException {
        Request.Builder request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "id-ID,id;q=0.9")
                .header("Accept", "application/json, text/html;q=0.9,*/*;q=0.8");
        if (referer != null && !referer.isEmpty()) {
            request.header("Referer", referer);
        }
        try (Response response = CLIENT.newCall(request.build()).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (text.length() > MAX_BODY) {
                throw new IOException("Balasan sumber terlalu besar.");
            }
            if (!response.isSuccessful()) {
                throw new IOException("Sumber membalas HTTP " + response.code() + ".");
            }
            String low = text.toLowerCase();
            if (low.contains("just a moment") || low.contains("cf-chl")) {
                throw new IOException("Sumber meminta verifikasi (CAPTCHA).");
            }
            return text;
        }
    }

    private JSONObject json(String path) throws SourceException {
        String body;
        try {
            body = transport.get(BASE + path, BASE + "/");
        } catch (IOException error) {
            throw new SourceException(error.getMessage() == null
                    ? "Sumber tidak dapat dihubungi." : error.getMessage(), error);
        }
        try {
            return new JSONObject(body);
        } catch (JSONException error) {
            throw new SourceException("Balasan sumber bukan JSON yang dikenali.", error);
        }
    }

    // ------------------------------------------------------------- beranda

    public List<Section> home() throws SourceException {
        JSONObject root = json("/api/home");
        List<Section> sections = new ArrayList<>();
        addSection(sections, "Episode Terbaru", root.optJSONArray("latestEpisodes"), true);
        addSection(sections, "Populer Minggu Ini", root.optJSONArray("popularWeekly"), false);
        addSection(sections, "Sedang Tayang", root.optJSONArray("ongoing"), false);
        addSection(sections, "Film", root.optJSONArray("movies"), false);
        if (sections.isEmpty()) {
            throw new SourceException("Daftar anime tidak terbaca.");
        }
        return sections;
    }

    private void addSection(List<Section> target, String name, JSONArray array, boolean episode) {
        if (array == null) return;
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Item item = episode ? episodeItem(array.optJSONObject(i)) : animeItem(array.optJSONObject(i));
            if (item != null) items.add(item);
        }
        if (!items.isEmpty()) target.add(new Section(name, items));
    }

    private static Item animeItem(JSONObject node) {
        if (node == null) return null;
        String slug = node.optString("slug", "");
        if (slug.isEmpty()) return null;
        String type = node.optString("type", "").toUpperCase();
        String status = node.optString("status", "");
        if (!status.isEmpty()) {
            status = Character.toUpperCase(status.charAt(0)) + status.substring(1);
        }
        String note = type + (type.isEmpty() || status.isEmpty() ? "" : " · ") + status;
        return new Item(slug, node.optString("title", ""),
                absolute(node.optString("cover", node.optString("banner", ""))), note);
    }

    private static Item episodeItem(JSONObject node) {
        if (node == null) return null;
        String slug = node.optString("animeSlug", "");
        if (slug.isEmpty()) return null;
        String number = node.optString("numberLabel", node.optString("number", ""));
        return new Item(slug, node.optString("animeTitle", ""),
                absolute(node.optString("cover", node.optString("animeCover", ""))),
                number.isEmpty() ? "Episode baru" : "Episode " + number);
    }

    static String absolute(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.startsWith("/") ? BASE + value : value;
    }

    // ------------------------------------------------------------ pencarian

    public List<Item> search(String query) throws SourceException {
        String encoded = encode(query == null ? "" : query.trim());
        JSONObject root = json("/api/search?q=" + encoded);
        JSONArray array = root.optJSONArray("anime");
        List<Item> items = new ArrayList<>();
        if (array == null) return items;
        for (int i = 0; i < array.length(); i++) {
            Item item = animeItem(array.optJSONObject(i));
            if (item != null) items.add(item);
        }
        return items;
    }

    // --------------------------------------------------------------- detail

    public Detail detail(String slug) throws SourceException {
        JSONObject root = json("/api/anime/" + encode(slug));
        JSONObject anime = root.optJSONObject("anime");
        if (anime == null) {
            throw new SourceException("Detail anime tidak ditemukan.");
        }
        List<Episode> episodes = new ArrayList<>();
        JSONArray array = root.optJSONArray("episodes");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject node = array.optJSONObject(i);
                if (node == null) continue;
                String epSlug = node.optString("slug", "");
                if (epSlug.isEmpty()) continue;
                int number = node.optInt("number", -1);
                String label = node.optString("title", "");
                if (label.isEmpty()) label = "Episode " + (number < 0 ? "?" : String.valueOf(number));
                episodes.add(new Episode(epSlug, label, number));
            }
        }
        // Terbaru dulu, sama seperti sumbernya.
        java.util.Collections.sort(episodes,
                (a, b) -> Integer.compare(b.number, a.number));
        String info = join(" · ",
                anime.optString("type", ""),
                anime.optString("status", ""),
                episodes.isEmpty() ? "" : episodes.size() + " episode",
                anime.optString("score", "").isEmpty() ? "" : "★ " + anime.optString("score", ""));
        return new Detail(slug, anime.optString("title", ""),
                absolute(anime.optString("cover", anime.optString("banner", ""))),
                anime.optString("synopsis", ""), info, episodes);
    }

    private static String join(String separator, String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (text.length() > 0) text.append(separator);
            text.append(part);
        }
        return text.toString();
    }

    // -------------------------------------------------------------- episode

    public EpisodeInfo episode(String slug) throws SourceException {
        JSONObject root = json("/api/episodes/" + encode(slug));
        JSONObject anime = root.optJSONObject("anime");
        JSONObject episode = root.optJSONObject("episode");
        List<Server> servers = new ArrayList<>();
        JSONArray array = root.optJSONArray("sources");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject node = array.optJSONObject(i);
                if (node == null) continue;
                String url = node.optString("url", "");
                if (url.isEmpty()) continue;
                servers.add(new Server(node.optString("label", "Server"), url, isFileHost(url)));
            }
        }
        String number = episode == null ? ""
                : episode.optString("numberLabel", episode.optString("number", ""));
        return new EpisodeInfo(anime == null ? "" : anime.optString("title", ""), number, servers);
    }

    static boolean isFileHost(String url) {
        String low = url == null ? "" : url.toLowerCase();
        return low.contains("pixeldrain.") || low.contains("mp4upload.");
    }

    /** URLEncoder versi String: overload Charset baru ada di Android 33. */
    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            return "";
        }
    }

    // --------------------------------------------------------------- pemutar

    /**
     * Ubah halaman server menjadi alamat berkas video.
     *
     * @return alamat berkas, atau {@code null} bila server hanya menyediakan
     *         halaman pemutar.
     */
    public String resolve(String serverUrl) throws SourceException {
        if (serverUrl == null || serverUrl.isEmpty()) return null;
        String low = serverUrl.toLowerCase();
        if (low.contains("pixeldrain.")) {
            Matcher matcher = Pattern.compile("/(?:u|api/file)/([A-Za-z0-9]+)").matcher(serverUrl);
            if (matcher.find()) {
                return "https://pixeldrain.com/api/file/" + matcher.group(1) + "?download";
            }
            return null;
        }
        if (low.contains("mp4upload.")) {
            String html;
            try {
                html = transport.get(serverUrl, BASE + "/");
            } catch (IOException error) {
                throw new SourceException("Server video tidak dapat dihubungi.", error);
            }
            Matcher matcher = MP4UPLOAD_FILE.matcher(html);
            return matcher.find() ? matcher.group(1) : null;
        }
        return null;
    }

    /** Referer yang harus dipakai saat memutar berkas dari server tertentu. */
    public static String refererFor(String serverUrl) {
        if (serverUrl == null) return BASE + "/";
        String low = serverUrl.toLowerCase();
        if (low.contains("mp4upload.")) return "https://www.mp4upload.com/";
        if (low.contains("pixeldrain.")) return "https://pixeldrain.com/";
        return BASE + "/";
    }
}
