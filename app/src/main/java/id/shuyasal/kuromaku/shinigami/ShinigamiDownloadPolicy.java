package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

/**
 * Gerbang izin unduhan Shinigami.
 *
 * <p>Default: TERKUNCI. Tanpa bukti izin eksplisit dari sumber, worker tidak
 * dijadwalkan dan dialog pembatasan tetap tampil. Ini keputusan yang diminta
 * Tuan dan dipertahankan dari rilis sebelumnya: API 200 bukan izin
 * penyimpanan, dan halaman situs tertahan Cloudflare sehingga ToS tidak bisa
 * diverifikasi. Saat izin tertulis sudah ada, buka lewat
 * {@link #unlockForTesting} hanya dari pengujian, atau ganti implementasi ini
 * dengan pemeriksaan lisensi yang sebenarnya — jangan sekadar menghapus
 * dialog.
 */
public final class ShinigamiDownloadPolicy {
    private static volatile boolean unlocked;

    private ShinigamiDownloadPolicy() {
    }

    public static boolean allowed(Context context) {
        return unlocked;
    }

    /** Hanya untuk pengujian: tidak dipakai kode produksi. */
    public static void unlockForTesting(boolean value) {
        unlocked = value;
    }

    public static String blockedMessage() {
        return "Unduhan offline Shinigami dinonaktifkan. Sumber belum menyatakan izin "
                + "pengambilan konten untuk reader pihak ketiga, jadi aplikasi tidak menyimpan "
                + "gambar chapter secara permanen. Chapter tetap dapat dibaca secara online.";
    }
}
