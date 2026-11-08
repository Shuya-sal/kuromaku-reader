package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Worker unduhan satu episode anime.
 *
 * <p>Worker ini hanya menyiapkan masukan, memeriksa izin, memanggil
 * {@link AnimeDownloadFetcher}, lalu menerjemahkan hasilnya menjadi status
 * WorkManager. Seluruh aturan pengunduhan ada di fetcher supaya bisa diuji
 * terhadap server sungguhan.
 */
public class AnimeDownloadWorker extends Worker {

    public static final String KEY_ANIME_ID = "anime_id";
    public static final String KEY_ANIME_TITLE = "anime_title";
    public static final String KEY_POSTER = "poster";
    public static final String KEY_EPISODE_LABEL = "episode_label";
    public static final String KEY_EPISODE_URL = "episode_url";
    public static final String KEY_EPISODE_KEY = "episode_key";
    public static final String KEY_QUALITY = "quality";
    public static final String KEY_SERVER = "server";
    public static final String KEY_URL = "url";
    public static final String KEY_KIND = "kind";

    private static final String TAG = "AnimeDownload";

    public AnimeDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * ForegroundInfo untuk WorkManager.
     *
     * <p>Bawaan {@code Worker} melempar {@code IllegalStateException} bila
     * WorkManager perlu mempromosikan pekerjaan ini di luar {@code doWork()},
     * misalnya saat pekerjaan dijalankan ulang setelah proses dibunuh sistem.
     */
    @NonNull
    @Override
    public com.google.common.util.concurrent.ListenableFuture<androidx.work.ForegroundInfo>
            getForegroundInfoAsync() {
        String episodeKey = text(KEY_EPISODE_KEY);
        String label = text(KEY_EPISODE_LABEL);
        int notificationId = 5100 + Math.abs(episodeKey.hashCode() % 400);
        androidx.work.impl.utils.futures.SettableFuture<androidx.work.ForegroundInfo> future =
                androidx.work.impl.utils.futures.SettableFuture.create();
        future.set(id.shuyasal.kuromaku.offline.ForegroundDownloads.info(
                getApplicationContext(), notificationId,
                "Mengunduh " + (label.isEmpty() ? "Episode" : label),
                "Melanjutkan unduhan", -1,
                AnimeDownloadQueue.episodeTag(episodeKey)));
        return future;
    }

    @NonNull
    @Override public Result doWork() {
        final String key = getInputData().getString(KEY_EPISODE_KEY);
        if (key != null && !key.isEmpty()
                && id.shuyasal.kuromaku.offline.DownloadCrashGuard
                        .isSuspect(getApplicationContext(), key)) {
            // Episode ini pernah membuat proses mati saat diunduh. Menjalankannya
            // otomatis lagi akan mengulang crash yang sama; pengguna memulainya
            // sendiri lewat tombol Unduh setelah aplikasi terbuka.
            AnimeDownloadStatus.set(key, AnimeDownloadStatus.FAILED, 0,
                    "Unduhan sebelumnya terhenti. Silakan coba lagi.");
            return Result.failure();
        }
        if (key != null && !key.isEmpty()) {
            id.shuyasal.kuromaku.offline.DownloadCrashGuard
                    .begin(getApplicationContext(), key);
        }
        try {
            return runDownload();
        } catch (Throwable error) {
            // Satu episode yang gagal tidak boleh mematikan proses, apalagi
            // membuat aplikasi tidak bisa dibuka lagi.
            Log.e(TAG, "unduhan episode gagal tak terduga", error);
            if (key != null && !key.isEmpty()) {
                AnimeDownloadStatus.set(key, AnimeDownloadStatus.FAILED, 0,
                        "Unduhan gagal. Silakan coba lagi.");
            }
            return Result.failure();
        } finally {
            id.shuyasal.kuromaku.offline.DownloadCrashGuard.end(getApplicationContext());
        }
    }

    private Result runDownload() {
        Context context = getApplicationContext();
        AnimeDownloadFetcher.Params params = new AnimeDownloadFetcher.Params(
                text(KEY_ANIME_ID), text(KEY_ANIME_TITLE), text(KEY_POSTER),
                text(KEY_EPISODE_LABEL), text(KEY_EPISODE_URL), text(KEY_EPISODE_KEY),
                text(KEY_QUALITY), text(KEY_SERVER), text(KEY_URL), text(KEY_KIND));

        if (params.episodeKey.isEmpty()) {
            Log.w(TAG, "unduhan tanpa penanda episode dilewati");
            return Result.failure();
        }
        if (isStopped()) {
            AnimeDownloadStatus.clear(params.episodeKey);
            return Result.failure();
        }
        if (!AnimeDownloadPolicy.allowed(context)) {
            AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.FAILED, 0,
                    "Unduhan offline belum diizinkan.");
            return Result.failure();
        }

