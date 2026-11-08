package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import androidx.work.WorkManager;

import java.io.File;

/**
 * Perbaikan antrean WorkManager yang rusak.
 *
 * <p>Basis data WorkManager hanya menyimpan metadata pekerjaan — bukan novel,
 * manga, anime, progres baca, atau berkas unduhan. Kalau berkas itu rusak
 * (misalnya karena proses dibunuh tepat saat menulis, atau karena crash
 * berulang), setiap panggilan antrean bisa melempar exception dan aplikasi
 * terlihat "force close setiap kali menekan Unduh" — bahkan setelah kode
 * unduhannya diperbaiki, karena kerusakannya menetap di disk.
 *
 * <p>Karena itu: bila antrean tidak bisa dibuka, berkas basis datanya dihapus
 * dan WorkManager membuat yang baru. Yang hilang hanya daftar pekerjaan lama
 * yang memang sudah tidak bisa dijalankan; berkas media dan progres pengguna
 * tidak disentuh.
 */
public final class WorkManagerRepair {

    private static final String TAG = "KuromakuDownloads";

    /** Berkas basis data WorkManager (beserta berkas pendampingnya). */
    private static final String[] DATABASE_FILES = {
            "androidx.work.workdb",
            "androidx.work.workdb-wal",
            "androidx.work.workdb-shm",
            "androidx.work.workdb-journal",
    };

    private WorkManagerRepair() {
    }

    /**
     * Pastikan antrean bisa dipakai; perbaiki bila rusak.
     *
     * @return true bila antrean sehat (atau berhasil diperbaiki).
     */
    public static boolean ensure(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        if (healthy(app)) return true;

        Log.w(TAG, "antrean WorkManager tidak bisa dibuka; basis data direset");
        deleteDatabase(app);
        boolean ok = healthy(app);
        if (!ok) Log.w(TAG, "antrean WorkManager masih tidak bisa dibuka");
        return ok;
    }

    private static boolean healthy(Context app) {
        try {
            WorkManager.getInstance(app);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "WorkManager tidak siap", error);
            return false;
        }
    }

    /** Hapus berkas basis data antrean; tidak menyentuh data pengguna. */
    static int deleteDatabase(Context app) {
        File dir = new File(app.getApplicationInfo().dataDir, "databases");
        int dihapus = 0;
        for (String name : DATABASE_FILES) {
            File file = new File(dir, name);
            if (file.isFile() && file.delete()) dihapus++;
        }
        return dihapus;
    }
}