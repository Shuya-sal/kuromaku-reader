package id.shuyasal.kuromaku.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Kunci chapter harus menyatukan bentuk penomoran yang setara dan menolak
 * menyamakan chapter yang berbeda jenis — inilah yang mencegah progres
 * "Prolog" dianggap sama dengan "Chapter 1".
 */
public class ChapterKeyTest {

    @Test public void bentukPenomoranSamaMenghasilkanKunciSama() {
        assertEquals("n:10", ChapterKey.of("Chapter 10"));
        assertEquals("n:10", ChapterKey.of("chapter 10"));
        assertEquals("n:10", ChapterKey.of("Ch. 10"));
        assertEquals("n:10", ChapterKey.of("Bab 10"));
        assertEquals("n:10", ChapterKey.of("10"));
    }

    @Test public void angkaDesimalDipertahankan() {
        assertEquals("n:1.5", ChapterKey.of("Chapter 1.5"));
        assertEquals("n:278.4", ChapterKey.of("Chapter 278.4"));
        assertFalse("1.5 dan 15 bukan chapter yang sama",
                ChapterKey.same("Chapter 1.5", "Chapter 15"));
    }

    @Test public void chapterKhususPunyaJenisSendiri() {
        assertEquals("prolog", ChapterKey.of("Prolog"));
        assertEquals("epilog", ChapterKey.of("Epilog"));
        assertEquals("extra", ChapterKey.of("Extra"));
        assertEquals("special", ChapterKey.of("Special"));

        assertFalse("Prolog bukan Chapter 1", ChapterKey.same("Prolog", "Chapter 1"));
        assertFalse("Chapter 0 bukan Chapter 1", ChapterKey.same("Chapter 0", "Chapter 1"));
        assertFalse("Extra bukan Chapter 1", ChapterKey.same("Extra", "Chapter 1"));
    }

    @Test public void labelTidakDikenalTidakDipaksaCocok() {
        assertEquals(ChapterKey.UNKNOWN, ChapterKey.of(""));
        assertEquals(ChapterKey.UNKNOWN, ChapterKey.of(null));
        assertEquals(ChapterKey.UNKNOWN, ChapterKey.of("Tanpa nomor"));
        assertFalse(ChapterKey.same("Tanpa nomor", "Tanpa nomor"));
    }

    @Test public void edisiBerbedaTidakDicampur() {
        String a = ChapterKey.of("Chapter 5 Part 1");
        String b = ChapterKey.of("Chapter 5 Part 2");
        assertFalse("dua part berbeda bukan chapter yang sama", a.equals(b));
        assertEquals(a, ChapterKey.of("Chapter 5 Part 1"));
    }

    @Test public void nomorNilaiUntukPerbandingan() {
        assertEquals(10.0, ChapterKey.numberValue("Chapter 10"), 0.001);
        assertEquals(1.5, ChapterKey.numberValue("Chapter 1.5"), 0.001);
        assertEquals(-1, ChapterKey.numberValue("Prolog"), 0.001);
    }

    @Test public void judulChapterIkutTidakMengubahKunci() {
        // Sumber 2 menulis "Chapter 10 - Kelahiran"; sumber 3 menulis "Chapter 10".
        assertEquals(ChapterKey.of("Chapter 10"), ChapterKey.of("Chapter 10 - Kelahiran"));
        assertTrue(ChapterKey.same("Chapter 10 - Kelahiran", "Chapter 10"));
    }

    @Test public void prologDanChapterNolTetapBerbeda() {
        assertFalse("Prolog bukan Chapter 0",
                ChapterKey.same("Prolog", "Chapter 0"));
    }
}
