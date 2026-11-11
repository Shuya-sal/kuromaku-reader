package id.shuyasal.kuromaku.ui;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.content.res.Configuration;
import android.os.Build;
import android.view.InputDevice;

import java.util.Locale;

/**
 * Deteksi perangkat televisi.
 *
 * <p>Aplikasi ini dirancang untuk ponsel: seluruh kendali memakai sentuhan dan
 * susunan halaman dibuat untuk layar tegak. Di televisi tidak ada sentuhan sama
 * sekali, jadi kendali harus pindah ke tombol arah remote dan tata letak harus
 * muat di layar lebar pada jarak tonton jauh.
 *
 * <p>Kelas ini yang memutuskan kapan mode televisi dipakai. Keputusannya
 * sengaja mengandung dua jalur:
 *
 * <ul>
 *   <li>{@code UiModeManager} — jalur resmi. Perangkat yang benar-benar
 *       dinyatakan sebagai televisi selalu masuk jalur ini.</li>
 *   <li>Pemeriksaan tambahan — sebagian box Android TV murah dan proyektor
 *       pintar melaporkan dirinya sebagai perangkat biasa (tidak punya kendali
 *       sentuh, layar lanskap besar). Tanpa jalur ini, aplikasi akan menyangka
 *       perangkat itu ponsel dan memasang kendali sentuh yang tidak bisa
 *       dipakai.</li>
 * </ul>
 *
 * <p>Hasilnya disimpan agar konsisten selama satu sesi, dan dapat dipaksa lewat
 * preferensi untuk pengujian.
 */
public final class TvMode {

    /** Preferensi: paksa mode televisi (1) atau paksa mode sentuh (0). */
    public static final String PREF_FORCE = "tv_mode_force";
    /** Preferensi: perangkat ini sudah pernah terdeteksi sebagai televisi. */
    public static final String PREF_DETECTED = "tv_mode_detected";

    private TvMode() {
    }

    /**
     * Apakah aplikasi harus berjalan dalam mode televisi.
     *
     * <p>Dipanggil berkali-kali, jadi tidak boleh menyentuh sistem secara berat
     * setiap kali: hasilnya diambil dari preferensi bila sudah pernah dihitung.
     */
    public static boolean isTelevision(Context context) {
        if (context == null) return false;
        Prefs prefs = new Prefs(context);
        int force = prefs.getInt(PREF_FORCE, -1);
        if (force == 0) return false;
        if (force == 1) return true;
        boolean detected = prefs.getBoolean(PREF_DETECTED, false) || detect(context);
        if (detected && !prefs.getBoolean(PREF_DETECTED, false)) {
            prefs.setBoolean(PREF_DETECTED, true);
        }
        return detected;
    }

    /** Hitung ulang dari sistem, tanpa memakai hasil yang tersimpan. */
    public static boolean detect(Context context) {
        if (context == null) return false;
        if (declaresTelevision(context)) return true;
        // Layar lebar tanpa kendali sentuh: box TV, proyektor, atau layar
        // eksternal. Ponsel yang diputar ke lanskap tetap punya sentuhan.
        return !hasTouchscreen(context) && isWideScreen(context) && !isHandheld(context);
    }

    /** Jalur resmi: perangkat menyatakan dirinya televisi. */
    private static boolean declaresTelevision(Context context) {
        try {
            UiModeManager manager = (UiModeManager) context.getSystemService(
                    Context.UI_MODE_SERVICE);
            if (manager == null) return false;
            int mode = manager.getCurrentModeType();
            if (mode == Configuration.UI_MODE_TYPE_TELEVISION) return true;
            // Sebagian perangkat melaporkan mode "appliance" atau "desk".
            return mode == Configuration.UI_MODE_TYPE_APPLIANCE;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Ada layar sentuh yang bisa dipakai untuk mengetuk? */
    public static boolean hasTouchscreen(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            if (manager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return true;
            if (manager.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH)) return true;
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static boolean isWideScreen(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int widthDp = config.screenWidthDp;
        if (widthDp <= 0) {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            widthDp = Math.round(metrics.widthPixels / Math.max(1f, metrics.density));
        }
        return widthDp >= 600 && config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Perangkat genggam? Ponsel dan tablet selalu punya sentuhan, jadi ini
     * mencegah perangkat genggam berlayar lebar ikut dianggap televisi.
     */
    private static boolean isHandheld(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            return manager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    /** Nama perangkat, dipakai untuk keterangan mode di pengaturan. */
    public static String deviceLabel() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String label = (maker + " " + model).trim();
        return label.isEmpty() ? "Perangkat tidak dikenal" : label;
    }

    /** Lebar layar dalam dp, untuk memilih tata letak. */
    public static int widthDp(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int widthDp = config.screenWidthDp;
        if (widthDp > 0) return widthDp;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(metrics.widthPixels / Math.max(1f, metrics.density));
    }

    /**
     * Jumlah kolom yang nyaman untuk televisi.
     *
     * <p>Di layar televisi, kartu kecil sulit dipilih karena jaraknya jauh dan
     * sorotan harus terlihat jelas. Kartu dibuat lebih besar, jadi jumlah kolom
     * lebih sedikit daripada di ponsel.
     */
    public static int gridColumns(Context context) {
        int widthDp = widthDp(context);
        if (isTelevision(context)) {
            // Yang dijaga adalah LEBAR KARTU, bukan jumlah kolom. Dengan
            // pembagi ponsel (150 dp), televisi 960 dp menghasilkan 6 kolom —
            // kartu justru lebih kecil daripada di ponsel, padahal dilihat dari
            // sofa. Pembagi 260 dp memberi kartu sekitar 240 dp: lebih besar
            // dari ponsel, dan sorotan mudah diikuti.
            return Math.max(3, Math.min(8, Math.round(widthDp / 260f)));
        }
        return Math.max(2, Math.min(4, widthDp / 150));
    }

    /** Ukuran huruf diskalakan sedikit pada televisi agar terbaca dari jauh. */
    public static float fontScale(Context context) {
        return isTelevision(context) ? 1.15f : 1f;
    }

    /** Lebar konten maksimum supaya baris teks tidak terlalu panjang. */
    public static int maxContentWidthDp(Context context) {
        return isTelevision(context) ? 1080 : 0;
    }

    /** Keterangan singkat untuk pengaturan. */
    public static String describe(Context context) {
        if (!isTelevision(context)) return "Ponsel atau tablet (sentuh)";
        return "Televisi (" + deviceLabel() + ")";
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
