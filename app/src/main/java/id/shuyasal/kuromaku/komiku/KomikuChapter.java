package id.shuyasal.kuromaku.komiku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Satu chapter Komiku: alamat, label, dan tanggal rilis bila ada. */
public final class KomikuChapter {

    public final String url;
    public final String title;
    public final String date;

    public KomikuChapter(String url, String title, String date) {
        this.url = url == null ? "" : url;
        this.title = title == null ? "" : title.trim();
        this.date = date == null ? "" : date.trim();
    }

    /** Nomor chapter dari label; -1 bila label tidak memuat angka. */
    public double number() {
        return KomikuManga.chapterNumber(title);
    }

    /** Kunci stabil: alamat chapter, bukan indeks di daftar. */
    public String key() {
        return url;
    }

    /** Urutkan menurun (terbaru dulu) berdasarkan nomor chapter. */
    public static List<KomikuChapter> newestFirst(List<KomikuChapter> chapters) {
        List<KomikuChapter> copy = new ArrayList<>(chapters);
        Collections.sort(copy, (a, b) -> Double.compare(b.number(), a.number()));
        return copy;
    }

    /** Urutkan menaik (terlama dulu). */
    public static List<KomikuChapter> oldestFirst(List<KomikuChapter> chapters) {
        List<KomikuChapter> copy = new ArrayList<>(chapters);
        Collections.sort(copy, (a, b) -> Double.compare(a.number(), b.number()));
        return copy;
    }
}