package id.shuyasal.kuromaku.sync;

import java.util.Locale;

/**
 * Kunci chapter kanonis.
 *
 * <p>Nomor chapter saja tidak cukup: satu sumber bisa punya "Prolog",
 * "Chapter 0", "Chapter 1.5", "Extra", atau "Epilog" yang tidak punya
 * padanan di sumber lain. Kunci di sini menyatukan bentuk penomoran
 * menjadi satu identitas yang bisa dibandingkan, dan menolak menyamakan
 * dua chapter yang hanya kebetulan bernomor sama tetapi berbeda jenis.
 */
public final class ChapterKey {

    public static final String UNKNOWN = "";

    private ChapterKey() {
    }

    /**
     * Kunci stabil dari label chapter, mis. "Chapter 10" → {@code "n:10"},
     * "Prolog" → {@code "prolog"}, "Chapter 1.5" → {@code "n:1.5"}.
     *
     * <p>Mengembalikan {@link #UNKNOWN} bila label tidak bisa dipahami —
     * lebih baik tidak tersinkron daripada salah menyambung chapter.
     */
    public static String of(String label) {
        if (label == null) return UNKNOWN;
        String text = label.toLowerCase(Locale.ROOT).trim();
        if (text.isEmpty()) return UNKNOWN;

        // Buang awalan merek yang tidak membedakan isi.
        text = text.replace("chapter", " ")
                .replace("ch.", " ")
                .replace("ch ", " ")
                .replace("bab", " ")
                .replace("episode", " ")
                .trim();

        String kind = "";
        if (text.contains("prolog") || text.contains("prologue")) kind = "prolog";
        else if (text.contains("epilog") || text.contains("epilogue")) kind = "epilog";
        else if (text.contains("extra") || text.contains("bonus")) kind = "extra";
        else if (text.contains("special")) kind = "special";

        String number = number(text);
        String part = part(text);

        if (number.isEmpty() && kind.isEmpty()) return UNKNOWN;
        if (number.isEmpty()) return part.isEmpty() ? kind : kind + ":" + part;
        if (!kind.isEmpty()) return kind + ":" + number + (part.isEmpty() ? "" : ":" + part);
        return "n:" + number + (part.isEmpty() ? "" : ":" + part);
    }

    /** Angka pertama pada teks (boleh desimal), atau kosong. */
    static String number(String text) {
        int i = 0;
        while (i < text.length() && !Character.isDigit(text.charAt(i))) i++;
        if (i >= text.length()) return "";
        int start = i;
        while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
            i++;
        }
        String raw = text.substring(start, i);
        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == '.') end--;
        if (end == 0) return "";
        return raw.substring(0, end);
    }

    /** Penanda "part N" / "edisi N" bila ada, supaya dua edisi tidak tertukar. */
    static String part(String text) {
        int index = text.indexOf("part");
        if (index < 0) index = text.indexOf("edisi");
        if (index < 0) return "";
        String tail = text.substring(index);
        String value = number(tail);
        return value;
    }

    /** Angka chapter sebagai double, atau -1 bila tidak ada. */
    public static double numberValue(String label) {
        String raw = number(label == null ? "" : label.toLowerCase(Locale.ROOT));
        if (raw.isEmpty()) return -1;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /** Benar bila dua label menunjuk chapter yang sama menurut kunci. */
    public static boolean same(String a, String b) {
        String keyA = of(a);
        String keyB = of(b);
        return !keyA.isEmpty() && keyA.equals(keyB);
    }

    /**
     * Label yang bisa dibaca manusia dari kunci kanonis.
     *
     * <p>{@code n:10} menjadi "Chapter 10", {@code prolog} menjadi "Prolog",
     * {@code extra:2} menjadi "Extra 2". Kunci yang tidak dikenali
     * dikembalikan apa adanya supaya tidak ada label karangan.
     */
    public static String displayLabel(String key) {
        if (key == null || key.isEmpty()) return "";
        if (key.startsWith("n:")) {
            return "Chapter " + key.substring(2).replace(":", " Part ");
        }
        if (key.startsWith("extra")) {
            String tail = key.length() > 6 ? key.substring(6) : "";
            return tail.isEmpty() || ":".equals(tail) ? "Extra" : "Extra " + tail.replace(":", "");
        }
        if (key.startsWith("special")) {
            String tail = key.length() > 8 ? key.substring(8) : "";
            return tail.isEmpty() ? "Special" : "Special " + tail.replace(":", "");
        }
        if (key.equals("prolog")) return "Prolog";
        if (key.equals("epilog")) return "Epilog";
        return key;
    }
}
