package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Parser Komiku diuji terhadap halaman nyata yang disimpan sebagai fixture.
 *
 * <p>Yang dijaga: daftar katalog benar-benar terisi, detail membawa metadata
 * dan chapter, dan alamat gambar chapter hanya berasal dari host aset Komiku —
 * tanpa banner promosi dan tanpa alamat karangan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KomikuSourceTest {

    private static KomikuSource source() {
        return new KomikuSource(url -> {
            if (url.contains("/manga/")) return fixture("detail.html");
            if (url.contains("chapter")) return fixture("chapter.html");
            return fixture("home.html");
        });
    }

    private static String fixture(String name) {
        try (java.io.InputStream in = KomikuSourceTest.class.getResourceAsStream(
                "/komiku/" + name)) {
            if (in == null) throw new IllegalStateException("fixture: " + name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @Test public void berandaMembawaDaftarTerbaru() throws Exception {
        List<KomikuManga> items = source().latest();
        assertFalse("daftar terbaru tidak boleh kosong", items.isEmpty());
        KomikuManga first = items.get(0);
        assertFalse("judul terbaca", first.title.isEmpty());
        assertTrue("alamat detail HTTPS", first.url.startsWith("https://komiku.org/manga/"));
        assertFalse("id stabil dari slug", first.id.isEmpty());
        assertTrue("cover tersedia", first.coverUrl.startsWith("https://"));
    }

    @Test public void peringkatDanBaruDitambahkanTerbaca() throws Exception {
        assertFalse(source().popular().isEmpty());
        assertFalse(source().newest().isEmpty());
    }

    @Test public void detailMembawaMetadataDanChapter() throws Exception {
        KomikuDetail detail = source().detail("https://komiku.org/manga/black-clover-indonesia/");

        assertEquals("Black Clover", detail.title);
        assertEquals("black-clover-indonesia", detail.id);
        assertFalse("status terbaca", detail.status.isEmpty());
        assertFalse("tipe terbaca", detail.type.isEmpty());
        assertTrue("sinopsis terbaca: [" + detail.synopsis + "]",
                detail.synopsis.length() > 40);
        assertFalse("genre terbaca", detail.genres.isEmpty());
        assertTrue("cover detail ada", detail.coverUrl.startsWith("https://"));

        assertTrue("chapter lebih dari seratus", detail.chapters.size() > 100);
        KomikuChapter first = detail.chapters.get(0);
        assertTrue("alamat chapter HTTPS", first.url.startsWith("https://komiku.org/"));
        assertTrue("nomor chapter terbaca", first.number() > 0);
    }

    @Test public void chapterTerbaruDiAtasDanBisaDibalik() throws Exception {
        KomikuDetail detail = source().detail("https://komiku.org/manga/black-clover-indonesia/");
        List<KomikuChapter> terbaru = KomikuChapter.newestFirst(detail.chapters);
        List<KomikuChapter> terlama = KomikuChapter.oldestFirst(detail.chapters);

        assertTrue("terbaru dulu", terbaru.get(0).number() >= terbaru.get(1).number());
        assertTrue("terlama dulu", terlama.get(0).number() <= terlama.get(1).number());
        assertEquals("jumlah sama setelah diurutkan",
                terbaru.size(), terlama.size());
    }

    @Test public void gambarChapterHanyaDariHostAset() throws Exception {
        List<String> images = source().images("https://komiku.org/black-clover-chapter-393/");

        assertTrue("gambar chapter terbaca", images.size() >= 10);
        for (String url : images) {
            assertTrue("hanya host aset Komiku: " + url,
                    url.startsWith("https://img.komiku.org/"));
            assertFalse("banner promosi dibuang",
                    url.toLowerCase(java.util.Locale.ROOT).contains("promosi"));
        }
        assertEquals("tidak ada alamat ganda", images.size(),
                new java.util.LinkedHashSet<>(images).size());
    }

    @Test public void alamatTidakSahDitolak() {
        assertFalse(KomikuConfig.isAllowed("http://komiku.org/manga/x/"));
        assertFalse(KomikuConfig.isAllowed("https://contoh-lain.com/manga/x/"));
        assertFalse(KomikuConfig.isAllowed(null));
        assertTrue(KomikuConfig.isAllowed("https://komiku.org/manga/x/"));
        assertTrue(KomikuConfig.isAllowed("https://img.komiku.org/upload5/a/1.webp"));
    }

    @Test public void nomorChapterBerbagaiBentuk() {
        assertEquals(393.0, KomikuManga.chapterNumber("Chapter 393"), 0.001);
        assertEquals(278.4, KomikuManga.chapterNumber("Chapter 278.4"), 0.001);
        assertEquals(1.5, KomikuManga.chapterNumber("Chapter 1.5"), 0.001);
        assertEquals(-1.0, KomikuManga.chapterNumber("Prolog"), 0.001);
        assertEquals(-1.0, KomikuManga.chapterNumber(null), 0.001);
    }

    @Test public void kunciChapterMemakaiAlamatBukanIndeks() {
        KomikuChapter a = new KomikuChapter("https://komiku.org/a-chapter-1/", "Chapter 1", "");
        KomikuChapter b = new KomikuChapter("https://komiku.org/a-chapter-1/", "Chapter 1", "");
        assertEquals(a.key(), b.key());
        assertNotNull(a.key());
    }
}