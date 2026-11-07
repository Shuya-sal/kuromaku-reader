package id.shuyasal.kuromaku.anime;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pemilih server streaming: membuka satu mirror yang dipilih pengguna dan
 * memeriksa apakah mirror itu benar-benar memuat berkas video.
 *
 * <p>Alurnya sama dengan yang dilakukan browser: halaman episode menyebut id
 * server di dalam {@code data-content}, halaman itu mengirim id tersebut ke
 * pemilih mirror miliknya, dan pemilih itu menjawab dengan alamat pemutar yang
 * dipakai halaman episode sendiri. Aplikasi melakukan hal yang sama, lalu
 * memeriksa jawabannya:
 *
 * <ol>
 *   <li>kalau jawabannya sebuah <b>pemutar pihak ketiga</b> (iframe penyedia),
 *       halaman pemutar itu dibuka dan dibaca apa adanya;</li>
 *   <li>kalau di dalamnya ada <b>alamat berkas</b> (mp4/m3u8/mpd) yang bisa
 *       dibaca, berkas itu dipakai — browser juga membacanya, karena halaman itu
 *       memang mengirimkannya ke peramban biasa;</li>
 *   <li>kalau pemutarnya <b>mengenkripsi</b> alamat berkas atau menutupnya di
 *       balik kunci yang tidak dipublikasikan, hasilnya dilaporkan
 *       {@link Status#PROTECTED_SERVER} — aplikasi tidak pernah membalik
 *       enkripsi, tidak menebak pola alamat, dan tidak memakai kredensial.</li>
 * </ol>
 *
 * <p>Yang tidak pernah dilakukan kelas ini: menebak alamat berkas dari nama
 * episode, memakai kunci privat penyedia, melewati CAPTCHA/DRM/autentikasi, atau
 * menganggap sebuah server bisa diputar hanya karena namanya muncul.
 */
public final class OtakudesuServerPicker {

    /** Hasil pemeriksaan satu mirror. */
    public enum Status {
        /** Halaman pemutar benar-benar memuat alamat berkas media. */
        READY("Siap diputar"),
        /** Halaman pemutar terbuka, tetapi berkasnya ditutup pemutar itu. */
        PROTECTED_SERVER("Pemutar penyedia"),
        /** Halaman pemutar tidak bisa dibuka atau berkasnya hilang. */
        UNAVAILABLE("Tidak tersedia"),
        /** Belum diperiksa. */
        UNTESTED("Belum diperiksa");

        public final String label;

        Status(String label) {
            this.label = label;
        }
    }

    /** Hasil pemeriksaan: status, sumber (bila ada), dan alasan singkat. */
    public static final class Probed {
        public final Status status;
        public final OtakudesuPlayback.Source source;
        public final String note;

        public Probed(Status status, OtakudesuPlayback.Source source, String note) {
            this.status = status == null ? Status.UNTESTED : status;
            this.source = source;
            this.note = note == null ? "" : note;
        }

        public boolean playable() {
            return source != null;
        }
    }

    /** Pengambil HTTP; dipisah agar bisa diuji tanpa jaringan. */
    public interface Http {
        String get(String url) throws IOException, OtakudesuSource.SourceException;

        String post(String url, List<String[]> fields)
                throws IOException, OtakudesuSource.SourceException;
    }

    /** Pengambil lewat jaringan sungguhan. */
    public static Http networkHttp() {
        return new Http() {
            @Override public String get(String url) throws OtakudesuSource.SourceException {
                return OtakudesuSource.fetchHtml(url);
            }

            @Override public String post(String url, List<String[]> fields)
                    throws OtakudesuSource.SourceException {
                return OtakudesuSource.postForm(url, fields);
            }
        };
    }

    /**
     * Aksi pemilih mirror. Halaman episode menulisnya di dalam skripnya
     * sendiri: {@code data:{...e,nonce:window.__x__nonce,action:"<32 hex>"}}.
     * Nilainya dibaca dari halaman, bukan ditulis tetap di kode.
     */
    static final Pattern ACTION = Pattern.compile(
            "action\\s*:\\s*[\"']([a-f0-9]{16,64})[\"']");

    /** Aksi pengambil nonce: dikirim lebih dulu saat nonce belum ada di halaman. */
    static final Pattern NONCE_ACTION = Pattern.compile(
            "data\\s*:\\s*\\{\\s*action\\s*:\\s*[\"']([a-f0-9]{16,64})[\"']");

    private static final Pattern MIRROR_ID = Pattern.compile(
            "id\\s*=\\s*[\"']?(\\d{2,12})[\"']?");

    private OtakudesuServerPicker() {
    }

    // ------------------------------------------------------------- pembuka

    /**
     * Meminta alamat pemutar untuk satu mirror, sama seperti halaman episode:
     * {@code POST admin-ajax.php} dengan aksi, id, indeks, kualitas, dan nonce
     * yang keduanya dibaca dari halaman episode.
     */
    static String openPlayer(OtakudesuPlayback.Mirror mirror, Http http, Document episodeDoc)
            throws IOException, OtakudesuSource.SourceException {
        String nonce = nonce(http, episodeDoc);
        String action = action(episodeDoc);
        if (nonce.isEmpty() || action.isEmpty() || mirror.serverId.isEmpty()) {
            throw new IOException("Pemilih server sumber tidak dapat dibaca.");
        }
        List<String[]> fields = new ArrayList<>();
        fields.add(new String[]{"id", mirror.serverId});
        fields.add(new String[]{"i", String.valueOf(mirror.index)});
        fields.add(new String[]{"q", mirror.quality});
        fields.add(new String[]{"nonce", nonce});
        fields.add(new String[]{"action", action});
        String body = http.post(OtakudesuSource.BASE + "/wp-admin/admin-ajax.php", fields);
        String player = playerUrl(body);
        if (player.isEmpty()) {
            throw new IOException("Sumber tidak mengirim alamat pemutar untuk server ini.");
        }
        return player;
    }

    /** Nonce halaman: dibaca dari halaman episode, tidak pernah dikarang. */
    static String nonce(Http http, Document episodeDoc)
            throws IOException, OtakudesuSource.SourceException {
        String embedded = nonceIn(episodeDoc);
        if (!embedded.isEmpty()) return embedded;
        String nonceAction = nonceAction(episodeDoc);
        if (nonceAction.isEmpty()) return "";
        List<String[]> fields = new ArrayList<>();
        fields.add(new String[]{"action", nonceAction});
        String body = http.post(OtakudesuSource.BASE + "/wp-admin/admin-ajax.php", fields);
        return value(body);
    }

    /**
     * Nilai nonce yang sudah tertanam di halaman, bila ada.
     *
     * <p>Halaman episode memulai dengan {@code window.__x__nonce=null} lalu
     * mengisinya setelah permintaan pertama. Karena itu {@code null} dan string
     * kosong diabaikan — kalau tidak, pemutar akan mengirim literal "null" dan
     * sumber menolaknya dengan HTTP 403.
     */
    static String nonceIn(Document doc) {
        if (doc == null) return "";
        // Nilai literal saja: setelah tanda kutip, atau bentuk nonce=<nilai>
        // tanpa nama variabel/titik (nonce:window.__x__nonce bukan nilai).
        Matcher quoted = Pattern.compile(
                "nonce\\s*[:=]\\s*['\"]([A-Za-z0-9]{6,32})['\"]").matcher(doc.html());
        if (quoted.find()) return quoted.group(1);
        Matcher assigned = Pattern.compile(
                "nonce\\s*=\\s*([A-Za-z0-9]{6,32})(?![.\\w])").matcher(doc.html());
        while (assigned.find()) {
            String value = assigned.group(1);
            if ("null".equalsIgnoreCase(value)) continue;
            return value;
        }
        return "";
    }

    /**
     * Dua aksi di halaman episode: {@code mirror} untuk membuka server, dan
     * {@code nonce} untuk mengambil token. Keduanya dibaca berurutan dari
     * skripnya, jadi tidak ada nilai yang dihafal di kode.
     */
    /**
     * Aksi pemilih mirror dan aksi nonce dibaca dari skrip halaman episode, lalu
     * ditentukan dari <b>bentuk pemakaiannya</b> — bukan dari urutan atau
     * panjangnya:
     *
     * <ul>
     *   <li>aksi mirror selalu dikirim bersama {@code nonce:} di dalam objek yang
     *       sama ({@code data:{...e,nonce:…,action:"…"}});</li>
     *   <li>aksi nonce dikirim sendirian di objek berisi {@code action} saja
     *       ({@code data:{action:"…"}}).</li>
     * </ul>
     *
     * <p>Membedakan keduanya penting: mengirim salah satu ke posisi yang lain
     * membuat sumber menjawab HTTP 403.
     */
    static String action(Document doc) {
        if (doc == null) return "";
        Matcher pair = Pattern.compile(
                "\\{[^{}]*nonce\\s*:\\s*[^{}]*action\\s*:\\s*[\"']([a-f0-9]{16,64})[\"']")
                .matcher(doc.html());
        if (pair.find()) return pair.group(1);
        List<String> found = actions(doc);
        return found.isEmpty() ? "" : found.get(found.size() - 1);
    }

    /** Aksi nonce: satu-satunya aksi yang tidak dikirim bersama {@code nonce:}. */
    static String nonceAction(Document doc) {
        if (doc == null) return "";
        String mirror = action(doc);
        for (String candidate : actions(doc)) {
            if (!candidate.equals(mirror)) return candidate;
        }
        return "";
    }

    /**
     * Dua aksi di halaman episode, dalam urutan kemunculannya di skrip:
     * {@code data:{action:"<nonce>"}} lebih dulu, lalu
     * {@code data:{...e,nonce:…,action:"<mirror>"}}. Regex terpisah tidak bisa
     * membedakan keduanya, jadi yang menentukan adalah urutan di halaman.
     */
    static List<String> actions(Document doc) {
        List<String> found = new ArrayList<>();
        if (doc == null) return found;
        Matcher matcher = Pattern.compile(
                "action\\s*:\\s*[\"']([a-f0-9]{16,64})[\"']").matcher(doc.html());
        while (matcher.find()) {
            if (!found.contains(matcher.group(1))) found.add(matcher.group(1));
        }
        return found;
    }

    /** {@code {"data":"<base64 iframe>"}} → alamat pemutar. */
    static String playerUrl(String body) {
        if (body == null) return "";
        String html = decode(body);
        Matcher matcher = Pattern.compile("(?:iframe[^>]*src|src)\\s*=\\s*['\"]([^'\"]+)['\"]")
                .matcher(html);
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            if (url.startsWith("https://")) return url;
        }
        return "";
    }

    /** Body jawaban bisa berupa JSON base64, JSON biasa, atau HTML langsung. */
    static String decode(String body) {
        String value = value(body);
        if (value.isEmpty()) value = body;
        String trimmed = value.trim();
        if (trimmed.startsWith("<")) return trimmed;
        if (trimmed.matches("[A-Za-z0-9+/=\\s]{24,}")) {
            try {
                byte[] bytes = java.util.Base64.getMimeDecoder().decode(trimmed);
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    /** Ambil field "data" dari jawaban JSON; kalau bukan JSON, kembalikan apa adanya. */
    static String value(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) return trimmed;
        try {
            org.json.JSONObject object = new org.json.JSONObject(trimmed);
            return object.optString("data", "");
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    // ------------------------------------------------------------ pemeriksa

    /**
     * Membuka satu mirror lalu memeriksa hasilnya. Tidak ada penebakan alamat:
     * yang dilaporkan {@link Status#READY} hanya berkas yang benar-benar tertulis
     * di halaman pemutar.
     */
    public static Probed probe(OtakudesuPlayback.Mirror mirror, Http http, Document episodeDoc) {
        if (mirror == null || mirror.serverId.isEmpty()) {
            return new Probed(Status.UNTESTED, null,
                    "Server ini tidak menyebut id-nya di halaman episode.");
        }
        String player;
        try {
            player = openPlayer(mirror, http, episodeDoc);
        } catch (Exception error) {
            return new Probed(Status.UNAVAILABLE, null,
                    error.getMessage() == null ? "Pemutar tidak dapat dibuka." : error.getMessage());
        }
        return probePlayer(mirror, player, http);
    }

    /**
     * Memeriksa halaman pemutar: kalau pemutar pihak ketiga, halamannya dibuka
     * sekali lagi seperti peramban.
     *
     * <p>Untuk HLS, playlist media di dalamnya <b>tidak</b> dibuka. Playlist itu
     * sering menyusun nama segmen dari playlist git (di sumber ini
     * {@code seg-1-v1-a1.t}), sedangkan berkas yang benar-benar dilayani
     * berakhiran {@code .ts}. Menyentuh alamat playlist lebih jauh berarti
     * menyusun ulang alamat berkas — itu penebakan, dan berkasnya sudah bisa
     * dibaca Media3 apa adanya lewat playlist master.
     */
    static Probed probePlayer(OtakudesuPlayback.Mirror mirror, String player, Http http) {
        OtakudesuPlayback.Source direct = firstSource(player);
        if (direct != null) return new Probed(Status.READY, direct, "Berkas terbuka di pemutar.");


        if (OtakudesuMediaResolver.looksLikeProviderPage(player)) {
            String html;
            try {
                html = http.get(player);
            } catch (Exception error) {
                return new Probed(Status.UNAVAILABLE, null,
                        "Halaman pemutar tidak dapat dibuka.");
            }
            OtakudesuPlayback.Source found = firstSource(html);
            if (found != null) {
                return new Probed(Status.READY, withQuality(found, mirror.quality),
                        "Berkas terbaca dari halaman pemutar.");
            }
            // Halaman pemutar mengirim isi videonya lewat permintaan susulan
            // (mis. googlevideo dengan ip= dan expire= di dalam alamatnya),
            // bukan lewat alamat yang tertulis di HTML-nya.
            if (looksLateBound(html)) {
                return new Probed(Status.PROTECTED_SERVER, null,
                        "Alamat berkas hanya muncul setelah pemutar dijalankan.");
            }
            if (looksEncrypted(html)) {
                return new Probed(Status.PROTECTED_SERVER, null,
                        "Berkas ditutup pemutar penyedia (terenkripsi).");
            }
            return new Probed(Status.PROTECTED_SERVER, null,
                    "Pemutar penyedia tidak memuat alamat berkas yang bisa dibaca.");
        }
        if (looksLateBound(player)) {
            return new Probed(Status.PROTECTED_SERVER, null,
                    "Alamat berkas hanya muncul setelah pemutar dijalankan.");
        }
        if (looksEncrypted(player)) {
            return new Probed(Status.PROTECTED_SERVER, null,
                    "Berkas ditutup pemutar penyedia (terenkripsi).");
        }
        return new Probed(Status.UNAVAILABLE, null, "Tidak ada berkas media di pemutar ini.");
    }

    /**
     * Penanda alamat yang baru terbentuk saat pemutar berjalan: pemutar menyusun
     * alamat berkas dari potongan URL dan meminta berkasnya dengan permintaannya
     * sendiri. Alamat seperti itu tidak ada di HTML, jadi tidak bisa dipakai.
     */
    static boolean looksLateBound(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("googlevideo.com") || lower.contains("videoplayback")
                || lower.contains("ip=") && lower.contains("expire=")
                || lower.contains("&range=") || lower.contains("rqh=1");
    }

    /**
     * Alamat berkas pertama yang benar-benar tertulis di sebuah halaman dan
     * bentuknya masuk akal. Alamat yang tersusun ganda atau hostnya rusak
     * dilewati: bentuknya memang ditulis di halaman, tetapi tidak bisa diminta.
     */
    static OtakudesuPlayback.Source firstSource(String html) {
        if (html == null || html.isEmpty()) return null;
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(html, null);
        for (OtakudesuPlayback.Source source : playback.sources) {
            if (looksDoubled(source.url) || looksBroken(source.url)) continue;
            return source;
        }
        return null;
    }

    private static OtakudesuPlayback.Source withQuality(OtakudesuPlayback.Source source,
                                                        String quality) {
        if (source.quality != null && !source.quality.isEmpty()) return source;
        return new OtakudesuPlayback.Source(source.url, source.kind, quality);
    }

    /** Dua alamat berkas yang sama tersusun ganda, mis. {@code /download/https://…}. */
    static boolean looksDoubled(String url) {
        if (url == null) return false;
        String value = url.trim();
        int first = value.indexOf("https://");
        if (first < 0) return false;
        return value.indexOf("https://", first + 8) >= 0;
    }

    /**
     * Alamat yang tidak bisa diminta sama sekali: tanpa host, atau hostnya bukan
     * nama domain. Yang hanya berisi direktori (mis. {@code https://archive.org/download/})
     * juga ditolak karena tidak menunjuk satu berkas.
     */
    static boolean looksBroken(String url) {
        if (url == null) return true;
        String value = url.trim();
        if (!value.startsWith("https://")) return true;
        String rest = value.substring("https://".length());
        int slash = rest.indexOf('/');
        String host = slash < 0 ? rest : rest.substring(0, slash);
        if (host.isEmpty() || !host.contains(".")) return true;
        if (slash < 0) return true;
        String path = rest.substring(slash);
        // Harus ada nama berkas setelah garis miring terakhir; direktori saja
        // (mis. /download/) tidak menunjuk satu berkas.
        return path.lastIndexOf('/') == path.length() - 1;
    }

    /**
     * Penanda pemutar yang menutup berkasnya: kunci RSA, dekripsi AES, atau
     * susunan pkcs7 yang hanya bisa dibuka skrip miliknya.
     */
    static boolean looksEncrypted(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase(Locale.ROOT);
        String[] marks = {"rsaprivatekey", "privatekey", "pkcs7", "aes.decrypt", "crypto-js",
                "setprivatekey", "decrypt(", "atob(", "jwplayer.key"};
        for (String mark : marks) {
            if (lower.contains(mark)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------- daftar server

    /**
     * Semua mirror yang benar-benar disebut halaman episode, dikelompokkan per
     * resolusi dan diurutkan dari kualitas tertinggi. Tidak ada nama server yang
     * ditulis tetap di kode: daftarnya selalu berasal dari halaman episode.
     */
    public static LinkedHashMap<String, List<OtakudesuPlayback.Mirror>> grouped(
            List<OtakudesuPlayback.Mirror> mirrors) {
        List<String> order = new ArrayList<>(new LinkedHashSet<>(quantities(mirrors)));
        order.sort((left, right) -> Integer.compare(
                OtakudesuMediaResolver.qualityRank(right),
                OtakudesuMediaResolver.qualityRank(left)));

        LinkedHashMap<String, List<OtakudesuPlayback.Mirror>> groups = new LinkedHashMap<>();
        for (String quality : order) {
            List<OtakudesuPlayback.Mirror> inGroup = new ArrayList<>();
            for (OtakudesuPlayback.Mirror mirror : mirrors) {
                if (quality.equals(mirror.quality)) inGroup.add(mirror);
            }
            if (!inGroup.isEmpty()) groups.put(quality, inGroup);
        }
        // Kualitas yang tidak disebut tetap ditampilkan di bagian akhir.
        List<OtakudesuPlayback.Mirror> unknown = new ArrayList<>();
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if (mirror.quality == null || mirror.quality.isEmpty()) unknown.add(mirror);
        }
        if (!unknown.isEmpty()) groups.put("Resolusi tidak disebut", unknown);
        return groups;
    }

    static List<String> quantities(List<OtakudesuPlayback.Mirror> mirrors) {
        List<String> found = new ArrayList<>();
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if (mirror.quality != null && !mirror.quality.isEmpty()
                    && !found.contains(mirror.quality)) {
                found.add(mirror.quality);
            }
        }
        return found;
    }

    /**
     * Pengurutan pemeriksaan: mirror yang berkasnya terbuka lebih dulu, pemutar
     * beriklan terakhir — supaya pengguna tidak menunggu pemeriksaan server yang
     * berkasnya memang ditutup.
     */
    public static List<OtakudesuPlayback.Mirror> probeOrder(
            List<OtakudesuPlayback.Mirror> mirrors) {
        List<OtakudesuPlayback.Mirror> ordered = new ArrayList<>();
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if (!OtakudesuMediaResolver.isAdGated(mirror.provider)) ordered.add(mirror);
        }
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if (OtakudesuMediaResolver.isAdGated(mirror.provider)) ordered.add(mirror);
        }
        return ordered;
    }

    /** Elemen {@code div.mirrorstream ul} → mirror, dipakai tes dan layar. */
    static List<OtakudesuPlayback.Mirror> parseMirrors(Document doc) {
        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        for (Element group : doc.select("div.mirrorstream ul")) {
            String quality = "";
            for (String cssClass : group.classNames()) {
                if (cssClass.matches("m\\d{3,4}p")) {
                    quality = cssClass.substring(1);
                    break;
                }
            }
            Elements items = group.select("li a");
            for (int i = 0; i < items.size(); i++) {
                Element item = items.get(i);
                String payload = item.attr("data-content").trim();
                String provider = item.text().trim();
                String serverId = idOf(payload);
                if (provider.isEmpty() && serverId.isEmpty()) continue;
                mirrors.add(new OtakudesuPlayback.Mirror(quality, provider, serverId, i));
            }
        }
        return mirrors;
    }

    /** {@code {"id":207282,"i":0,"q":"360p"}} → id. */
    static String idOf(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            String json = new String(java.util.Base64.getMimeDecoder().decode(payload),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!json.startsWith("{")) return "";
            return new org.json.JSONObject(json).optString("id", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    /** ID dari HTML mentah; dipakai bila assert. */
    static String idNear(String html, String provider) {
        Matcher matcher = MIRROR_ID.matcher(html == null ? "" : html);
        return matcher.find() ? matcher.group(1) : "";
    }
}
