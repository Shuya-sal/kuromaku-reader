package id.shuyasal.kuromaku.sync;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pencocokan judul antar sumber.
 *
 * <p>Contoh nyata dari instruksi: Komiku menampilkan <i>Magic Emperor</i> dengan
 * alternatif <i>Kaisar Sihir</i>, sementara sumber lain bisa menamainya
 * <i>Demonic Emperor</i>. Nama yang mirip saja bukan bukti — karena itu kelas
 * ini hanya memberi <b>saran</b> dengan tingkat keyakinan, dan hasil yang
 * ambigu tetap harus dikonfirmasi pengguna.
 */
public final class MangaMatcher {

    /** Saran pasangan: keyakinan 0..1 dan alasan yang bisa ditampilkan. */
    public static final class Suggestion {
        public final String candidateId;
        public final String candidateTitle;
        public final double confidence;
        public final String reason;

        Suggestion(String candidateId, String candidateTitle, double confidence, String reason) {
            this.candidateId = candidateId;
            this.candidateTitle = candidateTitle;
            this.confidence = confidence;
            this.reason = reason;
        }

        /** Cukup yakin untuk ditawarkan sebagai pasangan utama. */
        public boolean strong() {
            return confidence >= 0.85;
        }

        /** Terlalu lemah untuk ditautkan tanpa konfirmasi manusia. */
        public boolean weak() {
            return confidence < 0.55;
        }
    }

    /** Satu calon judul dari sumber lain. */
    public static final class Candidate {
        public final String id;
        public final String title;
        public final String alternative;

        public Candidate(String id, String title, String alternative) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.alternative = alternative == null ? "" : alternative;
        }
    }

    private MangaMatcher() {
    }

    /**
     * Normalkan judul: huruf kecil, tanpa tanda baca, tanpa kata sambung
     * bahasa Inggris/Indonesia yang tidak membedakan karya.
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^a-z0-9\\s]", " ");
        Set<String> keep = new LinkedHashSet<>();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (word.equals("the") || word.equals("a") || word.equals("an")
                    || word.equals("of") || word.equals("and") || word.equals("no")
                    || word.equals("wa") || word.equals("ga") || word.equals("de")
                    || word.equals("to") || word.equals("in")) {
                continue;
            }
            keep.add(word);
        }
        return String.join(" ", keep);
    }

    /** Pecah daftar judul alternatif yang dipisah koma, "|", atau "/". */
    public static List<String> splitAlternatives(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split("[,|/;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /**
     * Saran pasangan untuk satu judul, urut dari paling yakin.
     *
     * <p>Aturan penilaian:
     * <ul>
     *   <li>judul sama persis setelah normalisasi → 0.95</li>
     *   <li>judul alternatif sama persis → 0.90</li>
     *   <li>satu judul memuat judul lain secara utuh → 0.70</li>
     *   <li>kemiripan kata tinggi tetapi bukan sama → paling tinggi 0.60</li>
     * </ul>
     * Tidak ada hasil yang otomatis dianggap pasti; pemanggil tetap
     * memutuskan berdasarkan {@link Suggestion#strong()}.
     */
    public static List<Suggestion> suggest(String title, String alternative,
                                           List<Candidate> candidates) {
        List<Suggestion> out = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return out;

        Set<String> mine = new LinkedHashSet<>();
        String base = normalize(title);
        if (!base.isEmpty()) mine.add(base);
        for (String alt : splitAlternatives(alternative)) {
            String norm = normalize(alt);
            if (!norm.isEmpty()) mine.add(norm);
        }
        if (mine.isEmpty()) return out;

        for (Candidate candidate : candidates) {
            if (candidate == null) continue;
            String candidateTitle = normalize(candidate.title);
            String candidateAlt = normalize(candidate.alternative);

            double best = 0;
            String reason = "";

            for (String own : mine) {
                if (!candidateTitle.isEmpty() && own.equals(candidateTitle)) {
                    if (best < 0.95) {
                        best = 0.95;
                        reason = "judul sama";
                    }
                }
                if (!candidateAlt.isEmpty() && own.equals(candidateAlt)) {
                    if (best < 0.90) {
                        best = 0.90;
                        reason = "judul alternatif sama";
                    }
                }
                if (!candidateTitle.isEmpty() && !own.isEmpty()
                        && (own.contains(candidateTitle) || candidateTitle.contains(own))
                        && Math.min(own.length(), candidateTitle.length()) >= 8) {
                    if (best < 0.70) {
                        best = 0.70;
                        reason = "salah satu judul memuat judul lain";
                    }
                }
                double overlap = wordOverlap(own, candidateTitle);
                if (overlap >= 0.75 && best < 0.60) {
                    best = 0.60;
                    reason = "sebagian besar kata sama";
                }
            }

            if (best > 0) {
                out.add(new Suggestion(candidate.id, candidate.title, best, reason));
            }
        }
        out.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        return out;
    }

    /** Perbandingan kata: irisan / kata terbanyak, 0..1. */
    static double wordOverlap(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        String[] left = a.split("\\s+");
        String[] right = b.split("\\s+");
        Set<String> setRight = new LinkedHashSet<>(java.util.Arrays.asList(right));
        int shared = 0;
        for (String word : left) if (setRight.contains(word)) shared++;
        return shared / (double) Math.max(left.length, right.length);
    }
}