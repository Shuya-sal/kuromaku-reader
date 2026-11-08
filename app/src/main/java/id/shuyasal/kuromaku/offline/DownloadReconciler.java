package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import id.shuyasal.kuromaku.anime.AnimeDownloadStatus;
import id.shuyasal.kuromaku.anime.AnimeDownloads;
import id.shuyasal.kuromaku.shinigami.ShinigamiDownloadStatus;
import id.shuyasal.kuromaku.shinigami.ShinigamiDownloads;

/**
 * Menyelaraskan status unduhan yang tersimpan dengan kenyataan setelah aplikasi
 * dibuka kembali.
 *
 * <p>Dua hal yang dibereskan:
 *
 * <ol>
 *   <li><b>Status menggantung.</b> Entri yang tercatat "antre/mengunduh" tetapi
 *       tidak berubah lagi (proses dibunuh, pekerjaan dibatalkan sistem, atau
 *       baterai OEM memblokir) ditandai {@code Dijeda} — bukan dibiarkan
 *       berputar selamanya, dan bukan dianggap selesai.</li>
 *   <li><b>Klaim tersimpan yang tidak sah.</b> Entri "tersimpan" yang berkasnya
 *       sudah tidak ada diturunkan menjadi gagal, sehingga tidak ada status
 *       tersimpan palsu.</li>
 * </ol>
 *
 * <p>Rekonsiliasi tidak pernah menghapus berkas unduhan dan tidak menyentuh
 * progres membaca/menonton.
 */
public final class DownloadReconciler {

    private static final String TAG = "KuromakuDownloads";

    /** Entri yang lebih tua dari ini dianggap tidak lagi berjalan. */
    static final long STALE_MS = 60_000L;

    private DownloadReconciler() {
    }

    /** Jalankan di luar thread utama. */
    public static void reconcile(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        DownloadStateFile.install(app);
        long now = System.currentTimeMillis();

        Set<String> savedChapters = null;
        for (Map.Entry<String, DownloadStateFile.Row> entry
                : DownloadStateFile.all().entrySet()) {
            String key = entry.getKey();
            DownloadStateFile.Row row = entry.getValue();
            try {
                if (key.startsWith(AnimeDownloadStatus.PREFIX)) {
                    reconcileAnime(app, key, row, now);
                } else if (key.startsWith(ShinigamiDownloadStatus.PREFIX)) {
                    if (savedChapters == null) savedChapters = savedShinigamiChapters(app);
                    reconcileShinigami(key, row, now, savedChapters);
                } else if (key.startsWith(DownloadStatusStore.PREFIX)) {
                    reconcileTransient(key, row, now);
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "rekonsiliasi status gagal untuk " + key, error);
            }
        }
    }

    /** Keadaan yang lebih muda dari ini tidak direkonsiliasi. */
    private static final long GRACE_MS = 30_000L;

    private static void reconcileAnime(Context context, String key,
                                       DownloadStateFile.Row row, long now) {
        String episodeKey = key.substring(AnimeDownloadStatus.PREFIX.length());
        if (row.state == AnimeDownloadStatus.SAVED) {
            // Entri yang baru saja ditulis belum tentu sudah punya berkas: unduhan
            // bisa sedang berjalan di proses lain. Rekonsiliasi hanya menyentuh
            // keadaan yang sudah lama, bukan yang baru berubah.
            if (now - row.updatedAt <= GRACE_MS) return;
            if (!AnimeDownloads.isSavedAnywhere(context, episodeKey)) {
                AnimeDownloadStatus.set(episodeKey, AnimeDownloadStatus.FAILED, 0,
                        "Berkas unduhan tidak ditemukan lagi.", row.quality);
            }
            return;
        }
        if (isTransient(row.state) && now - row.updatedAt > STALE_MS) {
            AnimeDownloadStatus.set(episodeKey, AnimeDownloadStatus.PAUSED, row.percent,
                    "Dijeda", row.quality);
        }
    }

    private static void reconcileShinigami(String key, DownloadStateFile.Row row, long now,
                                           Set<String> savedChapters) {
        String chapterId = key.substring(ShinigamiDownloadStatus.PREFIX.length());
        if (row.state == ShinigamiDownloadStatus.SAVED) {
            if (!savedChapters.contains(chapterId)) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED, 0,
                        "Halaman unduhan tidak ditemukan lagi.");
            }
            return;
        }
        if (isTransient(row.state) && now - row.updatedAt > STALE_MS) {
            // Manga tidak punya status "dijeda" tersendiri: sebagian halaman
            // yang sudah sah dilaporkan sebagai PARTIAL, bukan hilang.
            ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.PARTIAL,
                    row.percent, "Dijeda");
        }
    }

    private static void reconcileTransient(String key, DownloadStateFile.Row row, long now) {
        if (!isTransient(row.state) || now - row.updatedAt <= STALE_MS) return;
        String novelKey = key.substring(DownloadStatusStore.PREFIX.length());
        DownloadStatusStore.set(novelKey, DownloadStatusStore.PARTIAL, "Dijeda");
    }

    private static boolean isTransient(int state) {
        return state == AnimeDownloadStatus.QUEUED
                || state == AnimeDownloadStatus.DOWNLOADING
                || state == AnimeDownloadStatus.VERIFYING
                || state == ShinigamiDownloadStatus.QUEUED
                || state == ShinigamiDownloadStatus.DOWNLOADING
                || state == ShinigamiDownloadStatus.VERIFYING
                || state == DownloadStatusStore.QUEUED
                || state == DownloadStatusStore.DOWNLOADING
                || state == DownloadStatusStore.VERIFYING;
    }

    private static Set<String> savedShinigamiChapters(Context context) {
        Set<String> ids = new HashSet<>();
        try {
            for (ShinigamiDownloads.Manga manga : ShinigamiDownloads.scan(context)) {
                for (ShinigamiDownloads.Chapter chapter : manga.chapters) {
                    if (chapter.complete) ids.add(chapter.chapterId);
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "pemindaian unduhan manga gagal", error);
        }
        return ids;
    }
}