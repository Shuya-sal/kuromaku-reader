package id.shuyasal.kuromaku.komiku;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.List;

/**
 * Worker unduhan Komiku: satu chapter per pekerjaan, halaman berurutan.
 *
 * <p>Sama seperti Manga Server 2: halaman yang sudah valid dilewati (resume),
 * setiap gambar divalidasi sebelum disimpan, dan chapter baru ditandai
 * tersimpan bila SEMUA halaman lolos. Membatalkan pekerjaan berhenti di halaman
 * yang sedang berjalan; halaman yang sudah lengkap tetap tersimpan. Progres
 * baca tidak disentuh.
 */
public class KomikuDownloadWorker extends Worker {

    private static final String TAG = "KomikuDownload";

    static final String KEY_CHAPTER = "chapter_url";
    static final String KEY_MANGA_ID = "manga_id";
    static final String KEY_MANGA_TITLE = "manga_title";
    static final String KEY_LABEL = "chapter_label";

    public KomikuDownloadWorker(@NonNull Context context,
                                @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public com.google.common.util.concurrent.ListenableFuture<androidx.work.ForegroundInfo>
            getForegroundInfoAsync() {
        androidx.work.impl.utils.futures.SettableFuture<androidx.work.ForegroundInfo> future =
                androidx.work.impl.utils.futures.SettableFuture.create();
        future.set(id.shuyasal.kuromaku.offline.ForegroundDownloads.info(
                getApplicationContext(), notificationId(), "Mengunduh chapter manga",
                "Menyiapkan halaman", -1, tag()));
        return future;
    }

    private int notificationId() {
        return 6300 + Math.abs(safeChapter().hashCode() % 400);
    }

    private String tag() {
        return KomikuDownloadQueue.workName(safeChapter());
    }

    private String safeChapter() {
        return getInputData().getString(KEY_CHAPTER) == null
                ? "" : getInputData().getString(KEY_CHAPTER);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            return runJob();
        } catch (Throwable error) {
            Log.e(TAG, "unduhan manga gagal tak terduga", error);
            return Result.failure();
        }
    }

    private Result runJob() {
        String chapterKey = safeChapter();
        if (chapterKey.isEmpty()) return Result.failure();
        String mangaTitle = getInputData().getString(KEY_MANGA_TITLE);
        String label = getInputData().getString(KEY_LABEL);

        try {
            setForegroundAsync(id.shuyasal.kuromaku.offline.ForegroundDownloads.info(
                    getApplicationContext(), notificationId(), "Mengunduh chapter manga",
                    label == null ? "Menyiapkan halaman" : label, -1, tag()));
        } catch (RuntimeException error) {
            Log.w(TAG, "notifikasi progres tidak bisa ditampilkan", error);
        }

        String guardKey = "komiku|" + chapterKey;
        if (id.shuyasal.kuromaku.offline.DownloadCrashGuard
                .isSuspect(getApplicationContext(), guardKey)) {
            KomikuDownloadStatus.set(chapterKey, KomikuDownloadStatus.FAILED, 0,
                    "Unduhan sebelumnya terhenti. Silakan coba lagi.");
            return Result.failure();
        }
        id.shuyasal.kuromaku.offline.DownloadCrashGuard.begin(getApplicationContext(), guardKey);
        if (!id.shuyasal.kuromaku.offline.DownloadGate.acquire()) {
            id.shuyasal.kuromaku.offline.DownloadCrashGuard.end(getApplicationContext());
            return Result.retry();
        }
        try {
            KomikuDownloads local = new KomikuDownloads(getApplicationContext());
            List<String> urls;
            try {
                urls = new KomikuSource().images(chapterKey);
            } catch (KomikuSource.SourceException error) {
                KomikuDownloadStatus.set(chapterKey, KomikuDownloadStatus.FAILED, 0,
                        safeMessage(error.getMessage()));
                return Result.retry();
            }
            if (urls.isEmpty()) {
                KomikuDownloadStatus.set(chapterKey, KomikuDownloadStatus.FAILED, 0,
                        "Sumber tidak memberi gambar untuk chapter ini.");
                return Result.failure();
            }
            local.remember(chapterKey, getInputData().getString(KEY_MANGA_ID),
                    mangaTitle, label, urls.size());
            KomikuDownloadStatus.set(chapterKey, KomikuDownloadStatus.DOWNLOADING, 0, "");
            try {
                KomikuDownloads.Result result = local.download(chapterKey, urls,
                        (done, total) -> {
                            int percent = total <= 0 ? 0 : done * 100 / total;
                            KomikuDownloadStatus.set(chapterKey,
                                    KomikuDownloadStatus.DOWNLOADING, percent, "");
                            try {
                                setProgressAsync(new androidx.work.Data.Builder()
                                        .putInt("done", done).putInt("total", total).build());
                            } catch (RuntimeException ignored) {
                                // Progres hanya tampilan; kegagalan di sini
                                // tidak membatalkan unduhan.
                            }
                        });
                if (result.complete) {
                    KomikuDownloadStatus.set(chapterKey, KomikuDownloadStatus.SAVED, 100, "");
                    return Result.success();
                }
                KomikuDownloadStatus.set(chapterKey, KomikuDownloadStatus.PARTIAL,
                        result.total <= 0 ? 0 : result.saved * 100 / result.total,
                        result.message);
                return Result.retry();
            } catch (IOException error) {
                int expected = urls.size();
                int saved = local.savedPages(chapterKey, expected);
                KomikuDownloadStatus.set(chapterKey,
                        saved > 0 ? KomikuDownloadStatus.PARTIAL : KomikuDownloadStatus.FAILED,
                        expected <= 0 ? 0 : saved * 100 / expected,
                        safeMessage(error.getMessage()));
                return Result.retry();
            }
        } finally {
            id.shuyasal.kuromaku.offline.DownloadGate.release();
            id.shuyasal.kuromaku.offline.DownloadCrashGuard.end(getApplicationContext());
        }
    }

    /** Pesan yang aman ditampilkan: tanpa token, cookie, atau alamat lengkap. */
    static String safeMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "Unduhan gagal.";
        String cleaned = message.replaceAll("https?://\\S+", "[tautan]");
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }
}
