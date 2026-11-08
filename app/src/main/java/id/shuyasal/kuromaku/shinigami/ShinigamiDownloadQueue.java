package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.util.Log;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Antrean unduhan Shinigami di atas WorkManager.
 *
 * <p>Pola ini mengikuti {@code DownloadQueue} milik Novel: antrean unik per
 * chapter (tidak ganda), batch multi-chapter berurutan, cancel/resume/refresh,
 * dan penghapusan file tidak pernah menyentuh progres baca di
 * {@link ShinigamiStore}. Worker-nya sendiri tetap gated oleh
 * {@link ShinigamiDownloadPolicy}: unduhan hanya dijadwalkan setelah pengguna
 * menyetujui pemberitahuan penyimpanan offline untuk pemakaian pribadi.
 */
public final class ShinigamiDownloadQueue {
    static final String KEY_CHAPTERS = "chapters";
    static final String KEY_MANGA_ID = "manga_id";
    static final String KEY_MANGA_TITLE = "manga_title";
    private static final String TAG = "ShinigamiDownload";

    private ShinigamiDownloadQueue() {
    }

    public static String workName(String chapterId) {
        return "shinigami-download-" + (chapterId == null ? "" : chapterId.trim());
    }

    public static String batchName(String mangaId) {
        return "shinigami-batch-" + (mangaId == null ? "" : mangaId.trim());
    }

    /** Antre satu chapter; mengembalikan false bila kebijakan melarang. */
    public static boolean enqueueChapter(Context context, String chapterId) {
        if (chapterId == null || chapterId.isEmpty()) return false;
        if (!ShinigamiDownloadPolicy.allowed(context)) return false;
        return enqueueChapters(context, "", "", single(chapterId)) > 0;
    }

    /** Antre beberapa chapter sekaligus; yang sudah tersimpan dilewati. */
    public static int enqueueChapters(Context context, String mangaId, String mangaTitle,
                                      List<String> chapterIds) {
        try {
            return enqueueChaptersUnsafe(context, mangaId, mangaTitle, chapterIds);
        } catch (Throwable error) {
            // Satu chapter yang gagal dijadwalkan tidak boleh mematikan aplikasi.
            Log.e(TAG, "chapter manga gagal dijadwalkan", error);
            return 0;
        }
    }

