package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pembaca Otakudesu diuji terhadap HTML asli sumber yang disimpan sebagai
 * fixture, bukan terhadap HTML karangan. Kalau sumber mengubah markup, tes ini
 * gagal — persis seperti aplikasi yang menolak menampilkan data palsu.
 */
public class OtakudesuSourceTest {

    // ---------------------------------------------------------------- fixture

    private static String fixture(String name) {
        try (InputStream in = OtakudesuSourceTest.class
                .getResourceAsStream("/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("Fixture tidak ditemukan: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Transport palsu: memetakan URL ke fixture dan mencatat URL yang diminta. */
    private static final class FixtureTransport implements OtakudesuSource.Transport {
        final List<String> requested = new ArrayList<>();

        @Override public String get(String url) {
            requested.add(url);
            if (url.contains("/ongoing-anime/page/2/")) return fixture("ongoing.html");
            if (url.contains("/ongoing-anime/")) return fixture("ongoing.html");
            if (url.contains("/complete-anime/")) return fixture("ongoing.html");
            if (url.contains("/anime/")) return fixture("detail.html");
            if (url.contains("/jadwal-rilis/")) return fixture("schedule.html");
            if (url.contains("?s=")) return fixture("search.html");
            return "<html><body>kosong</body></html>";
        }
    }

    private OtakudesuSource source(FixtureTransport transport) {
        return new OtakudesuSource(transport);
    }

    // ---------------------------------------------------------------- katalog

    @Test public void katalogOngoingTerbacaDariHalamanAsli() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        OtakudesuAnime.Page page = source(transport).catalog(OtakudesuSource.Feed.ONGOING, 1);

        assertTrue("Katalog harus berisi anime", page.items.size() >= 10);
        assertEquals(1, page.page);
        assertTrue("Sumber punya 6 halaman; totalPages harus terbaca", page.totalPages >= 6);

        OtakudesuAnime first = page.items.get(0);
        assertFalse(first.animeId.isEmpty());
        assertFalse(first.title.isEmpty());
        assertTrue(first.url.startsWith("https://otakudesu.blog/anime/"));
        assertTrue(first.posterUrl.startsWith("https://"));
        assertTrue("Kartu harus membawa episode terbaru", first.latestEpisode.startsWith("Episode"));

        Set<String> ids = new HashSet<>();
        for (OtakudesuAnime item : page.items) {
            assertTrue("ID ganda: " + item.animeId, ids.add(item.animeId));
        }
    }

    @Test public void halamanKeduaMemakaiAlamatPaginasiSumber() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        source(transport).catalog(OtakudesuSource.Feed.ONGOING, 2);
        assertEquals("https://otakudesu.blog/ongoing-anime/page/2/",
                transport.requested.get(0));
    }

    @Test public void katalogCompleteMemakaiHalamanComplete() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        source(transport).catalog(OtakudesuSource.Feed.COMPLETE, 1);
        assertEquals("https://otakudesu.blog/complete-anime/", transport.requested.get(0));
    }

    // -------------------------------------------------------------- pencarian

    @Test public void pencarianMemakaiHalamanTunggalSumber() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        OtakudesuAnime.Page page = source(transport).search("naruto", 3);

