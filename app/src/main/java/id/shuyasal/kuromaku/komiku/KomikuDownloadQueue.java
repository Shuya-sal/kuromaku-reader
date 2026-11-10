package id.shuyasal.kuromaku.komiku;

import android.content.Context;
import android.util.Log;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Antrean unduhan Komiku.
 *
 * <p>Setiap chapter menjadi satu pekerjaan WorkManager dengan nama unik yang
 * stabil, jadi menekan Unduh dua kali tidak pernah menghasilkan dua unduhan
 * untuk chapter yang sama, dan pekerjaan bisa dibatalkan per chapter.
 */
public final class KomikuDownloadQueue {

    private static final String TAG = "KomikuDownload";

    private KomikuDownloadQueue() {
    }

    public static String workName(String chapterKey) {
        return "komiku-download-" + (chapterKey == null ? "" : chapterKey.trim());
    }

    /** Antre satu chapter. Mengembalikan false bila tidak dijadwalkan. */
    public static boolean enqueueChapter(Context context, String chapterKey, String mangaId,
                                         String mangaTitle, String label) {
        if (chapterKey == null || chapterKey.isEmpty()) return false;
        return enqueue(context, mangaId, mangaTitle,
                mapOf(chapterKey, label)) > 0;
    }

    /**
     * Antre beberapa chapter sekaligus.
     *
     * <p>Chapter yang seluruh halamannya sudah valid dilewati, jadi menekan
     * "Unduh semua" pada judul yang sebagian sudah tersimpan hanya mengunduh
     * sisanya.
     */
    public static int enqueue(Context context, String mangaId, String mangaTitle,
                              Map<String, String> chapters) {
        try {
            return enqueueUnsafe(context, mangaId, mangaTitle, chapters);
        } catch (Throwable error) {
            // Satu jadwal gagal tidak boleh mematikan aplikasi.
            Log.e(TAG, "chapter manga gagal dijadwalkan", error);
            return 0;
        }
    }

    private static int enqueueUnsafe(Context context, String mangaId, String mangaTitle,
                                     Map<String, String> chapters) {
        if (context == null || chapters == null || chapters.isEmpty()) return 0;
        KomikuDownloads local = new KomikuDownloads(context);
        Map<String, String> missing = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : chapters.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) continue;
            // Halaman yang sudah tersimpan tidak diketahui panjangnya sebelum
            // sumber dibaca, jadi keberadaan direktori berisi berkas valid
            // dipakai sebagai penanda: bila semua berkas yang ada valid dan
            // jumlahnya sudah sama dengan catatan, chapter dilewati.
            KomikuDownloads.Entry recorded = entryFor(local, key);
            if (recorded != null && recorded.pages > 0
                    && local.isDownloaded(key, recorded.pages)) {
                continue;
            }
            missing.put(key, entry.getValue());
        }
        if (missing.isEmpty()) return 0;

        WorkManager manager;
        try {
            manager = WorkManager.getInstance(context.getApplicationContext());
        } catch (RuntimeException error) {
            Log.w(TAG, "WorkManager tidak siap; unduhan tidak dijadwalkan", error);
            return 0;
        }
        for (Map.Entry<String, String> entry : missing.entrySet()) {
            String key = entry.getKey();
            KomikuDownloadStatus.set(key, KomikuDownloadStatus.QUEUED, 0, "");
            KomikuDownloadStatus.describe(key,
                    mangaTitle == null || mangaTitle.isEmpty() ? "Manga" : mangaTitle,
                    entry.getValue() == null ? "" : entry.getValue(), mangaId);
            manager.enqueueUniqueWork(workName(key), ExistingWorkPolicy.KEEP,
                    request(key, mangaId, mangaTitle, entry.getValue()));
        }
        return missing.size();
    }

    private static KomikuDownloads.Entry entryFor(KomikuDownloads local, String chapterKey) {
        for (KomikuDownloads.Entry entry : local.entries()) {
            if (chapterKey.equals(entry.chapterKey)) return entry;
        }
        return null;
    }

    private static OneTimeWorkRequest request(String chapterKey, String mangaId,
                                              String mangaTitle, String label) {
        Data data = new Data.Builder()
                .putString(KomikuDownloadWorker.KEY_CHAPTER, chapterKey)
                .putString(KomikuDownloadWorker.KEY_MANGA_ID, mangaId == null ? "" : mangaId)
                .putString(KomikuDownloadWorker.KEY_MANGA_TITLE,
                        mangaTitle == null ? "" : mangaTitle)
                .putString(KomikuDownloadWorker.KEY_LABEL, label == null ? "" : label)
                .build();
        return new OneTimeWorkRequest.Builder(KomikuDownloadWorker.class)
                .setInputData(data)
                .addTag("komiku-download")
                .build();
    }

    /** Batalkan satu chapter; halaman yang sudah lengkap tetap tersimpan. */
    public static void cancelChapter(Context context, String chapterKey) {
        if (context == null || chapterKey == null || chapterKey.isEmpty()) return;
        try {
            WorkManager.getInstance(context.getApplicationContext())
                    .cancelUniqueWork(workName(chapterKey));
        } catch (RuntimeException error) {
            Log.w(TAG, "WorkManager tidak siap saat membatalkan unduhan", error);
        }
    }

    /** Hapus berkas chapter beserta catatannya. */
    public static boolean deleteChapter(Context context, String chapterKey) {
        if (context == null || chapterKey == null || chapterKey.isEmpty()) return false;
        cancelChapter(context, chapterKey);
        return new KomikuDownloads(context).delete(chapterKey);
    }

    private static Map<String, String> mapOf(String chapterKey, String label) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(chapterKey, label == null ? "" : label);
        return map;
    }

    private static List<String> single(String value) {
        List<String> out = new ArrayList<>();
        out.add(value);
        return out;
    }
}
