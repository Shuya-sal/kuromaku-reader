package id.shuyasal.kuromaku.anime;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pilihan kualitas unduhan untuk satu episode.
 *
 * <p>Label mirror seperti "720p" bukan bukti bahwa ada berkas video: yang
 * dibuktikan di sini adalah hasil pemeriksaan nyata — mirror dibuka seperti
 * peramban membukanya, alamat berkas yang benar-benar tertulis diambil, lalu
 * ukurannya dibaca dari server lewat permintaan satu byte. Kualitas yang tidak
 * lolos uji itu tampil <b>tidak tersedia</b> beserta alasannya, dan tidak pernah
 * ditawarkan untuk diunduh.
 *
 * <p>Yang tidak dilakukan: menebak alamat berkas dari nama episode, menganggap
 * HLS/DASH bisa disimpan sebagai satu berkas, membuka halaman embed sebagai
 * berkas media, atau melewati DRM/CAPTCHA/autentikasi penyedia.
 */
public final class AnimeDownloadOptions {

    /** Kualitas yang ditawarkan di lembar pemilihan. */
    public static final String[] QUALITIES = {"720p", "480p"};

    /** Batas mirror yang diperiksa per kualitas. */
    static final int MAX_PROBES_PER_QUALITY = 3;

    /** Satu pilihan kualitas. */
    public static final class Option {
        public final String quality;
        public final String server;
        public final String qualityLabel;
        public final String url;
        public final String kind;
        /** Ukuran berkas dalam byte; 0 bila server tidak menyebutkannya. */
        public final long sizeBytes;
        /** Berkas benar-benar bisa disimpan dan diputar offline. */
        public final boolean available;
        /** Alasan ketika tidak tersedia; kosong ketika tersedia. */
        public final String reason;

        Option(String quality, String server, String url, String kind, long sizeBytes,
               boolean available, String reason) {
            this.quality = quality == null ? "" : quality;
            this.server = server == null ? "" : server;
            this.qualityLabel = this.quality;
            this.url = url == null ? "" : url;
            this.kind = kind == null ? "" : kind;
            this.sizeBytes = Math.max(0L, sizeBytes);
            this.available = available;
            this.reason = reason == null ? "" : reason;
        }

        static Option missing(String quality, String reason) {
            return new Option(quality, "", "", "", 0L, false, reason);
        }

        /** Keterangan baris lembar pemilihan. */
        public String detail() {
            if (!available) return reason;
            StringBuilder text = new StringBuilder();
            if (!server.isEmpty()) text.append(server);
            if (sizeBytes > 0) {
                if (text.length() > 0) text.append(" \u00b7 ");
                text.append(AnimeDownload.size(sizeBytes));
            }
            if (text.length() == 0) text.append("Berkas siap diunduh");
            return text.toString();
        }
    }

    private AnimeDownloadOptions() {
    }

    /**
     * Susun pilihan untuk sebuah episode.
     *
     * <p>Selalu mengembalikan entri untuk setiap kualitas yang diminta, supaya
     * layar bisa menjelaskan mengapa sebuah kualitas tidak bisa dipilih alih-alih
     * diam-diam menghilangkannya.
     */
    public static List<Option> resolve(OtakudesuSource source, OkHttpClient client,
                                       String episodeUrl) throws OtakudesuSource.SourceException {
        List<Option> options = new ArrayList<>();
        if (source == null || episodeUrl == null || episodeUrl.isEmpty()) {
            for (String quality : QUALITIES) {
                options.add(Option.missing(quality, "Episode ini tidak punya alamat yang bisa diperiksa."));
            }
            return options;
        }

        String html = source.html(episodeUrl);
        Document doc = Jsoup.parse(html, episodeUrl);
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(html, null, episodeUrl);

        // Kelompokkan mirror menurut kualitasnya, urut seperti di halaman.
        Map<String, List<OtakudesuPlayback.Mirror>> byQuality = new LinkedHashMap<>();
        if (playback != null) {
            for (OtakudesuPlayback.Mirror mirror : playback.mirrors) {
                String quality = normalizeQuality(mirror.quality);
                if (quality.isEmpty()) continue;
                List<OtakudesuPlayback.Mirror> group = byQuality.get(quality);
                if (group == null) {
                    group = new ArrayList<>();
                    byQuality.put(quality, group);
                }
                group.add(mirror);
            }
        }

        OkHttpClient http = client == null ? defaultClient() : client;
        OtakudesuServerPicker.Http browser = OtakudesuServerPicker.networkHttp();

        java.util.Set<String> seenUrls = new java.util.HashSet<>();
        for (String quality : QUALITIES) {
            List<OtakudesuPlayback.Mirror> group = byQuality.get(quality);
            if (group == null || group.isEmpty()) {
                options.add(Option.missing(quality,
                        "Episode ini tidak menyediakan " + quality + " pada halaman sumbernya."));
                continue;
            }
            Option option = resolveQuality(quality, group, browser, doc, http);
            if (option.available) {
                // Satu berkas tidak ditawarkan dua kali dengan dua label.
                if (!seenUrls.add(option.url)) {
                    option = Option.missing(quality,
                            "Berkas " + quality + " pada episode ini sama dengan kualitas lain; "
                                    + "pilih yang sudah tersedia.");
                }
            }
            options.add(option);
        }
        // Satu kualitas hanya muncul sekali. Bila grup "480p" ternyata menyajikan
        // berkas 720p, baris "720p tidak tersedia" dibuang karena kualitas itu
        // justru tersedia — hanya lewat mirror lain.
        java.util.Set<String> available = new java.util.HashSet<>();
        for (Option option : options) {
            if (option.available) available.add(option.quality);
        }
        List<Option> ringkas = new ArrayList<>();
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (Option option : options) {
            if (!option.available && available.contains(option.quality)) continue;
            if (!shown.add(option.quality)) continue;
            ringkas.add(option);
        }
        return ringkas;
    }