        String requested = transport.requested.get(0);
        assertTrue(requested.startsWith("https://otakudesu.blog/?s=naruto"));
        assertFalse("Sumber menolak /page/N/?s=", requested.contains("/page/3/"));
        assertEquals("Hasil pencarian sumber satu halaman", 1, page.totalPages);
        assertTrue(page.items.size() >= 1);
        assertFalse(page.items.get(0).title.isEmpty());
        assertFalse("Genre hasil pencarian harus terbaca",
                page.items.get(0).genres.isEmpty());
    }

    @Test public void pencarianKosongTidakMenghubungiJaringan() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        OtakudesuAnime.Page page = source(transport).search("   ", 1);
        assertTrue(page.items.isEmpty());
        assertTrue(transport.requested.isEmpty());
    }

    // ----------------------------------------------------------------- detail

    @Test public void detailAnimeLengkapDenganDaftarEpisode() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        OtakudesuAnime.Detail detail = source(transport).detail("uchi-otouto-sumimasen-sub-indo");

        assertTrue(detail.summary.title.startsWith("Uchi no Otouto-domo"));
        assertTrue(detail.summary.posterUrl.startsWith("https://"));
        assertEquals("Ongoing", detail.status);
        assertEquals("Lay-duce", detail.studio);
        assertEquals("24", detail.totalEpisodes);
        assertEquals("Jul 04, 2026", detail.released);
        assertTrue("Genre harus terbaca", detail.summary.genres.contains("Comedy"));
        assertFalse("Sinopsis harus terbaca", detail.synopsis.isEmpty());

        assertTrue("Episode harus terbaca", detail.episodes.size() >= 18);
        OtakudesuAnime.Episode first = detail.episodes.get(0);
        assertEquals("utgs-episode-18-sub-indo", first.episodeId);
        assertEquals(18d, first.number, 0.0001d);
        assertFalse("Tanggal episode harus terbaca", first.date.isEmpty());
        assertTrue(first.url.startsWith("https://otakudesu.blog/episode/"));

        Set<String> urls = new HashSet<>();
        for (OtakudesuAnime.Episode episode : detail.episodes) {
            assertTrue("Episode ganda: " + episode.url, urls.add(episode.url));
        }
    }

    @Test public void alamatBukanSumberDikenalDitolak() {
        OtakudesuSource source = new OtakudesuSource(new FixtureTransport());
        try {
            source.detail("https://contoh-lain.test/anime/x/");
            fail("Alamat asing seharusnya ditolak");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("bukan dari sumber"));
        }
    }

    // ----------------------------------------------------------------- jadwal

    @Test public void jadwalRilisTerbacaPerHari() throws Exception {
        FixtureTransport transport = new FixtureTransport();
        List<OtakudesuAnime> items = source(transport).schedule();
        assertTrue(items.size() >= 20);

        Set<String> days = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (OtakudesuAnime item : items) {
            assertFalse(item.day.isEmpty());
            days.add(item.day);
            assertTrue("ID ganda di jadwal: " + item.animeId, ids.add(item.animeId));
        }
        assertTrue("Jadwal harus memuat nama hari", days.contains("Senin"));
        assertTrue(days.contains("Minggu"));
    }

    // ------------------------------------------------------------------ galat

    @Test public void strukturBerubahMenghasilkanPesanJelas() {
        OtakudesuSource source = new OtakudesuSource(new OtakudesuSource.Transport() {
            @Override public String get(String url) {
                return "<html><body><p>halaman lain</p></body></html>";
            }
        });
        try {
            source.catalog(OtakudesuSource.Feed.ONGOING, 1);
            fail("Struktur asing seharusnya gagal, bukan mengembalikan data kosong");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("struktur halaman"));
        }
    }

    @Test public void halamanVerifikasiTidakDilewati() {
        OtakudesuSource source = new OtakudesuSource(new OtakudesuSource.Transport() {
            @Override public String get(String url) {
                return "<html><title>Just a moment...</title><body>cf-chl</body></html>";
            }
        });
        try {
            source.catalog(OtakudesuSource.Feed.COMPLETE, 1);
            fail("CAPTCHA tidak boleh dilewati");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("verifikasi"));
        }
    }

    @Test public void hanyaHttpsYangDiizinkan() {
        OtakudesuSource source = new OtakudesuSource(new FixtureTransport());
        try {
            source.detail("http://otakudesu.blog/anime/x/");
            fail("HTTP biasa seharusnya ditolak");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
    }

    @Test public void pesanGalatJaringanTidakMembocorkanDetailInternal() {
        OtakudesuSource source = new OtakudesuSource(new OtakudesuSource.Transport() {
            @Override public String get(String url) throws IOException {
                throw new IOException("timeout ke 10.0.0.1");
            }
        });
        try {
            source.catalog(OtakudesuSource.Feed.ONGOING, 1);
            fail("Kegagalan jaringan harus dilaporkan");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("tidak dapat diakses"));
            assertNotNull(expected.getCause());
        }
    }

    // ------------------------------------------------------------------ model

    @Test public void idEpisodeStabilDanTidakKosong() {
        assertEquals("utgs-episode-18-sub-indo",
                OtakudesuSource.episodeId("https://otakudesu.blog/episode/utgs-episode-18-sub-indo/"));
        assertEquals("naruto-episode-1-sub-indo",
                OtakudesuSource.episodeId("https://otakudesu.blog/episode/naruto-episode-1-sub-indo"));
        assertEquals("", OtakudesuSource.episodeId(""));
        assertTrue(OtakudesuSource.animeId("https://contoh.test/bukan-anime/x/").isEmpty());
        assertEquals("uchi-otouto-sumimasen-sub-indo",
                OtakudesuSource.animeId("https://otakudesu.blog/anime/uchi-otouto-sumimasen-sub-indo/"));
    }
}