    private static int enqueueChaptersUnsafe(Context context, String mangaId, String mangaTitle,
                                             List<String> chapterIds) {
        if (context == null || chapterIds == null || chapterIds.isEmpty()) return 0;
        if (!ShinigamiDownloadPolicy.allowed(context)) return 0;
        List<String> missing = missing(context, chapterIds);
        if (missing.isEmpty()) return 0;
        for (String chapterId : missing) {
            // Nama yang bisa dibaca pengguna, supaya unduhan berjalan tampil di
            // layar Unduhan Manga Server 2.
            ShinigamiDownloadStatus.describe(chapterId,
                    mangaTitle == null || mangaTitle.isEmpty() ? "Manga" : mangaTitle,
                    chapterId, mangaId);
            ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.QUEUED, 0, "");
        }
        WorkManager manager;
        try {
            manager = WorkManager.getInstance(context.getApplicationContext());
        } catch (RuntimeException error) {
            Log.w(TAG, "WorkManager tidak siap; unduhan tidak dijadwalkan", error);
            for (String chapterId : missing) ShinigamiDownloadStatus.clear(chapterId);
            return 0;
        }
        for (String chapterId : missing) {
            manager.enqueueUniqueWork(workName(chapterId), ExistingWorkPolicy.KEEP,
                    request(mangaId, mangaTitle, single(chapterId)));
        }
        return missing.size();
    }

    /** Chapter yang belum lengkap tersimpan; murni, tanpa menyentuh WorkManager. */
    static List<String> missing(Context context, List<String> chapterIds) {
        List<String> missing = new ArrayList<>();
        if (context == null || chapterIds == null) return missing;
        ShinigamiStore store = new ShinigamiStore(context);
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        for (String chapterId : chapterIds) {
            if (chapterId == null || chapterId.isEmpty()) continue;
            if (local.isDownloaded(chapterId, store.expectedPages(chapterId))) continue;
            missing.add(chapterId);
        }
        return missing;
    }

    public static void cancelChapter(Context context, String chapterId) {
        if (context == null || chapterId == null || chapterId.isEmpty()) return;
        try {
            WorkManager.getInstance(context.getApplicationContext())
                    .cancelUniqueWork(workName(chapterId));
        } catch (RuntimeException error) {
            Log.w(TAG, "WorkManager tidak siap saat membatalkan unduhan", error);
        }
        if (ShinigamiDownloadStatus.state(chapterId) == ShinigamiDownloadStatus.QUEUED
                || ShinigamiDownloadStatus.state(chapterId) == ShinigamiDownloadStatus.DOWNLOADING) {
            ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.CANCELLED,
                    ShinigamiDownloadStatus.percent(chapterId), "");
        }
    }

    public static void cancelBatch(Context context, String mangaId, List<String> chapterIds) {
        if (context == null || chapterIds == null) return;
        for (String chapterId : chapterIds) cancelChapter(context, chapterId);
    }

    public static void retryChapter(Context context, String mangaId, String mangaTitle, String chapterId) {
        if (context == null || chapterId == null || chapterId.isEmpty()) return;
        if (!ShinigamiDownloadPolicy.allowed(context)) return;
        ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.QUEUED, 0, "");
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(workName(chapterId), ExistingWorkPolicy.REPLACE,
                        request(mangaId, mangaTitle, single(chapterId)));
    }

    /**
     * Selaraskan status di memori dengan keadaan WorkManager + berkas nyata.
     *
     * <p>Dipakai saat halaman detail dibuka kembali: pekerjaan yang masih
     * berjalan setelah proses aplikasi dimatikan tetap tampil "Mengunduh" pada
     * persentase halaman yang benar-benar tersimpan, bukan kembali ke nol.
     * Panggil dari thread latar.
     */
    public static void refreshStatus(Context context, List<String> chapterIds) {
        if (context == null || chapterIds == null || chapterIds.isEmpty()) return;
        ShinigamiStore store = new ShinigamiStore(context);
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        WorkManager manager;
        try {
            manager = WorkManager.getInstance(context.getApplicationContext());
        } catch (RuntimeException error) {
            Log.w(TAG, "WorkManager tidak siap saat menyelaraskan status", error);
            return;
        }
        for (String chapterId : chapterIds) {
            if (chapterId == null || chapterId.isEmpty()) continue;
            int expected = store.expectedPages(chapterId);
            if (expected <= 0) continue;
            boolean complete = local.isDownloaded(chapterId, expected);
            int percent = ShinigamiPageStore.percent(local, chapterId, expected);
            int state = ShinigamiDownloadStatus.state(chapterId);
            boolean working = state == ShinigamiDownloadStatus.QUEUED
                    || state == ShinigamiDownloadStatus.DOWNLOADING;
            if (complete && !working) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.SAVED, 100, "");
                continue;
            }
            androidx.work.WorkInfo info = latest(manager, chapterId);
            if (info == null) {
                if (percent > 0 && state == ShinigamiDownloadStatus.NOT_DOWNLOADED) {
                    // Sisa halaman dari sesi sebelumnya yang tidak dilanjutkan.
                    ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED,
                            percent, "Unduhan terhenti. Tekan Coba Lagi untuk melanjutkan.");
                }
                continue;
            }
            switch (info.getState()) {
                case ENQUEUED:
                case BLOCKED:
                    ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.QUEUED, percent, "");
                    break;
                case RUNNING:
                    ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.DOWNLOADING,
                            percent, "");
                    break;
                case SUCCEEDED:
                    ShinigamiDownloadStatus.set(chapterId,
                            complete ? ShinigamiDownloadStatus.SAVED : ShinigamiDownloadStatus.FAILED,
                            complete ? 100 : percent,
                            complete ? "" : "Sebagian halaman belum lengkap.");
                    break;
                case FAILED:
                case CANCELLED:
                    ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED, percent,
                            ShinigamiDownloadStatus.message(chapterId));
                    break;
                default:
                    break;
            }
        }
    }

    private static androidx.work.WorkInfo latest(WorkManager manager, String chapterId) {
        try {
            List<androidx.work.WorkInfo> infos = manager
                    .getWorkInfosForUniqueWork(workName(chapterId)).get();
            return infos == null || infos.isEmpty() ? null : infos.get(infos.size() - 1);
        } catch (Exception error) {
            Log.w(TAG, "gagal membaca status WorkManager", error);
            return null;
        }
    }

    /** Hapus file unduhan; progres baca di store TIDAK disentuh. */
    public static boolean deleteChapter(Context context, String chapterId) {
        if (context == null) return false;
        boolean removed = new ShinigamiDownloader(context).deleteChapter(chapterId);
        ShinigamiDownloadStatus.clear(chapterId);
        return removed;
    }

    static OneTimeWorkRequest request(String mangaId, String mangaTitle, List<String> chapterIds) {
        return new OneTimeWorkRequest.Builder(ShinigamiDownloadWorker.class)
                .setInputData(input(mangaId, mangaTitle, chapterIds))
                .addTag("shinigami-download")
                .build();
    }

    static Data input(String mangaId, String mangaTitle, List<String> chapterIds) {
        StringBuilder joined = new StringBuilder();
        for (String id : chapterIds) {
            if (joined.length() > 0) joined.append('\n');
            joined.append(id);
        }
        return new Data.Builder()
                .putString(KEY_MANGA_ID, mangaId == null ? "" : mangaId)
                .putString(KEY_MANGA_TITLE, mangaTitle == null ? "" : mangaTitle)
                .putString(KEY_CHAPTERS, joined.toString())
                .build();
    }

    private static List<String> single(String chapterId) {
        List<String> one = new ArrayList<>(1);
        if (chapterId != null && !chapterId.isEmpty()) one.add(chapterId);
        return one;
    }
}
