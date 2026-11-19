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
     * Waktu relatif untuk kartu sempit: "3 jam", "5 hr", "2 mgg", "3 bln".
     * Di atas satu tahun memakai tahun. Nilai masa depan diperlakukan sebagai
     * "baru" supaya jam perangkat yang bergeser tidak menghasilkan teks aneh.
     */
    public static String relative(long millis, long now) {
        if (millis <= 0L) return "";
        long delta = now - millis;
        if (delta < 0L) delta = 0L;
        long minutes = delta / 60000L;
        if (minutes < 1L) return "baru";
        if (minutes < 60L) return minutes + " mnt";
        long hours = minutes / 60L;
        if (hours < 24L) return hours + " jam";
        long days = hours / 24L;
        if (days < 7L) return days + " hr";
        long weeks = days / 7L;
        if (weeks < 5L) return weeks + " mgg";
        long months = days / 30L;
        if (months < 12L) return months + " bln";
        return (days / 365L) + " thn";
    }

    /** Label kartu: relatif bila masih segar, tanggal ringkas bila sudah lama. */
    public static String updatedLabel(long millis, long now) {
        if (millis <= 0L) return "";
        long days = Math.max(0L, now - millis) / 86400000L;
        return days <= 45L ? relative(millis, now) : formatShort(millis);
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
