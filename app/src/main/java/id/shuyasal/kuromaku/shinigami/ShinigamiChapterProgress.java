package id.shuyasal.kuromaku.shinigami;

/**
 * Label progres MEMBACA chapter. Satu-satunya tempat yang mengubah angka
 * baca menjadi teks, supaya makna {@code 12%} tidak pernah tertukar dengan
 * progres unduhan.
 */
public final class ShinigamiChapterProgress {
    private ShinigamiChapterProgress() {
    }

    public static String readLabel(int percent) {
        if (percent >= 100) return "Selesai";
        if (percent > 0) return "Dibaca " + percent + "%";
        return "";
    }
}
