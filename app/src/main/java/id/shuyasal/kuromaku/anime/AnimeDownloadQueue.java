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

    public static String workName(String episodeKey) {
        return PREFIX + (episodeKey == null ? "" : episodeKey.trim());
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
            manager.enqueueUniqueWork(workName(task.episodeKey), ExistingWorkPolicy.KEEP,
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
            manager.enqueueUniqueWork(workName(task.episodeKey), ExistingWorkPolicy.REPLACE,
                    request(task));
        } catch (RuntimeException error) {
            return false;
        }
        AnimeDownloadStatus.set(task.episodeKey, AnimeDownloadStatus.QUEUED,
                AnimeDownloadStatus.percent(task.episodeKey), "Menunggu");
        return true;
    }

    /**
     * Hentikan unduhan berjalan.
     *
     * <p>Berkas sementara tidak dibuang, jadi unduhan bisa dilanjutkan. Status
     * menjadi {@code Terjeda} bila sudah ada byte tersimpan, atau hilang bila
     * belum ada apa pun.
     */
    public static void cancel(Context context, String animeId, String episodeKey) {
        if (context == null || episodeKey == null || episodeKey.isEmpty()) return;
        try {
            WorkManager.getInstance(context.getApplicationContext())
                    .cancelUniqueWork(workName(episodeKey));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unduhan tidak bisa dihentikan", error);
        }
        long saved = AnimeDownloads.savedBytes(context, animeId, episodeKey);
        if (saved > 0) {
            AnimeDownloadStatus.set(episodeKey, AnimeDownloadStatus.PAUSED,
                    AnimeDownloadStatus.percent(episodeKey), "Terjeda");
        } else {
            AnimeDownloadStatus.clear(episodeKey);
        }
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
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,
                        10, TimeUnit.SECONDS)
                .build();
    }
}