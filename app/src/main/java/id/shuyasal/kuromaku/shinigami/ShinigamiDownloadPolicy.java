package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Persetujuan unduhan offline Shinigami.
 *
 * <p>Status yang sebenarnya: gambar chapter dilayani publik oleh
 * {@code assets.shngm.id} tanpa login, token, DRM, atau CAPTCHA — aplikasi tidak
 * melewati proteksi apa pun. Yang <b>tidak</b> bisa dipastikan adalah pernyataan
 * izin tertulis: halaman syarat layanan sumber tertahan Cloudflare, jadi tidak
 * ada dasar untuk mengklaim "sumber mengizinkan penyimpanan offline".
 *
 * <p>Karena itu unduhan tidak dibuka diam-diam lewat flag tersembunyi. Aplikasi
 * menampilkan pemberitahuan sekali, menjelaskan bahwa berkas disimpan untuk
 * penggunaan pribadi di perangkat ini dan tidak untuk disebarkan ulang, lalu
 * meminta persetujuan pengguna. Tanpa persetujuan, tidak ada berkas yang
 * disimpan; chapter tetap bisa dibaca online.
 */
public final class ShinigamiDownloadPolicy {

    private static final String KEY = "shinigami_offline_consent";
    private static volatile Boolean override;

    private ShinigamiDownloadPolicy() {
    }

    public static boolean allowed(Context context) {
        if (override != null) return override;
        return context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
    }

    public static void acknowledge(Context context) {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, true).apply();
    }

    public static void revoke(Context context) {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    /** Teks dialog persetujuan sebelum unduhan pertama. */
    public static String notice() {
        return "Gambar chapter diambil dari server publik sumber tanpa melewati login, DRM, "
                + "atau CAPTCHA. Halaman syarat layanan sumber tidak dapat diakses dari aplikasi, "
                + "jadi izin tertulis untuk penyimpanan offline tidak bisa dipastikan.\n\n"
                + "Dengan menyetujui, berkas chapter disimpan di penyimpanan khusus aplikasi untuk "
                + "dibaca sendiri di perangkat ini — bukan untuk disebarkan ulang. Unduhan bisa "
                + "dihapus kapan saja tanpa menghapus progres membaca.\n\n"
                + "Jika sumber kelak menyatakan izin tidak diberikan, matikan lewat Pengaturan "
                + "dan hapus unduhan yang ada.";
    }

    public static String blockedMessage() {
        return "Unduhan offline butuh persetujuan lebih dulu: izin tertulis dari sumber belum "
                + "dapat diverifikasi, jadi aplikasi tidak menyimpan gambar chapter secara "
                + "diam-diam. Chapter tetap bisa dibaca online.";
    }

    /** Hanya untuk pengujian: tidak dipakai kode produksi. */
    public static void unlockForTesting(boolean value) {
        override = value;
    }

    static void resetForTesting() {
        override = null;
    }
}