    /** Cari satu mirror yang benar-benar memuat berkas untuk kualitas ini. */
    private static Option resolveQuality(String quality, List<OtakudesuPlayback.Mirror> group,
                                         OtakudesuServerPicker.Http browser, Document doc,
                                         OkHttpClient http) {
        String lastReason = "";
        int checked = 0;
        for (OtakudesuPlayback.Mirror mirror : probeOrder(group)) {
            if (checked >= MAX_PROBES_PER_QUALITY) break;
            checked++;
            OtakudesuServerPicker.Probed probed = OtakudesuServerPicker.probe(mirror, browser, doc);
            if (probed == null || probed.status != OtakudesuServerPicker.Status.READY
                    || probed.source == null) {
                lastReason = probed == null || probed.note.isEmpty()
                        ? "Server " + mirror.provider + " tidak menyediakan berkas."
                        : "Server " + mirror.provider + ": " + probed.note;
                continue;
            }
            if (probed.source.kind != OtakudesuPlayback.Kind.PROGRESSIVE
                    || !looksLikeFile(probed.source.url)) {
                lastReason = "Server " + mirror.provider
                        + " hanya menyediakan pemutar bertaut, bukan berkas utuh.";
                continue;
            }
            long size = sizeOf(http, probed.source.url);
            // Nama berkas lebih dipercaya daripada label grup mirror: beberapa
            // penyedia menyajikan satu berkas yang sama untuk beberapa label.
            // Menyebutnya "480p" padahal berkasnya 720p adalah klaim palsu.
            String real = qualityFromUrl(probed.source.url);
            String label = real.isEmpty() ? quality : real;
            return new Option(label, mirror.provider, probed.source.url, "PROGRESSIVE",
                    size, true, "");
        }
        if (lastReason.isEmpty()) {
            lastReason = "Tidak ada mirror " + quality + " yang bisa diperiksa.";
        }
        return Option.missing(quality, lastReason);
    }

    /**
     * Urutkan mirror untuk pemeriksaan: penyedia yang sudah terbukti mengirim
     * berkas utuh lebih dulu, sisanya mengikuti urutan halaman.
     */
    static List<OtakudesuPlayback.Mirror> probeOrder(List<OtakudesuPlayback.Mirror> group) {
        List<OtakudesuPlayback.Mirror> ordered = new ArrayList<>();
        for (OtakudesuPlayback.Mirror mirror : group) {
            if (mirror.provider.toLowerCase(Locale.ROOT).contains("odcdn")) ordered.add(mirror);
        }
        for (OtakudesuPlayback.Mirror mirror : group) {
            if (!ordered.contains(mirror)) ordered.add(mirror);
        }
        return ordered;
    }

    /** Berkas utuh yang bisa disimpan — bukan manifest streaming. */
    static boolean looksLikeFile(String url) {
        if (url == null || url.isEmpty()) return false;
        String clean = url.toLowerCase(Locale.ROOT);
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        if (clean.endsWith(".m3u8") || clean.endsWith(".mpd")) return false;
        if (clean.contains(".m3u8") || clean.contains(".mpd")) return false;
        return clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm")
                || clean.endsWith(".m4v");
    }

    /** Ukuran berkas menurut server; 0 bila tidak disebutkan. */
    static long sizeOf(OkHttpClient client, String url) {
        if (url == null || url.isEmpty()) return 0L;
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", "Mozilla/5.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 206) return 0L;
            String range = response.header("Content-Range");
            if (range != null) {
                int slash = range.lastIndexOf('/');
                if (slash >= 0) {
                    try {
                        return Long.parseLong(range.substring(slash + 1).trim());
                    } catch (NumberFormatException ignored) {
                        // lanjut ke Content-Length
                    }
                }
            }
            long length = response.header("Content-Length", "0") == null ? 0L
                    : parseLong(response.header("Content-Length", "0"));
            return Math.max(0L, length);
        } catch (IOException | RuntimeException error) {
            return 0L;
        } finally {
            // tidak ada yang perlu ditutup: try-with-resources
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /**
     * Kualitas yang tertulis pada alamat berkas, mis. {@code ..._720p.mp4}.
     * Kosong bila alamatnya tidak menyebut resolusi.
     */
    static String qualityFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d{3,4})p", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(url);
        String found = "";
        while (matcher.find()) {
            String value = matcher.group(1);
            // Ambil yang paling besar: nama berkas kadang memuat dua angka.
            if (found.isEmpty() || Integer.parseInt(value) > Integer.parseInt(found)) {
                found = value;
            }
        }
        return found.isEmpty() ? "" : found + "p";
    }

    /** Normalisasi label kualitas halaman ("Mp4 720p", "720P") menjadi "720p". */
    static String normalizeQuality(String raw) {
        if (raw == null) return "";
        String clean = raw.toLowerCase(Locale.ROOT).replace(" ", "");
        if (clean.contains("1080")) return "1080p";
        if (clean.contains("720")) return "720p";
        if (clean.contains("480")) return "480p";
        if (clean.contains("360")) return "360p";
        return "";
    }
}