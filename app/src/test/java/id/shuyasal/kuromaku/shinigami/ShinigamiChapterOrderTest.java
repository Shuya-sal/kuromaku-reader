package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Urutan cerita untuk panah chapter: Prolog, Chapter 0, 1, 1.5, 2, … Epilog.
 * Urutan tampilan daftar (Terbaru/Terlama) tidak boleh mempengaruhinya.
 */
public class ShinigamiChapterOrderTest {

    private static ShinigamiChapter chapter(double number, String title) {
        return new ShinigamiChapter("id-" + number + "-" + title, "m", number, title, "", "");
    }

    private static List<ShinigamiChapter> story() {
        List<ShinigamiChapter> list = new ArrayList<>(Arrays.asList(
                chapter(3, ""), chapter(0, ""), chapter(1, ""), chapter(10.5, ""),
                chapter(2, ""), chapter(1.5, ""), chapter(1, "Prolog"),
                chapter(60, "Epilog")));
        java.util.Collections.sort(list, ShinigamiChapterOrder.ASC);
        return list;
    }

    @Test public void urutanMemakaiNomorBukanAlfabet() {
        List<ShinigamiChapter> ordered = story();
        List<Double> numbers = new ArrayList<>();
        for (ShinigamiChapter item : ordered) numbers.add(item.number);
        assertEquals(Arrays.asList(0d, 1d, 1d, 1.5d, 2d, 3d, 10.5d, 60d), numbers);
    }

    @Test public void prologDidahulukanSaatNomorSama() {
        List<ShinigamiChapter> ordered = story();
        assertEquals("Prolog", ordered.get(1).title);
        assertEquals("", ordered.get(2).title);
    }

    @Test public void desimalTidakSalahTempat() {
        List<ShinigamiChapter> ordered = story();
        int indexOfTwo = ShinigamiChapterOrder.indexOf(ordered, "id-2.0-");
        int indexOfDecimal = ShinigamiChapterOrder.indexOf(ordered, "id-1.5-");
        assertEquals("1.5 harus tepat sebelum 2", indexOfTwo - 1, indexOfDecimal);
    }

    @Test public void sebelumnyaDanBerikutnyaMengikutiUrutanCerita() {
        List<ShinigamiChapter> ordered = story();
        ShinigamiChapter two = ordered.get(ShinigamiChapterOrder.indexOf(ordered, "id-2.0-"));
        assertEquals("id-1.5-", ShinigamiChapterOrder.previous(ordered, two).id);
        assertEquals("id-3.0-", ShinigamiChapterOrder.next(ordered, two).id);
    }

    @Test public void chapterPertamaDanTerakhirTidakPunyaTetangga() {
        List<ShinigamiChapter> ordered = story();
        assertNull(ShinigamiChapterOrder.previous(ordered, ordered.get(0)));
        assertNull(ShinigamiChapterOrder.next(ordered, ordered.get(ordered.size() - 1)));
    }

    @Test public void chapterYangBelumAdaDiDaftarTetapPunyaTetangga() {
        List<ShinigamiChapter> ordered = story();
        // Chapter baru dari sumber, nomor 1.7, belum ada di daftar.
        ShinigamiChapter fresh = chapter(1.7, "");
        assertEquals("id-1.5-", ShinigamiChapterOrder.previous(ordered, fresh).id);
        assertEquals("id-2.0-", ShinigamiChapterOrder.next(ordered, fresh).id);
    }

    @Test public void urutanTerbalikTetapMemakaiPatokanYangSama() {
        List<ShinigamiChapter> ordered = story();
        List<ShinigamiChapter> reversed = new ArrayList<>(ordered);
        java.util.Collections.sort(reversed, ShinigamiChapterOrder.DESC);
        ShinigamiChapter two = ordered.get(ShinigamiChapterOrder.indexOf(ordered, "id-2.0-"));
        // Panah memakai urutan cerita, bukan urutan daftar yang sedang tampil.
        assertEquals("id-3.0-", ShinigamiChapterOrder.next(ordered, two).id);
        assertTrue("daftar tampil terbalik", ShinigamiChapterOrder.DESC.compare(
                reversed.get(0), reversed.get(reversed.size() - 1)) < 0);
    }
}