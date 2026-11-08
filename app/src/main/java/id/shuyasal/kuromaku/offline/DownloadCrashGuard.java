package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.WorkManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Pengaman crash-loop untuk pekerjaan unduhan.
 *
 * <p>Cara kerjanya sengaja sederhana dan tahan mati mendadak:
 *
 * <ol>
 *   <li>Sebelum pekerjaan unduhan mulai, kunci episode ditulis ke preferensi
 *       sebagai "sedang berjalan".</li>
 *   <li>Setelah pekerjaan selesai (apa pun hasilnya), catatan itu dihapus.</li>
 *   <li>Kalau aplikasi dibuka dan catatan itu <b>masih ada</b>, berarti proses
 *       sebelumnya mati saat pekerjaan itu berjalan. Episode tersebut dicatat
 *       sebagai pernah membunuh proses, dan pekerjaannya dibatalkan sebelum
 *       WorkManager sempat menjalankannya lagi.</li>
 * </ol>
 *
 * <p>Ini yang memutus siklus "aplikasi tidak bisa dibuka lagi": satu pekerjaan
 * yang merusak proses tidak akan pernah dijalankan otomatis untuk kedua kalinya.
 * Pengguna tetap bisa memulainya sendiri lewat tombol Unduh setelah aplikasi
 * terbuka, dan berkas yang sudah tersimpan tidak disentuh.
 */
public final class DownloadCrashGuard {

    private static final String TAG = "KuromakuDownloads";
    private static final String FILE = "download-crash-guard";
    private static final String KEY_RUNNING = "running_key";
    private static final String KEY_SUSPECT = "suspect_keys";

    private DownloadCrashGuard() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Catat bahwa pekerjaan untuk episode ini sedang berjalan. */
    public static void begin(Context context, String episodeKey) {
        if (context == null || episodeKey == null || episodeKey.isEmpty()) return;
        try {
            prefs(context).edit().putString(KEY_RUNNING, episodeKey).apply();
        } catch (RuntimeException error) {
            Log.w(TAG, "penanda pekerjaan tidak bisa ditulis", error);
        }
    }

    /** Hapus catatan karena pekerjaan sudah berakhir (berhasil atau gagal). */
    public static void end(Context context) {
        if (context == null) return;
        try {
            prefs(context).edit().remove(KEY_RUNNING).apply();
        } catch (RuntimeException error) {
            Log.w(TAG, "penanda pekerjaan tidak bisa dihapus", error);
        }
    }

    /** Benar bila episode ini pernah membuat proses mati. */
    public static boolean isSuspect(Context context, String episodeKey) {
        if (context == null || episodeKey == null || episodeKey.isEmpty()) return false;
        try {
            return prefs(context).getStringSet(KEY_SUSPECT, new HashSet<>())
                    .contains(episodeKey);
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Dipanggil saat aplikasi dibuka: kalau ada penanda yang tertinggal, proses
     * sebelumnya mati di tengah pekerjaan.
     *
     * @return kunci episode yang dicurigai, atau kosong bila tidak ada.
     */
    public static String recover(Context context) {
        if (context == null) return "";
        String running;
        try {
            running = prefs(context).getString(KEY_RUNNING, "");
        } catch (RuntimeException error) {
            return "";
        }
        if (running == null || running.isEmpty()) return "";

        try {
            Set<String> suspect = new HashSet<>(
                    prefs(context).getStringSet(KEY_SUSPECT, new HashSet<>()));
            suspect.add(running);
            prefs(context).edit()
                    .putStringSet(KEY_SUSPECT, suspect)
                    .remove(KEY_RUNNING)
                    .apply();
        } catch (RuntimeException error) {
            Log.w(TAG, "penanda pekerjaan tidak bisa diperbarui", error);
        }

        // Batalkan pekerjaan episode ini saja, sebelum WorkManager menjalankannya.
        try {
            WorkManager.getInstance(context.getApplicationContext())
                    .cancelAllWorkByTag(id.shuyasal.kuromaku.anime.AnimeDownloadQueue
                            .episodeTag(running));
        } catch (RuntimeException error) {
            Log.w(TAG, "pekerjaan bermasalah tidak bisa dibatalkan", error);
        }

        try {
            id.shuyasal.kuromaku.anime.AnimeDownloadStatus.set(running,
                    id.shuyasal.kuromaku.anime.AnimeDownloadStatus.FAILED, 0,
                    "Unduhan sebelumnya terhenti. Silakan coba lagi.",
                    id.shuyasal.kuromaku.anime.AnimeDownloadStatus.quality(running));
        } catch (RuntimeException error) {
            Log.w(TAG, "status episode bermasalah tidak bisa ditulis", error);
        }
        return running;
    }

    /** Buang seluruh catatan; dipakai pengujian. */
    static void reset(Context context) {
        try {
            prefs(context).edit().clear().apply();
        } catch (RuntimeException ignored) {
            // tidak ada yang bisa dilakukan
        }
    }
}