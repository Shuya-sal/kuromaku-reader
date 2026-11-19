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
 * Chapter terbaru pada daftar katalog Manga Server 3.
 *
 * <p>Kartu katalog Komiku memuat DUA baris chapter: "Awal: Chapter 1" lalu
 * "Terbaru: Chapter 10". Parser lama mengambil tautan pertama, sehingga yang
 * tampil di daftar selalu chapter satu — bukan chapter terbaru. Tes ini memakai
 * fragmen asli halaman katalog supaya urutan itu benar-benar diuji.
 */
public class KomikuCatalogChapterTest {

    private static String fixture(String name) throws Exception {
        try (InputStream stream = KomikuCatalogChapterTest.class.getResourceAsStream(
                "/komiku/" + name)) {
            assertNotNull("fixture " + name, stream);
            return new String(readAll(stream), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static KomikuSource source() throws Exception {
        final String html = fixture("api_page2_terbaru.html");
        return new KomikuSource(url -> html);
    }

    @Test public void setiapBarisKatalogPunyaChapterTerbaru() throws Exception {
        List<KomikuManga> page = source().catalogPage(2);
        assertEquals(10, page.size());
        for (KomikuManga manga : page) {
            assertFalse("chapter terbaru harus terisi untuk " + manga.title,
                    manga.latestChapter == null || manga.latestChapter.trim().isEmpty());
        }
    }

    @Test public void yangDiambilBarisTerbaruBukanBarisAwal() throws Exception {
        List<KomikuManga> page = source().catalogPage(2);
        assertEquals("Handsomely Monstrous", page.get(0).title);
        assertEquals("Chapter 10", page.get(0).latestChapter);

        // Kartu yang chapter awalnya berbeda nomor: "Awal: Chapter 01",
        // "Terbaru: Chapter 7". Yang benar adalah 7, bukan 01.
        KomikuManga aisai = cari(page, "Aisai no Ura Aka");
        assertEquals("Chapter 7", aisai.latestChapter);
    }

    @Test public void labelAwalDanTerbaruTidakIkutTampil() throws Exception {
        List<KomikuManga> page = source().catalogPage(2);
        for (KomikuManga manga : page) {
            String label = manga.latestChapter.toLowerCase(java.util.Locale.ROOT);
            assertFalse("label tidak boleh ikut: " + manga.latestChapter,
                    label.contains("awal") || label.contains("terbaru"));
        }
    }

    @Test public void barisDaftarMenampilkanChapterDanWaktu() throws Exception {
        List<KomikuManga> page = source().catalogPage(2);
        KomikuManga first = page.get(0);
        // Persis bentuk yang dipakai baris daftar: "Ch. N · waktu".
        String chapter = KomikuActivity.chapterShort(first.latestChapter);
        assertEquals("Ch. 10", chapter);
        assertFalse("waktu ikut tampil", first.updated.isEmpty());
    }

    private KomikuManga cari(List<KomikuManga> page, String judul) {
        for (KomikuManga manga : page) {
            if (judul.equals(manga.title)) return manga;
        }
        throw new AssertionError("judul tidak ada di halaman: " + judul);
    }
}