        // Unduhan episode bisa berukuran ratusan MB: jalankan sebagai foreground
        // worker supaya sistem tidak mematikannya saat pengguna pindah aplikasi.
        final int notificationId = 5100 + Math.abs(params.episodeKey.hashCode() % 400);
        final String cancelTag = AnimeDownloadQueue.episodeTag(params.episodeKey);
        final String judul = params.episodeLabel.isEmpty() ? "Episode" : params.episodeLabel;
        try {
            setForegroundAsync(id.shuyasal.kuromaku.offline.ForegroundDownloads.info(
                    context, notificationId, "Mengunduh " + judul,
                    params.quality.isEmpty() ? "Menyiapkan berkas" : params.quality,
                    -1, cancelTag));
        } catch (RuntimeException error) {
            Log.w(TAG, "notifikasi progres tidak bisa ditampilkan", error);
        }
        final AnimeDownloadStatus.Listener progres = () -> {
            int persen = AnimeDownloadStatus.total(params.episodeKey) > 0
                    ? AnimeDownloadStatus.percent(params.episodeKey) : -1;
            id.shuyasal.kuromaku.offline.ForegroundDownloads.update(context, notificationId,
                    "Mengunduh " + judul,
                    persen < 0 ? "Mengunduh\u2026" : persen + "%",
                    persen, cancelTag);
        };
        AnimeDownloadStatus.addListener(progres);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        // Satu transfer besar dalam satu waktu di seluruh aplikasi: novel, manga,
        // dan anime berbagi slot yang sama supaya tidak saling menekan memori.
        if (!id.shuyasal.kuromaku.offline.DownloadGate.acquire()) {
            AnimeDownloadStatus.removeListener(progres);
            id.shuyasal.kuromaku.offline.ForegroundDownloads.clear(context, notificationId);
            return Result.retry();
        }
        AnimeDownloadFetcher.Result result;
        try {
            result = AnimeDownloadFetcher.run(context, params, client, this::isStopped);
        } finally {
            id.shuyasal.kuromaku.offline.DownloadGate.release();
            AnimeDownloadStatus.removeListener(progres);
            id.shuyasal.kuromaku.offline.ForegroundDownloads.clear(context, notificationId);
        }

        switch (result.outcome) {
            case SAVED:
                AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.SAVED, 100,
                        params.quality.isEmpty() ? "Tersimpan offline"
                                : "Tersimpan \u00b7 " + params.quality, params.quality);
                return Result.success();
            case CANCELLED:
                // Pembatalan bukan kegagalan jaringan: mengulang di sini akan
                // menghidupkan kembali unduhan yang baru saja dibatalkan.
                AnimeDownloads.discardPart(context, params.animeId, params.episodeKey);
                AnimeDownloadStatus.clear(params.episodeKey);
                return Result.failure();
            case INCOMPLETE:
                // Percobaan dibatasi: pekerjaan yang selalu gagal tidak boleh
                // diulang selamanya, karena itulah yang membuat aplikasi
                // tampak "tidak bisa dibuka" setiap kali dijalankan.
                if (getRunAttemptCount() + 1 >= id.shuyasal.kuromaku.offline
                        .DownloadQuarantine.MAX_ATTEMPTS) {
                    AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.FAILED,
                            percent(result), "Unduhan gagal. Silakan coba lagi.");
                    return Result.failure();
                }
                AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.PAUSED,
                        percent(result), result.message);
                // Dilanjutkan otomatis bila sistem mengizinkan.
                return Result.retry();
            default:
                AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.FAILED,
                        percent(result), result.message);
                return Result.failure();
        }
    }

    private static int percent(AnimeDownloadFetcher.Result result) {
        if (result.total <= 0) return 0;
        return (int) Math.max(0L, Math.min(100L, result.bytes * 100L / result.total));
    }

    private String text(String key) {
        String value = getInputData().getString(key);
        return value == null ? "" : value;
    }
}