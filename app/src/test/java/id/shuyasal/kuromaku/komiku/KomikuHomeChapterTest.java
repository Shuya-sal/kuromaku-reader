package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Chapter terbaru pada kartu beranda Manga Server 3.
 *
 * <p>Kartu "Terbaru" menaruh tautan chapter pada kelas {@code ls2l}; kelas
 * {@code ls24} hanya dipakai blok populer. Parser lama hanya mencari
 * {@code ls24}, sehingga daftar "Chapter Terbaru" tampil tanpa chapter sama
 * sekali — hanya judul dan waktu. Tes ini memakai halaman beranda asli dari
 * situs sebagai bukti.
 */
public class KomikuHomeChapterTest {

    private static String fixture() throws Exception {
        try (InputStream stream = KomikuHomeChapterTest.class
                .getResourceAsStream("/komiku/home.html")) {
            assertNotNull("fixture beranda", stream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static KomikuSource source() throws Exception {
        final String home = fixture();
        return new KomikuSource(url -> home);
    }

    @Test public void umpanTerbaruMemuatJudul() throws Exception {
        List<KomikuManga> items = source().latest();
        assertFalse("umpan terbaru tidak boleh kosong", items.isEmpty());
    }

    /** Inti keluhan: baris daftar hanya judul dan waktu, tanpa chapter. */
    @Test public void setiapBarisUmpanTerbaruPunyaChapter() throws Exception {
        List<KomikuManga> items = source().latest();
        assertTrue("ada beberapa judul", items.size() >= 3);
        for (KomikuManga manga : items) {
            assertFalse("chapter terbaru harus terisi untuk " + manga.title,
                    manga.latestChapter == null || manga.latestChapter.trim().isEmpty());
            assertTrue("harus berbentuk Chapter N, dapat: " + manga.latestChapter,
                    manga.latestChapter.toLowerCase(java.util.Locale.ROOT)
                            .contains("chapter"));
        }
    }

    @Test public void labelChapterBisaDipendekkanUntukBarisDaftar() throws Exception {
        List<KomikuManga> items = source().latest();
        boolean ada = false;
        for (KomikuManga manga : items) {
            String pendek = KomikuActivity.chapterShort(manga.latestChapter);
            assertFalse("label pendek tidak kosong untuk " + manga.title, pendek.isEmpty());
            if (pendek.startsWith("Ch. ")) ada = true;
            // "Chapter 130" -> "Ch. 130": angka tidak boleh hilang.
            if (manga.latestChapter.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("chapter")) {
                assertEquals("angka chapter dipertahankan",
                        manga.latestChapter.substring("chapter".length()).trim(),
                        pendek.substring("Ch.".length()).trim());
            }
        }
        assertTrue("label memakai bentuk pendek Ch. N", ada);
    }

    @Test public void waktuUpdateTetapTerbaca() throws Exception {
        List<KomikuManga> items = source().latest();
        boolean adaWaktu = false;
        for (KomikuManga manga : items) {
            if (!manga.updated.isEmpty()) adaWaktu = true;
        }
        assertTrue("waktu update ikut tampil di baris daftar", adaWaktu);
    }

    /**
     * Blok populer tetap terbaca chapter-nya lewat {@code ls24}; perbaikan ini
     * tidak boleh merusak jalur lama.
     */
    @Test public void blokPopulerTetapTerbaca() throws Exception {
        List<KomikuManga> popular = source().popular();
        assertFalse(popular.isEmpty());
        for (KomikuManga manga : popular) {
            assertFalse("chapter populer harus terisi untuk " + manga.title,
                    manga.latestChapter == null || manga.latestChapter.trim().isEmpty());
        }
    }
}
