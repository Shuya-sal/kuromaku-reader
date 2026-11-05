package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Tingkat kualitas gambar chapter Shinigami.
 *
 * <p>Sumber hanya menyajikan satu ukuran: lebar 800 px, tinggi sampai 10.000 px
 * (tidak ada varian HD/low — {@code base_url_low} menunjuk host yang sama dan
 * parameter {@code ?w=} diabaikan). Karena itu "HD / Kualitas Asli" berarti
 * <b>lebar native sumber</b>, bukan upscaling: gambar tidak pernah diperbesar
 * melebihi piksel yang benar-benar dikirim server.
 *
 * <p>Yang membedakan mode adalah lebar target dan format piksel, bukan
 * penambahan detail yang tidak ada:
 * <ul>
 *   <li>{@link #SAVER} — maksimum 720 px, RGB_565, potongan lebih pendek:
 *       paling hemat RAM dan kuota.</li>
 *   <li>{@link #AUTO} — selebar layar tetapi tidak melebihi sumber, RGB_565
 *       hanya untuk halaman sangat panjang.</li>
 *   <li>{@link #HD} — lebar native sumber, ARGB_8888: gradasi warna utuh untuk
 *       line art dan teks kecil.</li>
 * </ul>
 */
public enum ShinigamiQuality {
    AUTO("Otomatis"),
    SAVER("Hemat Data"),
    HD("HD / Kualitas Asli");

    private static final String KEY = "shinigami_quality";

    public final String label;

    ShinigamiQuality(String label) {
        this.label = label;
    }

    public static ShinigamiQuality stored(Context context) {
        String value = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .getString(KEY, AUTO.name());
        try {
            return valueOf(value);
        } catch (RuntimeException ignored) {
            return AUTO;
        }
    }

    public void save(Context context) {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY, name()).apply();
    }

    /** Urutan tombol: Otomatis → Hemat Data → HD → Otomatis. */
    public ShinigamiQuality next() {
        ShinigamiQuality[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Lebar decode yang dipakai. Tidak pernah melebihi lebar sumber, sehingga
     * aplikasi tidak pernah "mengarang" resolusi yang tidak dikirim server.
     */
    public int targetWidth(int screenWidth, int sourceWidth) {
        int screen = Math.max(1, screenWidth);
        int source = Math.max(1, sourceWidth);
        switch (this) {
            case SAVER:
                return Math.max(1, Math.min(Math.min(screen, 720), source));
            case HD:
                return source;
            default:
                return Math.min(screen, source);
        }
    }

    /** Tinggi satu potongan (dalam piksel sumber) saat merender bertahap. */
    public int tileHeight() {
        switch (this) {
            case SAVER: return 640;
            case HD: return 1600;
            default: return 1280;
        }
    }

    /**
     * Format piksel. Halaman sangat panjang memakai RGB_565 pada mode otomatis
     * agar beberapa potongan tetap muat di RAM; mode HD selalu ARGB_8888.
     */
    public boolean useRgb565(int sourceWidth, int sourceHeight) {
        if (this == SAVER) return true;
        if (this == HD) return false;
        long pixels = (long) sourceWidth * (long) sourceHeight;
        return pixels > 6_000_000L;
    }

    /** Ringkasan untuk dialog pengaturan, tanpa klaim palsu soal resolusi. */
    public String summary(int sourceWidth) {
        switch (this) {
            case SAVER:
                return "Maksimal 720 px, warna 16-bit. Paling hemat RAM dan kuota.";
            case HD:
                return "Lebar asli sumber (" + sourceWidth + " px), warna 32-bit. Paling tajam; "
                        + "halaman panjang dimuat bertahap supaya tetap aman di memori.";
            default:
                return "Selebar layar tanpa melebihi sumber (" + sourceWidth + " px). Seimbang "
                        + "antara ketajaman dan memori.";
        }
    }
}
