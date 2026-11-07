package id.shuyasal.kuromaku.anime;

import android.content.Context;

import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Persetujuan unduhan episode anime.
 *
 * <p>Status yang sebenarnya: berkas {@code .mp4} yang diputar aplikasi
 * diterbitkan apa adanya oleh halaman episode ({@code cdn.odcloud.net}), dapat
 * dibuka tanpa login, token, DRM, atau CAPTCHA, dan mendukung permintaan
 * {@code Range}. Aplikasi tidak melewati proteksi apa pun dan tidak menebak
 * alamat media di balik mirror yang hanya menyediakan pemutar pihak ketiga.
 *
 * <p>Yang <b>tidak</b> dapat dipastikan adalah pernyataan izin tertulis dari
 * penyedia. Karena itu unduhan tidak dibuka diam-diam: aplikasi menjelaskan
 * sekali bahwa berkas disimpan untuk ditonton sendiri di perangkat ini dan
 * tidak untuk disebarkan ulang, lalu meminta persetujuan. Tanpa persetujuan,
 * tidak ada berkas yang disimpan; episode tetap bisa ditonton online.
 */
public final class AnimeDownloadPolicy {

    private static final String KEY = "anime_offline_consent";
    private static volatile Boolean override;

    private AnimeDownloadPolicy() {
    }

    public static boolean allowed(Context context) {
        if (context == null) return false;
        if (override != null) return override;
        return context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
    }

    public static void acknowledge(Context context) {
        if (context == null) return;
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, true).apply();
    }

    public static void revoke(Context context) {
        if (context == null) return;
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    /** Teks pemberitahuan sebelum unduhan pertama. */
    public static String notice() {
        return "Berkas video diambil dari alamat media yang diterbitkan halaman episode, "
                + "tanpa melewati login, DRM, CAPTCHA, atau URL bertanda tangan.\n\n"
                + "Dengan menyetujui, episode disimpan di penyimpanan khusus aplikasi untuk "
                + "ditonton sendiri di perangkat ini — bukan untuk disebarkan ulang. Unduhan "
                + "bisa dihapus kapan saja tanpa menghapus riwayat dan progres menonton.\n\n"
                + "Tidak semua server bisa diunduh. Hanya server yang menyediakan berkas media "
                + "langsung yang ditawarkan; sisanya tetap bisa ditonton lewat pemutar sumber.";
    }

    public static String blockedMessage() {
        return "Unduhan offline butuh persetujuan lebih dulu: izin tertulis dari sumber belum "
                + "dapat diverifikasi, jadi aplikasi tidak menyimpan video secara diam-diam. "
                + "Episode tetap bisa ditonton online.";
    }

    /** Hanya untuk pengujian. */
    static void unlockForTesting(Boolean value) {
        override = value;
    }
}