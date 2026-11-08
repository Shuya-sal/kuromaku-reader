package id.shuyasal.kuromaku.offline;

import android.util.Log;

import java.util.concurrent.Semaphore;

/**
 * Batas global jumlah transfer yang berjalan bersamaan.
 *
 * <p>Tiga modul punya antreannya sendiri. Tanpa batas bersama, satu chapter novel,
 * satu chapter manga, dan satu episode anime bisa berjalan serentak — tiga
 * koneksi besar, tiga penulisan berkas, tiga notifikasi. Di perangkat kelas
 * menengah itu berakhir pada tekanan memori dan UI tersendat, dan bila sistem
 * membunuh proses tepat saat tiga-tiganya menulis, status unduhan jadi kacau.
 *
 * <p>Satu slot, adil (FIFO). Pekerja yang menunggu tetap foreground, jadi sistem
 * tidak membunuhnya; yang dikorbankan hanya paralelisme, bukan unduhannya.
 */
public final class DownloadGate {

    private static final String TAG = "KuromakuDownloads";

    /** Jumlah transfer bersamaan yang diizinkan di seluruh aplikasi. */
    public static final int SLOTS = 1;

    private static final Semaphore SLOT = new Semaphore(SLOTS, true);

    private DownloadGate() {
    }

    /**
     * Ambil satu slot; menunggu giliran.
     *
     * @return true bila slot didapat. false bila thread diinterupsi (aplikasi
     *         dihentikan), sehingga pekerja bisa berhenti dengan rapi.
     */
    public static boolean acquire() {
        try {
            SLOT.acquire();
            return true;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "menunggu giliran unduhan dihentikan");
            return false;
        }
    }

    /** Kembalikan slot; wajib dipanggil di blok finally. */
    public static void release() {
        SLOT.release();
    }

    /** Berapa transfer yang sedang menunggu; dipakai pengujian dan diagnostik. */
    public static int waiting() {
        return SLOT.getQueueLength();
    }

    /** Berapa slot yang masih bebas; dipakai pengujian. */
    public static int available() {
        return SLOT.availablePermits();
    }
}