package id.shuyasal.kuromaku.komiku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Satu judul komik dari daftar Komiku.
 *
 * <p>{@link #id} adalah slug dari alamat detail — stabil dan tidak bergantung
 * pada urutan tampil, sehingga riwayat dan favorit tidak tertukar saat
 * katalog diurutkan ulang.
 */
public final class KomikuManga {

    public final String id;
    public final String title;
    public final String url;
    public final String coverUrl;
    public final String type;
    public final String latestChapter;
    public final String updated;

    public KomikuManga(String id, String title, String url, String coverUrl,
                       String type, String latestChapter, String updated) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title.trim();
        this.url = url == null ? "" : url;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.type = type == null ? "" : type.trim();
        this.latestChapter = latestChapter == null ? "" : latestChapter.trim();
        this.updated = updated == null ? "" : updated.trim();
    }

    /** Baris kecil di bawah judul: "Manhwa · 8 menit lalu". */
    public String subtitle() {
        List<String> parts = new ArrayList<>();
        if (!type.isEmpty()) parts.add(type);
        if (!updated.isEmpty()) parts.add(updated);
        if (parts.isEmpty() && !latestChapter.isEmpty()) parts.add(latestChapter);
        return join(parts);
    }

    static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(part);
        }
        return out.toString();
    }

    /**
     * Nomor chapter dari teks label, mis. "Chapter 393" → 393, "Chapter 278.4" → 278.4.
     * Mengembalikan -1 bila tidak ada angka.
     */
    public static double chapterNumber(String label) {
        if (label == null) return -1;
        String text = label.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < text.length() && !Character.isDigit(text.charAt(i))) i++;
        if (i >= text.length()) return -1;
        int start = i;
        while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) i++;
        String number = text.substring(start, i);
        int end = number.length();
        while (end > 0 && number.charAt(end - 1) == '.') end--;
        if (end == 0) return -1;
        try {
            return Double.parseDouble(number.substring(0, end));
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    static List<KomikuManga> emptyList() {
        return Collections.emptyList();
    }
}