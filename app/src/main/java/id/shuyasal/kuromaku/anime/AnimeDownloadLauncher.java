package id.shuyasal.kuromaku.anime;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Memulai unduhan episode langsung dari daftar episode.
 *
 * <p>Alurnya sengaja tidak membuka pemutar: halaman episode dibuka di latar
 * belakang, sumber yang tersedia diperiksa, lalu unduhan diantrekan. Pemutar
 * hanya dipakai kalau pengguna memilih "Tonton".
 *
 * <p>Aturan pemilihan server:
 * <ol>
 *   <li>Berkas media yang diterbitkan halaman episode dipakai lebih dulu.</li>
 *   <li>Bila tidak ada, mirror diperiksa satu per satu (maksimal
 *       {@link #MAX_PROBES}) sampai ada yang benar-benar menyediakan berkas.</li>
 *   <li>Mirror yang hanya menyediakan pemutar bertaut tidak pernah dianggap
 *       bisa diunduh, dan pemeriksaannya tidak diulang tanpa batas.</li>
 * </ol>
 */
public final class AnimeDownloadLauncher {

    /** Batas jumlah mirror yang diperiksa; menghindari menembaki semua mirror. */
    static final int MAX_PROBES = 3;

    /** Satu sumber yang layak diunduh beserta nama servernya. */
    public static final class Pick {
        public final OtakudesuPlayback.Source source;
        public final String server;

        Pick(OtakudesuPlayback.Source source, String server) {
            this.source = source;
            this.server = server == null ? "" : server;
        }
    }

    /** Hasil pemeriksaan satu mirror beserta nama servernya. */
    public static final class Probe {
        public final String server;
        public final OtakudesuServerPicker.Probed result;

        public Probe(String server, OtakudesuServerPicker.Probed result) {
            this.server = server == null ? "" : server;
            this.result = result;
        }
    }

    public interface Callback {
        /** Dipanggil di thread pemanggil; {@code queued=false} berisi alasan. */
        void onResult(boolean queued, String message);
    }

    private AnimeDownloadLauncher() {
    }

    /**
     * Pilih sumber pertama yang benar-benar bisa diunduh.
     *
     * <p>Fungsi murni: tidak menyentuh jaringan atau disk, sehingga aturannya
     * bisa diuji langsung terhadap hasil urai halaman episode.
     */
    public static Pick choose(OtakudesuPlayback playback, List<Probe> probes) {
        if (playback != null) {
            for (OtakudesuPlayback.Source source : playback.sources) {
                if (downloadable(source, "Halaman episode")) {
                    return new Pick(source, "Halaman episode");
                }
            }
        }
        if (probes != null) {
            for (Probe probe : probes) {
                if (probe == null || probe.result == null) continue;
                if (!probe.result.playable()) continue;
                if (downloadable(probe.result.source, probe.server)) {
                    return new Pick(probe.result.source, probe.server);
                }
            }
        }
        return null;
    }

    /** Apakah sumber ini berupa berkas yang boleh dan bisa disimpan. */
    private static boolean downloadable(OtakudesuPlayback.Source source, String server) {
        if (source == null) return false;
        AnimeDownloadTask task = AnimeDownloadTask.of("a", "t", "", "e", "https://x/y",
                source, server);
        return task != null && task.downloadable();
    }

    /**
     * Jalankan seluruh alur: periksa halaman episode, pilih sumber, antrekan.
     *
     * <p>Tidak pernah melempar: kegagalan apa pun (jaringan, parser, kebijakan
     * unduhan) berakhir sebagai status yang terlihat pengguna.
     */
    public static void start(Context context, String animeId, String animeTitle,
                             String animePoster, String episodeLabel, String episodeUrl,
                             Callback callback) {
        start(context, null, animeId, animeTitle, animePoster, episodeLabel, episodeUrl,
                callback);
    }

    /**
     * Seperti {@link #start(Context, String, String, String, String, String, Callback)},
     * tetapi memakai sumber yang diberikan (dipakai pengujian dan layar yang
     * sudah punya sumber sendiri).
     */
    public static void start(Context context, OtakudesuSource provided, String animeId,
                             String animeTitle, String animePoster, String episodeLabel,
                             String episodeUrl, Callback callback) {
        String key = AnimeDownloads.episodeKey(episodeUrl, episodeLabel);
        if (key.isEmpty()) {
            fail(key, callback, "Episode ini tidak punya penanda yang sah.");
            return;
        }
        if (AnimeDownloads.isSaved(context, animeId, key)) {
            AnimeDownloadStatus.set(key, AnimeDownloadStatus.SAVED, 100, "Tersimpan offline");
            if (callback != null) callback.onResult(true, "Episode sudah tersimpan.");
            return;
        }
        if (!AnimeDownloadPolicy.allowed(context)) {
            // Pemanggil yang meminta persetujuan lebih dulu; tidak ada berkas
            // yang disimpan tanpa izin.
            AnimeDownloadStatus.clear(key);
            if (callback != null) callback.onResult(false, AnimeDownloadPolicy.blockedMessage());
            return;
        }

        AnimeDownloadStatus.set(key, AnimeDownloadStatus.QUEUED, 0, "Menyiapkan…");

        final OtakudesuSource source = provided == null ? new OtakudesuSource() : provided;
        OtakudesuPlayback playback = null;
        String failure = null;
        Document doc = null;
        try {
            String html = source.html(episodeUrl);
            doc = Jsoup.parse(html);
            playback = OtakudesuMediaResolver.resolveHtml(html, null, episodeUrl);
        } catch (OtakudesuSource.SourceException error) {
            failure = error.getMessage();
        } catch (RuntimeException error) {
            failure = "Halaman episode tidak bisa dibaca.";
        }

        Pick pick = choose(playback, null);

        if (pick == null && playback != null && !playback.mirrors.isEmpty()) {
            List<Probe> probes = new ArrayList<>();
            int checked = 0;
            for (OtakudesuPlayback.Mirror mirror
                    : OtakudesuServerPicker.probeOrder(playback.mirrors)) {
                if (checked >= MAX_PROBES) break;
                checked++;
                AnimeDownloadStatus.set(key, AnimeDownloadStatus.QUEUED, 0, "Menyiapkan…");
                try {
                    probes.add(new Probe(OtakudesuServerPanel.summary(mirror),
                            OtakudesuServerPicker.probe(mirror,
                                    OtakudesuServerPicker.networkHttp(), doc)));
                } catch (RuntimeException error) {
                    // Satu mirror bermasalah tidak menghentikan yang lain.
                }
                pick = choose(null, probes);
                if (pick != null) break;
            }
        }

        if (pick == null) {
            String reason = failure != null ? failure
                    : "Tidak ada server episode ini yang menyediakan berkas untuk diunduh.";
            fail(key, callback, reason);
            return;
        }

        AnimeDownloadTask task = AnimeDownloadTask.of(animeId, animeTitle, animePoster,
                episodeLabel, episodeUrl, pick.source, pick.server);
        if (task == null || !task.downloadable()) {
            fail(key, callback, "Berkas dari server itu tidak bisa disimpan.");
            return;
        }
        boolean queued = AnimeDownloadQueue.enqueue(context, task);
        if (!queued) {
            fail(key, callback, "Unduhan tidak bisa dijadwalkan.");
            return;
        }
        if (callback != null) callback.onResult(true, "Episode masuk antrean unduhan.");
    }

    private static void fail(String key, Callback callback, String message) {
        if (key != null && !key.isEmpty()) {
            AnimeDownloadStatus.set(key, AnimeDownloadStatus.FAILED, 0, message);
        }
        if (callback != null) callback.onResult(false, message);
    }
}