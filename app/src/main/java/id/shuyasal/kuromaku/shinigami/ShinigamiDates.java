package id.shuyasal.kuromaku.shinigami;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Source instants are UTC; presentation follows the current user timezone. */
public final class ShinigamiDates {
    private ShinigamiDates() {}

    public static long parse(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            return Instant.parse(value.trim()).toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public static String format(long millis) {
        if (millis <= 0L) return "Tanggal belum tersedia";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }

    /**
     * Label ringkas untuk kartu sempit. Nama bulan mengikuti bahasa perangkat
     * supaya tidak terpotong pada kolom kecil.
     */
    public static String formatShort(long millis) {
        if (millis <= 0L) return "";
        Locale locale = Locale.getDefault();
        if (!"in".equals(locale.getLanguage()) && !"id".equals(locale.getLanguage())) {
            locale = new Locale("id", "ID");
        }
        return DateTimeFormatter.ofPattern("d MMM yyyy", locale)
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }
}
