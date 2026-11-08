package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Karantina pekerjaan unduhan yang terbukti bermasalah.
 *
 * <p>Pekerjaan unduhan yang tersimpan WorkManager dijalankan ulang setiap proses
 * aplikasi dimulai. Bila pekerjaan itu selalu gagal, aplikasi bisa terjebak
 * mengulanginya terus — terlihat sebagai aplikasi yang tidak bisa dibuka lagi.
 *
 * <p>Karantina hanya menyentuh pekerjaan pada tag unduhan anime yang sudah
 * <b>habis percobaannya</b>. Unduhan Novel dan Manga Server 2 tidak disentuh,
 * tidak ada berkas yang dihapus, dan tidak ada `cancelAllWork()`.
 */
public final class DownloadQuarantine {

    private static final String TAG = "KuromakuDownloads";

    /** Tag pekerjaan unduhan anime; sengaja sempit supaya modul lain aman. */
    public static final String ANIME_TAG = "anime-download";

    /** Awalan tag per episode; sisanya adalah kunci episode. */
    private static final String EPISODE_TAG_PREFIX = "anime-download-tag-";

    /** Percobaan yang dianggap cukup sebelum pekerjaan dikarantina. */
    public static final int MAX_ATTEMPTS = 3;

    private DownloadQuarantine() {
    }

    /** Keputusan untuk satu pekerjaan; murni supaya bisa diuji tanpa WorkManager. */
    public static final class Verdict {
        public final boolean quarantine;
        public final String reason;

        Verdict(boolean quarantine, String reason) {
            this.quarantine = quarantine;
            this.reason = reason == null ? "" : reason;
        }
    }

    /** Pekerjaan yang sudah selesai (apa pun hasilnya) tidak perlu dikarantina. */
    public static Verdict judge(boolean finished, int runAttemptCount) {
        return judge(finished, runAttemptCount, false);
    }

    /**
     * Seperti {@link #judge(boolean, int)}, tetapi setelah pembaruan aplikasi
     * pekerjaan yang sudah pernah dicoba tidak dilanjutkan otomatis.
     *
     * <p>Alasannya: pekerjaan lama bisa saja yang membuat versi sebelumnya mati.
     * Setelah pembaruan, pekerjaan seperti itu ditandai "Perlu dicoba ulang"
     * supaya pengguna memulainya dengan sadar — bukan dijalankan ulang saat
     * aplikasi baru pertama kali dibuka.
     */
    public static Verdict judge(boolean finished, int runAttemptCount, boolean afterUpdate) {
        if (finished) return new Verdict(false, "");
        if (runAttemptCount >= MAX_ATTEMPTS) {
            return new Verdict(true, "Unduhan gagal berulang kali. Silakan coba lagi.");
        }
        if (afterUpdate && runAttemptCount >= 1) {
            return new Verdict(true, "Unduhan dari versi sebelumnya dihentikan. "
                    + "Silakan coba lagi.");
        }
        return new Verdict(false, "");
    }

    /** Kunci episode dari tag pekerjaan, atau kosong bila tidak ada. */
    public static String episodeKeyFrom(Set<String> tags) {
        if (tags == null) return "";
        for (String tag : tags) {
            if (tag != null && tag.startsWith(EPISODE_TAG_PREFIX)) {
                return tag.substring(EPISODE_TAG_PREFIX.length());
            }
        }
        return "";
    }

    /**
     * Jalankan karantina; aman dipanggil dari thread latar saat aplikasi dibuka.
     */
    public static void run(Context context) {
        if (context == null) return;
        // Jalan cepat: bila tidak ada unduhan anime yang menggantung, antrean tidak
        // perlu dibaca sama sekali. Tanpa ini setiap pembukaan aplikasi menunggu
        // WorkManager, padahal tidak ada yang perlu dikarantina.
        if (!adaYangMenggantung()) return;
        Context app = context.getApplicationContext();
        List<WorkInfo> infos;
        try {
            infos = WorkManager.getInstance(app)
                    .getWorkInfosByTag(ANIME_TAG)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            // WorkManager belum siap: tidak ada yang perlu dilakukan, dan ini
            // tidak boleh menghalangi aplikasi dibuka.
            Log.w(TAG, "karantina dilewati: antrean tidak bisa dibaca", error);
            return;
        }
        if (infos == null) return;

        boolean afterUpdate = isFirstRunAfterUpdate(app);
        List<UUID> buang = new ArrayList<>();
        for (WorkInfo info : infos) {
            if (info == null) continue;
            boolean finished = info.getState() == WorkInfo.State.SUCCEEDED
                    || info.getState() == WorkInfo.State.FAILED
                    || info.getState() == WorkInfo.State.CANCELLED;
            Verdict verdict = judge(finished, info.getRunAttemptCount(), afterUpdate);
            if (!verdict.quarantine) continue;

            buang.add(info.getId());
            String key = episodeKeyFrom(info.getTags());
            if (!key.isEmpty()) {
                id.shuyasal.kuromaku.anime.AnimeDownloadStatus.set(key,
                        id.shuyasal.kuromaku.anime.AnimeDownloadStatus.FAILED, 0,
                        verdict.reason,
                        id.shuyasal.kuromaku.anime.AnimeDownloadStatus.quality(key));
            }
            Log.w(TAG, "pekerjaan unduhan dikarantina: " + info.getId()
                    + " (" + verdict.reason + ")");
        }
        if (buang.isEmpty()) return;
        try {
            WorkManager manager = WorkManager.getInstance(app);
            for (UUID id : buang) manager.cancelWorkById(id);
        } catch (RuntimeException error) {
            Log.w(TAG, "pembatalan pekerjaan karantina gagal", error);
        }
    }

    /** Benar bila ada entri anime yang belum selesai di berkas status. */
    static boolean adaYangMenggantung() {
        for (java.util.Map.Entry<String, DownloadStateFile.Row> entry
                : DownloadStateFile.all().entrySet()) {
            if (!entry.getKey().startsWith(
                    id.shuyasal.kuromaku.anime.AnimeDownloadStatus.PREFIX)) continue;
            int state = entry.getValue().state;
            if (state == id.shuyasal.kuromaku.anime.AnimeDownloadStatus.QUEUED
                    || state == id.shuyasal.kuromaku.anime.AnimeDownloadStatus.DOWNLOADING
                    || state == id.shuyasal.kuromaku.anime.AnimeDownloadStatus.VERIFYING
                    || state == id.shuyasal.kuromaku.anime.AnimeDownloadStatus.PAUSED) {
                return true;
            }
        }
        return false;
    }

    /** Benar bila versi aplikasi berubah sejak pemeriksaan terakhir. */
    private static boolean isFirstRunAfterUpdate(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "download-quarantine", Context.MODE_PRIVATE);
            int last = prefs.getInt("last_version", -1);
            int now = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
            if (last == now) return false;
            prefs.edit().putInt("last_version", now).apply();
            return last != -1;
        } catch (Exception error) {
            return false;
        }
    }
}
