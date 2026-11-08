package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.util.Log;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Antrean unduhan episode anime di atas WorkManager.
 *
 * <p>Satu pekerjaan unik per episode ({@code anime-download-<episodeKey>}),
 * sehingga menekan Unduh berkali-kali tidak pernah membuat dua job untuk
 * episode yang sama. Antrean tetap ada setelah aplikasi ditutup karena
 * WorkManager menyimpannya sendiri; pekerjaan yang dibatalkan meninggalkan
 * berkas {@code .part} yang dipakai untuk melanjutkan.
 *
 * <p>Antrean ini terpisah dari antrean manga Shinigami: nama pekerjaan, folder,
 * dan statusnya berbeda, jadi tidak ada job yang saling menimpa.
 */
public final class AnimeDownloadQueue {

    private static final String TAG = "AnimeDownload";
    private static final String PREFIX = "anime-download-";

    private AnimeDownloadQueue() {
    }

    public static String workName(String episodeKey, String quality) {
        String base = episodeKey == null ? "" : episodeKey.trim();
        String q = quality == null ? "" : quality.trim().replaceAll("[^A-Za-z0-9]", "");
        return PREFIX + base + "-" + (q.isEmpty() ? "auto" : q);
    }

    /**
     * Penanda per episode, dipasang pada setiap pekerjaan unduhan episode itu.
     *
     * <p>Pembatalan memakai tag ini, bukan posisi adapter dan bukan nama kerja:
     * nama kerja memuat kualitas, sedangkan pembatalan harus tetap bekerja walau
     * kualitas tidak diketahui lagi.
     */
    public static String episodeTag(String episodeKey) {
        return PREFIX + "tag-" + (episodeKey == null ? "" : episodeKey.trim());
    }

    /**
     * Antre satu episode.
     *
     * <p>Mengembalikan false bila kebijakan izin belum disetujui, alamat media
     * kosong, atau WorkManager tidak siap — bukan dilempar sebagai exception,
     * supaya pemanggil dari UI tidak pernah crash.
     */
    public static boolean enqueue(Context context, AnimeDownloadTask task) {
        if (context == null || task == null) return false;
        if (!task.downloadable()) return false;
        if (!AnimeDownloadPolicy.allowed(context)) return false;
        if (AnimeDownloads.isSaved(context, task.animeId, task.episodeKey)) return false;
        WorkManager manager;
        try {
            manager = WorkManager.getInstance(context.getApplicationContext());
        } catch (RuntimeException error) {
            Log.w(TAG, "WorkManager tidak siap; unduhan tidak dijadwalkan", error);
            return false;
        }
        try {
            manager.enqueueUniqueWork(workName(task.episodeKey, task.quality),
                    ExistingWorkPolicy.KEEP,
                    request(task));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unduhan gagal dijadwalkan", error);
            return false;
        }
        int percent = AnimeDownloadStatus.percent(task.episodeKey);
        AnimeDownloadStatus.set(task.episodeKey, AnimeDownloadStatus.QUEUED, percent,
                "Menunggu");
        return true;
    }

    /** Ulangi unduhan yang gagal; pekerjaan lama diganti bila masih ada. */
    public static boolean retry(Context context, AnimeDownloadTask task) {
        if (context == null || task == null || !task.downloadable()) return false;
        if (!AnimeDownloadPolicy.allowed(context)) return false;
        WorkManager manager;
        try {
            manager = WorkManager.getInstance(context.getApplicationContext());
        } catch (RuntimeException error) {
            return false;
        }
        try {
            manager.enqueueUniqueWork(workName(task.episodeKey, task.quality),
                    ExistingWorkPolicy.REPLACE,
                    request(task));
        } catch (RuntimeException error) {
            return false;
        }
        AnimeDownloadStatus.set(task.episodeKey, AnimeDownloadStatus.QUEUED,
                AnimeDownloadStatus.percent(task.episodeKey), "Menunggu");
        return true;
    }

