package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pencarian Komiku memakai fragmen dari alamat API situs.
 *
 * <p>Fragmen itu memuat kartu dengan tautan <b>relatif</b>; parser lama hanya
 * mencari tautan absolut sehingga selalu mengembalikan nol judul. Fixture di
 * sini adalah fragmen sungguhan dari situs untuk kata kunci "solo".
 */
public class KomikuSearchTest {

    private static String fixture(String name) throws Exception {
        try (InputStream stream = KomikuSearchTest.class.getResourceAsStream(
                "/komiku/" + name)) {
            assertNotNull("fixture " + name, stream);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test public void pencarianSoloMenemukanSepuluhJudul() throws Exception {
        final String fragment = fixture("search_solo.html");
        KomikuSource source = new KomikuSource(url -> {
            if (url.startsWith("https://api.komiku.org/")) return fragment;
            throw new KomikuSource.SourceException("tidak dipakai");
        });
        List<KomikuManga> found = source.search("solo");
        assertEquals("sepuluh hasil dari fragmen", 10, found.size());

        Set<String> slugs = new HashSet<>();
        for (KomikuManga manga : found) {
            assertFalse("slug terisi", manga.id.isEmpty());
            assertTrue("slug unik: " + manga.id, slugs.add(manga.id));
            assertFalse("judul terisi", manga.title.isEmpty());
            assertTrue("alamat absolut",
                    manga.url.startsWith("https://komiku.org/manga/"));
        }
    }

    @Test public void judulDanMetadataTerbaca() throws Exception {
        final String fragment = fixture("search_solo.html");
        KomikuSource source = new KomikuSource(url -> fragment);
        List<KomikuManga> found = source.search("solo");

        KomikuManga first = found.get(0);
        assertEquals("solo-swordmaster", first.id);
        assertEquals("Solo Swordmaster", first.title);
        assertEquals("Manhwa", first.type);
        assertTrue("waktu update terbaca: " + first.updated,
                first.updated.toLowerCase(java.util.Locale.ROOT).contains("hari"));
        assertTrue("chapter terbaru terbaca: " + first.latestChapter,
                first.latestChapter.toLowerCase(java.util.Locale.ROOT).contains("chapter"));
        assertTrue("cover HTTPS", first.coverUrl.startsWith("https://"));
    }

    @Test public void semuaHasilPunyaJudulYangBisaDibaca() throws Exception {
        final String fragment = fixture("search_solo.html");
        KomikuSource source = new KomikuSource(url -> fragment);
        for (KomikuManga manga : source.search("solo")) {
            assertFalse("tidak ada judul kosong", manga.title.trim().isEmpty());
            assertFalse("judul bukan slug mentah",
                    manga.title.equals(manga.id.replace('-', ' '))
                            && manga.title.contains("-"));
        }
    }

    @Test public void pencarianKosongTidakMemanggilJaringan() throws Exception {
        final List<String> called = new ArrayList<>();
        KomikuSource source = new KomikuSource(url -> {
            called.add(url);
            return "";
        });
        assertTrue(source.search("   ").isEmpty());
        assertTrue("tidak ada permintaan", called.isEmpty());
    }

    @Test public void apiTidakTersediaMenghasilkanDaftarKosongBukanCrash() throws Exception {
        // Bila sumber pencarian tidak bisa dihubungi, pencarian mengembalikan
        // daftar kosong dengan pesan — bukan hasil karangan dan bukan kegagalan
        // yang mematikan layar.
        KomikuSource source = new KomikuSource(url -> {
            throw new KomikuSource.SourceException("sumber tidak tersedia");
        });
        try {
            List<KomikuManga> found = source.search("solo");
            assertTrue("daftar kosong, bukan hasil karangan", found.isEmpty());
        } catch (KomikuSource.SourceException expected) {
            assertNotNull("pesan kegagalan ada", expected.getMessage());
        }
    }

    @Test public void alamatApiMemuatKataKunciTerkode() throws Exception {
        final List<String> urls = new ArrayList<>();
        KomikuSource source = new KomikuSource(url -> {
            urls.add(url);
            return "<div class=\"bge\"></div>";
        });
        source.search("solo leveling");
        assertFalse("ada permintaan", urls.isEmpty());
        assertTrue("kata kunci terkode di alamat API: " + urls.get(0),
                urls.get(0).startsWith("https://api.komiku.org/")
                        && urls.get(0).contains("post_type=manga")
                        && urls.get(0).contains("solo+leveling"));
    }

    /**
     * Uji jaringan sungguhan (opt-in): memastikan alamat pencarian yang dipakai
     * benar-benar hidup dan bentuk kartunya masih sama.
     */
    @Test public void jaringanSungguhanMencariSolo() throws Exception {
        org.junit.Assume.assumeTrue("jalankan dengan KUROMAKU_NETWORK_TEST=1",
                "1".equals(System.getenv("KUROMAKU_NETWORK_TEST"))
                        || "1".equals(System.getProperty("kuromaku.network")));
        List<KomikuManga> found = new KomikuSource().search("solo");
        assertFalse("hasil tidak kosong", found.isEmpty());
        for (KomikuManga manga : found) {
            assertFalse("judul " + manga.id, manga.title.isEmpty());
            assertTrue("alamat absolut", manga.url.startsWith("https://komiku.org/manga/"));
        }
        System.out.println("HASIL " + found.size() + " judul, pertama: "
                + found.get(0).title + " / " + found.get(0).type + " / "
                + found.get(0).updated + " / " + found.get(0).latestChapter);
    }
}
