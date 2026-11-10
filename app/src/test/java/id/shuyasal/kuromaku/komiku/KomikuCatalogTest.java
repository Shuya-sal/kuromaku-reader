package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Katalog lengkap Komiku.
 *
 * <p>Beranda hanya memuat 40 judul; katalog penuh diambil dari alamat paginasi
 * situs. Tes ini memakai fragmen asli dari situs (halaman 1, halaman 2, dan
 * halaman terakhir) supaya bentuk kartunya benar-benar yang diurai, bukan
 * contoh karangan.
 */
public class KomikuCatalogTest {

    private static String fixture(String name) throws Exception {
        try (InputStream stream = KomikuCatalogTest.class.getResourceAsStream(
                "/komiku/" + name)) {
            assertNotNull("fixture " + name, stream);
            return new String(readAll(stream), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static KomikuSource bound(final java.util.Map<String, String> pages) {
        return new KomikuSource(url -> {
            String html = pages.get(url);
            if (html == null) throw new KomikuSource.SourceException("tidak ada " + url);
            return html;
        });
    }

    private File tempFile() throws Exception {
        File file = File.createTempFile("katalog", ".json");
        assertTrue(file.delete());
        return file;
    }

    @Test public void halamanPertamaMemuatSepuluhJudul() throws Exception {
        KomikuSource source = bound(java.util.Collections.singletonMap(
                KomikuSource.catalogPageUrl(1), fixture("api_page1.html")));
        List<KomikuManga> page = source.catalogPage(1);
        assertEquals(10, page.size());
        KomikuManga first = page.get(0);
        assertTrue("slug terisi", !first.id.isEmpty());
        assertFalse("judul terisi", first.title.isEmpty());
        assertTrue("alamat detail",
                first.url.startsWith("https://komiku.org/manga/"));
        assertTrue("cover HTTPS dari host situs",
                first.coverUrl.startsWith("https://"));
        assertFalse("tipe terbaca", first.type.isEmpty());
    }

    @Test public void halamanKeduaTidakMengulangHalamanPertama() throws Exception {
        KomikuSource source = bound(java.util.Collections.singletonMap(
                KomikuSource.catalogPageUrl(2), fixture("api_page2.html")));
        List<KomikuManga> page = source.catalogPage(2);
        assertEquals(10, page.size());
        Set<String> ids = new HashSet<>();
        for (KomikuManga manga : page) assertTrue(ids.add(manga.id));
    }

    @Test public void halamanTerakhirTerbacaDanBisaKosong() throws Exception {
        KomikuSource source = bound(java.util.Collections.singletonMap(
                KomikuSource.catalogPageUrl(807), fixture("api_last.html")));
        List<KomikuManga> page = source.catalogPage(807);
        assertFalse("halaman terakhir memuat sisa judul", page.isEmpty());
        assertTrue("kurang dari sepuluh", page.size() <= 10);
    }

    @Test public void alamatHalamanMengikutiBentukSitus() {
        assertEquals("https://api.komiku.org/manga/", KomikuSource.catalogPageUrl(1));
        assertEquals("https://api.komiku.org/manga/page/2/",
                KomikuSource.catalogPageUrl(2));
        assertEquals("halaman nol dianggap satu", KomikuSource.catalogPageUrl(0),
                KomikuSource.catalogPageUrl(1));
    }

    @Test public void nomorHalamanBerikutnyaDibacaDariSatuPermintaan() throws Exception {
        KomikuSource source = bound(java.util.Collections.singletonMap(
                KomikuSource.catalogPageUrl(1), fixture("api_page1.html")));
        KomikuSource.CatalogPage page = source.catalogPageWithNext(1);
        assertEquals("sepuluh judul", 10, page.items.size());
        assertEquals("halaman berikutnya", 2, page.nextPage);
    }

    @Test public void halamanTerakhirTidakMenunjukHalamanBerikutnya() throws Exception {
        KomikuSource source = bound(java.util.Collections.singletonMap(
                KomikuSource.catalogPageUrl(807), fixture("api_last.html")));
        assertEquals("tidak ada halaman berikutnya", 0,
                source.catalogPageWithNext(807).nextPage);
    }

    @Test public void katalogDisimpanDanDibacaUlang() throws Exception {
        KomikuCatalog catalog = new KomikuCatalog(tempFile());
        List<KomikuManga> items = new ArrayList<>();
        items.add(new KomikuManga("satu", "Judul Satu", "https://komiku.org/manga/satu/",
                "https://thumbnail.komiku.org/satu.jpg", "Manhwa", "Chapter 3", "4 jam lalu"));
        items.add(new KomikuManga("dua", "Judul Dua", "https://komiku.org/manga/dua/",
                "", "Manga", "Chapter 9", "1 jam lalu"));
        catalog.save(items, 2);

        List<KomikuManga> back = catalog.load();
        assertEquals(2, back.size());
        assertEquals("Judul Satu", back.get(0).title);
        assertEquals("Manhwa", back.get(0).type);
        assertEquals("Chapter 3", back.get(0).latestChapter);
        assertEquals(2, catalog.stats().pages);
        assertTrue(catalog.exists());
    }

    @Test public void katalogRusakDianggapKosongBukanGagal() throws Exception {
        File file = tempFile();
        java.nio.file.Files.write(file.toPath(), "{bukan json".getBytes(StandardCharsets.UTF_8));
        KomikuCatalog catalog = new KomikuCatalog(file);
        assertTrue("katalog rusak dibaca sebagai kosong", catalog.load().isEmpty());
        assertFalse(catalog.exists());
    }

    @Test public void pengambilanBerhentiSaatHalamanHabis() throws Exception {
        final String page1 = fixture("api_page1.html");
        final String page2 = fixture("api_page2.html");
        KomikuSource source = new KomikuSource(url -> {
            if (url.equals(KomikuSource.catalogPageUrl(1))) return page1;
            if (url.equals(KomikuSource.catalogPageUrl(2))) return page2;
            // Halaman berikutnya kosong: paginasi habis.
            return "<div class=\"bge\"></div>";
        });
        KomikuCatalog.Result result = new KomikuCatalog(tempFile()).fetchAll(source, null);
        assertEquals("dua halaman berisi", 20, result.items.size());
        assertTrue("dianggap lengkap", result.complete);
        assertEquals(3, result.pagesRead);
    }

    @Test public void pengambilanSebagianTetapDisimpan() throws Exception {
        final String page1 = fixture("api_page1.html");
        KomikuSource source = new KomikuSource(url -> {
            if (url.equals(KomikuSource.catalogPageUrl(1))) return page1;
            throw new KomikuSource.SourceException("jaringan putus");
        });
        KomikuCatalog catalog = new KomikuCatalog(tempFile());
        KomikuCatalog.Result result = catalog.fetchAll(source, null);
        assertEquals(10, result.items.size());
        assertFalse("belum lengkap", result.complete);
        assertTrue("alasan berhenti dicatat", !result.stoppedBecause.isEmpty());
        catalog.save(result.items, result.pagesRead);
        assertEquals(10, catalog.load().size());
    }

    @Test public void pengambilanBisaDihentikanPemanggil() throws Exception {
        final String page1 = fixture("api_page1.html");
        final String page2 = fixture("api_page2.html");
        KomikuSource source = new KomikuSource(url -> {
            if (url.equals(KomikuSource.catalogPageUrl(1))) return page1;
            if (url.equals(KomikuSource.catalogPageUrl(2))) return page2;
            return "<div class=\"bge\"></div>";
        });
        final List<Integer> seen = new ArrayList<>();
        KomikuCatalog.Result result = new KomikuCatalog(tempFile()).fetchAll(source,
                (page, collected, last) -> {
                    seen.add(page);
                    return page < 2; // minta berhenti setelah halaman 2
                });
        assertEquals(2, seen.size());
        assertEquals(20, result.items.size());
        assertFalse("berhenti atas permintaan", result.complete);
    }

    @Test public void judulGandaTidakDigandakan() throws Exception {
        final String page1 = fixture("api_page1.html");
        // Situs yang mengulang halaman yang sama (pernah terjadi saat halaman
        // di luar jangkauan mengembalikan halaman pertama).
        KomikuSource source = new KomikuSource(url -> page1);
        KomikuCatalog.Result result = new KomikuCatalog(tempFile()).fetchAll(source, null);
        assertEquals("judul tidak berlipat", 10, result.items.size());
    }

    /**
     * Uji jaringan sungguhan (opt-in): memastikan alamat paginasi yang dipakai
     * benar-benar hidup dan bentuk kartunya masih sama. Dilewati secara default
     * supaya suite tidak bergantung pada internet.
     */
    @Test public void jaringanSungguhanMengambilTigaHalaman() throws Exception {
        org.junit.Assume.assumeTrue("jalankan dengan -PnetworkTest=1",
                "1".equals(System.getProperty("kuromaku.network"))
                        || "1".equals(System.getenv("KUROMAKU_NETWORK_TEST")));
        KomikuSource source = new KomikuSource();
        List<String> ids = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            List<KomikuManga> batch = source.catalogPage(page);
            assertFalse("halaman " + page + " berisi", batch.isEmpty());
            for (KomikuManga manga : batch) {
                assertTrue("judul " + page, !manga.title.isEmpty());
                ids.add(manga.id);
            }
        }
        assertEquals("tiga halaman penuh", 30, ids.size());
        assertEquals("tidak ada judul ganda", 30, new HashSet<>(ids).size());
        int next = source.catalogPageWithNext(1).nextPage;
        assertTrue("situs memberi halaman berikutnya", next == 2);
    }
}
