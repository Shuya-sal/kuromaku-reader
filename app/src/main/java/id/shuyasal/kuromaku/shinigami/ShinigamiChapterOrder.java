package id.shuyasal.kuromaku.shinigami;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Urutan cerita chapter: satu-satunya sumber kebenaran untuk "sebelumnya" dan
 * "berikutnya".
 *
 * <p>Urutan daftar di layar (Terbaru/Terlama) sengaja tidak dipakai: panah di
 * reader harus selalu bergerak menurut alur cerita, bukan menurut tampilan.
 * Perbandingan memakai nomor chapter (termasuk desimal seperti 10.5), lalu
 * judul untuk memisahkan Prolog/Epilog yang nomornya bisa sama.
 */
public final class ShinigamiChapterOrder {

    private ShinigamiChapterOrder() {}

    /** Terlama → terbaru: Prolog, Chapter 0, 1, 1.5, 2, … Epilog. */
    public static final Comparator<ShinigamiChapter> ASC = (a, b) -> {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int cmp = Double.compare(a.number, b.number);
        if (cmp != 0) return cmp;
        String at = a.title == null ? "" : a.title.toLowerCase(Locale.ROOT);
        String bt = b.title == null ? "" : b.title.toLowerCase(Locale.ROOT);
        if (at.contains("prolog") && !bt.contains("prolog")) return -1;
        if (!at.contains("prolog") && bt.contains("prolog")) return 1;
        if (at.contains("epilog") && !bt.contains("epilog")) return 1;
        if (!at.contains("epilog") && bt.contains("epilog")) return -1;
        return a.id.compareTo(b.id);
    };

    public static final Comparator<ShinigamiChapter> DESC = (a, b) -> ASC.compare(b, a);

    /** Indeks chapter ini pada daftar, atau -1 bila tidak ada. */
    public static int indexOf(List<ShinigamiChapter> ordered, String chapterId) {
        if (ordered == null || chapterId == null || chapterId.isEmpty()) return -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (chapterId.equals(ordered.get(index).id)) return index;
        }
        return -1;
    }

    /**
     * Chapter tepat sebelum {@code current} pada urutan cerita, atau null.
     *
     * <p>Bila chapter aktif tidak ada di daftar (misalnya sumber menambah
     * chapter baru), posisinya dicari lewat nomor chapter supaya panah tetap
     * menunjuk chapter yang benar-benar berdekatan.
     */
    public static ShinigamiChapter previous(List<ShinigamiChapter> ordered, ShinigamiChapter current) {
        int index = current == null ? -1 : indexOf(ordered, current.id);
        if (index > 0) return ordered.get(index - 1);
        if (index == 0) return null;
        return neighbourByNumber(ordered, current, false);
    }

    public static ShinigamiChapter next(List<ShinigamiChapter> ordered, ShinigamiChapter current) {
        int index = current == null ? -1 : indexOf(ordered, current.id);
        if (index >= 0 && index + 1 < ordered.size()) return ordered.get(index + 1);
        if (index >= 0) return null;
        return neighbourByNumber(ordered, current, true);
    }

    private static ShinigamiChapter neighbourByNumber(List<ShinigamiChapter> ordered,
                                                      ShinigamiChapter current, boolean forward) {
        if (ordered == null || ordered.isEmpty() || current == null) return null;
        ShinigamiChapter best = null;
        for (ShinigamiChapter candidate : ordered) {
            if (current.id.equals(candidate.id)) continue;
            if (forward) {
                if (ASC.compare(candidate, current) <= 0) continue;
                if (best == null || ASC.compare(candidate, best) < 0) best = candidate;
            } else {
                if (ASC.compare(candidate, current) >= 0) continue;
                if (best == null || ASC.compare(candidate, best) > 0) best = candidate;
            }
        }
        return best;
    }
}