    /** Hasil pembatalan, supaya layar bisa melaporkan keadaan yang sebenarnya. */
    public static final class CancelResult {
        /** Unduhan dihentikan dan sisa berkas sementara dibuang. */
        public static final int CANCELLED = 0;
        /** Terlambat: berkas sudah lengkap, jadi tidak ada yang dibatalkan. */
        public static final int ALREADY_SAVED = 1;
        /** Tidak ada unduhan berjalan untuk episode ini. */
        public static final int NOT_RUNNING = 2;

        public final int outcome;

        CancelResult(int outcome) {
            this.outcome = outcome;
        }

        public String message() {
            switch (outcome) {
                case ALREADY_SAVED:
                    return "Episode ini sudah tersimpan lengkap; berkasnya tidak dihapus.";
                case NOT_RUNNING:
                    return "Tidak ada unduhan berjalan untuk episode ini.";
                default:
                    return "Unduhan dibatalkan.";
            }
        }
    }

    /**
     * Hentikan unduhan satu episode.
     *
     * <p>Pembatalan menunjuk episode lewat tag stabil ({@link #episodeTag}), bukan
     * posisi baris, sehingga daftar yang bergeser atau kualitas yang berubah tidak
     * pernah membatalkan episode lain. Bila unduhan ternyata sudah selesai tepat
     * sebelum pembatalan, berkas lengkap dipertahankan dan hasilnya dilaporkan
     * sebagai {@link CancelResult#ALREADY_SAVED} — bukan dihapus.
     */
    public static CancelResult cancel(Context context, String animeId, String episodeKey) {
        if (context == null || episodeKey == null || episodeKey.isEmpty()) {
            return new CancelResult(CancelResult.NOT_RUNNING);
        }
        // Berkas lengkap lebih dulu: pembatalan yang datang berbarengan dengan
        // selesainya unduhan tidak boleh merusak hasil yang sah.
        if (AnimeDownloads.isSaved(context, animeId, episodeKey)) {
            AnimeDownloadStatus.set(episodeKey, AnimeDownloadStatus.SAVED, 100,
                    "Tersimpan offline", AnimeDownloadStatus.quality(episodeKey));
            return new CancelResult(CancelResult.ALREADY_SAVED);
        }

        int before = AnimeDownloadStatus.state(episodeKey);
        boolean running = before == AnimeDownloadStatus.QUEUED
                || before == AnimeDownloadStatus.DOWNLOADING
                || before == AnimeDownloadStatus.VERIFYING
                || before == AnimeDownloadStatus.PAUSED;

        try {
            WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
            manager.cancelAllWorkByTag(episodeTag(episodeKey));
            manager.cancelUniqueWork(workName(episodeKey, AnimeDownloadStatus.quality(episodeKey)));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unduhan tidak bisa dihentikan", error);
        }

        AnimeDownloads.discardPart(context, animeId, episodeKey);
        if (!AnimeDownloads.isSaved(context, animeId, episodeKey)) {
            AnimeDownloadStatus.clear(episodeKey);
        }
        return new CancelResult(running ? CancelResult.CANCELLED : CancelResult.NOT_RUNNING);
    }

    static OneTimeWorkRequest request(AnimeDownloadTask task) {
        Data data = new Data.Builder()
                .putString(AnimeDownloadWorker.KEY_ANIME_ID, task.animeId)
                .putString(AnimeDownloadWorker.KEY_ANIME_TITLE, task.animeTitle)
                .putString(AnimeDownloadWorker.KEY_POSTER, task.poster)
                .putString(AnimeDownloadWorker.KEY_EPISODE_LABEL, task.episodeLabel)
                .putString(AnimeDownloadWorker.KEY_EPISODE_URL, task.episodeUrl)
                .putString(AnimeDownloadWorker.KEY_EPISODE_KEY, task.episodeKey)
                .putString(AnimeDownloadWorker.KEY_QUALITY, task.quality)
                .putString(AnimeDownloadWorker.KEY_SERVER, task.server)
                .putString(AnimeDownloadWorker.KEY_URL, task.url)
                .putString(AnimeDownloadWorker.KEY_KIND, task.kind)
                .build();
        return new OneTimeWorkRequest.Builder(AnimeDownloadWorker.class)
                .setInputData(data)
                .addTag("anime-download")
                .addTag(episodeTag(task.episodeKey))
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,
                        10, TimeUnit.SECONDS)
                .build();
    }
}