package id.shuyasal.kuromaku.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pencocokan judul tidak boleh menggabungkan karya berbeda hanya karena
 * namanya mirip. Kasus nyata dari instruksi: Magic Emperor / Kaisar Sihir /
 * Demonic Emperor.
 */
public class MangaMatcherTest {

    private static MangaMatcher.Candidate candidate(String id, String title, String alt) {
        return new MangaMatcher.Candidate(id, title, alt);
    }

    @Test public void judulSamaPersisDianggapKuat() {
        List<MangaMatcher.Suggestion> found = MangaMatcher.suggest("Black Clover", "",
                Arrays.asList(candidate("k1", "Black Clover", "")));
        assertEquals(1, found.size());
        assertTrue("keyakinan tinggi", found.get(0).strong());
        assertEquals(0.95, found.get(0).confidence, 0.001);
    }

    @Test public void judulAlternatifSamaJugaKuat() {
        List<MangaMatcher.Suggestion> found = MangaMatcher.suggest("Kaisar Sihir", "",
                Arrays.asList(candidate("k1", "Magic Emperor", "Kaisar Sihir")));
        assertEquals(1, found.size());
        assertTrue("alternatif diakui", found.get(0).strong());
        assertEquals("judul alternatif sama", found.get(0).reason);
    }

    @Test public void judulMiripTetapiKaryaBerbedaTidakKuat() {
        // Magic Emperor dan Demonic Emperor berbagi kata "Emperor" saja.
        List<MangaMatcher.Suggestion> found = MangaMatcher.suggest("Magic Emperor", "Kaisar Sihir",
                Arrays.asList(candidate("k1", "Demonic Emperor", "")));
        for (MangaMatcher.Suggestion item : found) {
            assertFalse("tidak boleh ditautkan otomatis: " + item.reason, item.strong());
        }
    }

    @Test public void judulYangMemuatJudulLainHanyaSedang() {
        List<MangaMatcher.Suggestion> found = MangaMatcher.suggest("Black Clover Gaiden", "",
                Arrays.asList(candidate("k1", "Black Clover", "")));
        assertEquals(1, found.size());
        assertFalse("butuh konfirmasi pengguna", found.get(0).strong());
        assertTrue("masih ditawarkan", found.get(0).confidence >= 0.55);
    }

    @Test public void kataSambungTidakMembedakan() {
        assertEquals(MangaMatcher.normalize("The Black Clover"),
                MangaMatcher.normalize("Black Clover"));
    }

    @Test public void tandaBacaDanHurufBesarDiabaikan() {
        assertEquals(MangaMatcher.normalize("Black-Clover!"),
                MangaMatcher.normalize("black clover"));
    }

    @Test public void alternatifDipisahDenganBenar() {
        List<String> parts = MangaMatcher.splitAlternatives("Kaisar Sihir | Magic Emperor, Mo Huang");
        assertEquals(3, parts.size());
        assertTrue(parts.contains("Kaisar Sihir"));
        assertTrue(parts.contains("Magic Emperor"));
    }

    @Test public void tanpaCalonTidakAdaSaran() {
        List<MangaMatcher.Suggestion> found = MangaMatcher.suggest("Apa Saja", "",
                new ArrayList<>());
        assertTrue(found.isEmpty());
    }

    @Test public void urutDariPalingYakin() {
        List<MangaMatcher.Suggestion> found = MangaMatcher.suggest("Black Clover", "",
                Arrays.asList(candidate("k1", "Black Clover Gaiden", ""),
                        candidate("k2", "Black Clover", "")));
        assertEquals(2, found.size());
        assertEquals("k2", found.get(0).candidateId);
        assertTrue(found.get(0).confidence > found.get(1).confidence);
    }
}