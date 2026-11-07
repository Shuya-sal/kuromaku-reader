package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pemisahan sumber video: berkas media yang boleh diputar Media3 versus mirror
 * penyedia pihak ketiga yang tidak pernah dibuka aplikasi.
 *
 * <p>Bukti utamanya adalah halaman episode asli Otakudesu
 * ({@code episode_mirrors.html}): halaman itu tidak memuat satu pun alamat
 * media, hanya iframe pemutar dan ID mirror. Jadi pemutar native harus
 * melaporkan "tidak ada sumber", bukan menebak.
 */
public class OtakudesuMediaResolverTest {

    static String fixture(String name) {
        try (InputStream in = OtakudesuMediaResolverTest.class
                .getResourceAsStream("/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("Fixture hilang: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @Test public void halamanEpisodeAsliHanyaPunyaMirrorBukanMedia() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_mirrors.html"));

        assertFalse("Tidak ada berkas media di halaman episode Otakudesu",
                playback.playable());
        assertTrue("Sumber langsung harus kosong", playback.sources.isEmpty());
        assertFalse("Mirror tetap harus terbaca untuk laporan",
                playback.mirrors.isEmpty());

        boolean found480 = false;
        boolean foundProvider = false;
        for (OtakudesuPlayback.Mirror mirror : playback.mirrors) {
            if ("480p".equals(mirror.quality)) found480 = true;
            if (mirror.provider.toLowerCase(java.util.Locale.ROOT).contains("odcdn")) {
                foundProvider = true;
            }
        }
        assertTrue("Kualitas 480p harus terdeteksi", found480);
        assertTrue("Provider odcdn harus terdeteksi", foundProvider);
    }

    @Test public void mirrorHanyaMenyimpanIdServerTanpaAlamatMedia() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_mirrors.html"));
        boolean anyId = false;
        for (OtakudesuPlayback.Mirror mirror : playback.mirrors) {
            if (!mirror.serverId.isEmpty()) {
                anyId = true;
                assertTrue("ID server harus angka, bukan URL",
                        mirror.serverId.matches("\\d+"));
            }
            assertFalse("Tidak boleh ada alamat media di mirror",
                    mirror.provider.contains("http"));
        }
        assertTrue(anyId);
    }

    @Test public void mediaHlsDiDalamVideoTagDikenali() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_native_hls.html"));
        assertTrue(playback.playable());
        assertTrue(playback.sources.size() >= 1);
        OtakudesuPlayback.Source first = playback.sources.get(0);
        assertEquals(OtakudesuPlayback.Kind.HLS, first.kind);
        assertTrue(first.url.endsWith(".m3u8"));
    }

    @Test public void mediaMp4ProgresifDikenali() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_native_mp4.html"));
        assertTrue(playback.playable());
        assertEquals(OtakudesuPlayback.Kind.PROGRESSIVE, playback.sources.get(0).kind);
    }

    @Test public void alamatGandaTidakDiduplikasi() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_native_hls.html"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (OtakudesuPlayback.Source source : playback.sources) {
            assertTrue("URL ganda: " + source.url, seen.add(source.url));
        }
        // master.m3u8 muncul di og:video dan di <source>: harus satu.
        int master = 0;
        for (OtakudesuPlayback.Source source : playback.sources) {
            if (source.url.endsWith("master.m3u8")) master++;
        }
        assertEquals(1, master);
    }

    @Test public void halamanPemutarPenyediaTidakDianggapMedia() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_provider_only.html"));
        assertFalse(playback.playable());
        assertTrue(playback.sources.isEmpty());
    }

    @Test public void alamatProviderDenganAkhiranMp4TetapDitolak() {
        assertTrue(OtakudesuMediaResolver.looksLikeProviderPage(
                "https://filedon.co/file/xyz.mp4"));
        assertTrue(OtakudesuMediaResolver.looksLikeProviderPage(
                "https://vidhide.com/e/abc.mp4"));
        assertTrue(OtakudesuMediaResolver.looksLikeProviderPage(
                "https://desustream.net/dstream/odcdn/?id=x"));
        assertFalse(OtakudesuMediaResolver.looksLikeProviderPage(
                "https://cdn.contoh.test/anime/ep1/master.m3u8"));
    }

    @Test public void hanyaHttpsTanpaKredensialYangDiterima() {
        assertEquals("", OtakudesuMediaResolver.normalize("http://cdn.test/a.m3u8"));
        assertEquals("", OtakudesuMediaResolver.normalize("https://user:pass@cdn.test/a.mp4"));
        assertEquals("", OtakudesuMediaResolver.normalize("https://cdn.test/../a.mp4"));
        assertEquals("https://cdn.test/a.m3u8",
                OtakudesuMediaResolver.normalize("https://cdn.test/a.m3u8#t=10"));
        // Fragmen dibuang, query dipertahankan.
        assertEquals("https://cdn.test/a.mp4?token=abc",
                OtakudesuMediaResolver.normalize("https://cdn.test/a.mp4?token=abc#x"));
    }

    @Test public void jenisMediaDitentukanDariAkhiran() {
        assertEquals(OtakudesuPlayback.Kind.HLS,
                OtakudesuMediaResolver.kindOf("https://c.test/a.m3u8?x=1"));
        assertEquals(OtakudesuPlayback.Kind.DASH,
                OtakudesuMediaResolver.kindOf("https://c.test/a.mpd"));
        assertEquals(OtakudesuPlayback.Kind.PROGRESSIVE,
                OtakudesuMediaResolver.kindOf("https://c.test/a.mp4"));
        assertEquals(OtakudesuPlayback.Kind.PROGRESSIVE,
                OtakudesuMediaResolver.kindOf("https://c.test/a.webm"));
        assertNull(OtakudesuMediaResolver.kindOf("https://c.test/halaman-episode/"));
        assertNull(OtakudesuMediaResolver.kindOf(""));
    }

    @Test public void halamanKosongMenghasilkanHasilKosong() {
        assertFalse(OtakudesuMediaResolver.resolveHtml("").playable());
        assertFalse(OtakudesuMediaResolver.resolveHtml(null).playable());
    }

    @Test public void alamatEpisodeHarusHttps() {
        OtakudesuSource.Transport transport = url -> fixture("episode_mirrors.html");
        try {
            OtakudesuMediaResolver.resolve(transport, "http://otakudesu.blog/episode/x/");
            fail("HTTP biasa harus ditolak");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
    }

    @Test public void kegagalanJaringanJadiPesanJelas() {
        OtakudesuSource.Transport transport = url -> {
            throw new IOException("mati");
        };
        try {
            OtakudesuMediaResolver.resolve(transport,
                    "https://otakudesu.blog/episode/x/");
            fail("Kegagalan jaringan harus dilaporkan");
        } catch (OtakudesuSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("tidak dapat diakses"));
        }
    }

    @Test public void sourceMengambilHtmlEpisode() throws Exception {
        OtakudesuSource source = new OtakudesuSource(url -> fixture("episode_native_mp4.html"));
        String html = source.html("https://otakudesu.blog/episode/x/");
        assertTrue(html.contains("ep1-720.mp4"));
    }

    @Test public void labelKualitasHanyaDariYangAda() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_native_mp4.html"));
        assertTrue("Fixture hanya 720p", playback.qualities().isEmpty()
                || playback.qualities().contains("720p"));
        assertFalse("Tidak boleh mengarang 1080p", playback.qualities().contains("1080p"));
    }

    // ------------------------------------------------- batch / portofolio

    @Test public void halamanBatchTidakMenghasilkanBerkasMedia() {
        // Halaman /batch/ sumber hanya daftar judul; tidak ada MP4/MKV di sana,
        // jadi resolver tidak boleh mengarang apa pun dari halaman itu.
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("portfolio_index.html"), null);
        assertFalse(playback.playable());
        assertTrue(playback.sources.isEmpty());
    }

    @Test public void alamatBatchDiturunkanDariAlamatEpisode() {
        assertEquals("https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/batch/",
                OtakudesuMediaResolver.batchUrlFor(
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/"));
        assertEquals("https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/batch/",
                OtakudesuMediaResolver.batchUrlFor(
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo"));
        assertEquals("", OtakudesuMediaResolver.batchUrlFor("https://contoh.test/anime/x/"));
        assertEquals("", OtakudesuMediaResolver.batchUrlFor(null));
    }

    @Test public void portofolioDipakaiHanyaBilaAdaBerkasDidalamnya() throws Exception {
        String episode = "<html><body><a href='" + OtakudesuSource.BASE
                + "/episode/x/batch/'>Batch</a></body></html>";
        String batch = "<html><body><h3>720p</h3>"
                + "<a href=\"https://cdn.odcloud.net/anime/contoh_720p.mp4\">720p</a>"
                + "</body></html>";
        OtakudesuMediaResolver.Fetcher fetcher = url -> batch;
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(episode, fetcher,
                "https://otakudesu.blog/episode/x/");

        assertTrue("Berkas dari halaman batch harus terbaca", playback.playable());
        assertEquals(OtakudesuPlayback.Kind.PROGRESSIVE, playback.sources.get(0).kind);
        assertEquals("https://cdn.odcloud.net/anime/contoh_720p.mp4",
                playback.sources.get(0).url);
    }

    @Test public void portofolioYangGagalTidakMembatalkanHasilLain() {
        String episode = "<html><body><video src=\"https://media.contoh.test/a.mp4\"></video>"
                + "<a href='" + OtakudesuSource.BASE + "/episode/x/batch/'>Batch</a>"
                + "</body></html>";
        OtakudesuMediaResolver.Fetcher broken = url -> {
            throw new IOException("batch tidak bisa dibuka");
        };
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(episode, broken,
                "https://otakudesu.blog/episode/x/");
        assertTrue("Berkas di halaman episode tetap dipakai", playback.playable());
    }

    @Test public void portofolioHanyaDariAlamatSumber() {
        String html = "<html><body>"
                + "<a href='https://situs-lain.test/portofolio/x/'>luar</a>"
                + "<a href='" + OtakudesuSource.BASE + "/episode/x/batch/'>Batch</a>"
                + "</body></html>";
        java.util.List<String> links = OtakudesuMediaResolver.portfolioLinks(
                org.jsoup.Jsoup.parse(html), "https://otakudesu.blog/episode/x/");
        for (String link : links) {
            assertTrue("Hanya alamat sumber: " + link,
                    OtakudesuMediaResolver.isSameSource(link));
            assertTrue(link.endsWith("/batch/"));
        }
    }

    // ------------------------------------------------- cadangan mirror

    @Test public void cadanganTidakMemakaiPemutarBeriklan() {
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(
                fixture("episode_mirrors.html"), null);

        assertFalse("Harus ada cadangan dari mirror terbuka", playback.fallbacks.isEmpty());
        for (OtakudesuPlayback.Fallback fallback : playback.fallbacks) {
            String provider = fallback.provider.toLowerCase(java.util.Locale.ROOT);
            assertFalse("vidhide tidak boleh jadi cadangan: " + provider,
                    provider.contains("vidhide"));
            assertFalse("mega tidak boleh jadi cadangan: " + provider,
                    provider.contains("mega"));
            assertTrue("Cadangan memakai kualitas tertinggi",
                    OtakudesuMediaResolver.qualityRank(fallback.quality) > 0);
        }
    }

    @Test public void tanpaMirrorTerbukaCadanganKosong() {
        String html = "<html><body><div class='mirrorstream'><ul class='m720p'>"
                + "<span>Mirror 720p</span><li><a href='#'>vidhide</a></li>"
                + "<li><a href='#'>mega</a></li></ul></div></body></html>";
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(html, null);
        assertTrue("Semua mirror menutup berkas", playback.fallbacks.isEmpty());
        assertFalse(playback.playable());
    }

    @Test public void peringkatKualitasDibacaDariLabel() {
        assertEquals(720, OtakudesuMediaResolver.qualityRank("720p"));
        assertEquals(1080, OtakudesuMediaResolver.qualityRank("1080p"));
        assertEquals(720, OtakudesuMediaResolver.qualityRank("720"));
        assertEquals(-1, OtakudesuMediaResolver.qualityRank("HD"));
        assertEquals(-1, OtakudesuMediaResolver.qualityRank(null));
    }

    @Test public void alamatTerbukaDidalamSkripDikenali() {
        String html = "<html><body><script>const videoURL = "
                + "\"https://cdn.odcloud.net/anime/Otakudesu.io_FSKC--01_720p.mp4\";"
                + "</script></body></html>";
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(html, null);
        assertTrue("Alamat di dalam skrip harus dikenali", playback.playable());
        assertEquals(OtakudesuPlayback.Kind.PROGRESSIVE, playback.sources.get(0).kind);
    }

    @Test public void hlsDenganTokenDidalamSkripDikenali() {
        String html = "<html><script>var links={\"hls2\":"
                + "\"https://contoh.acek-cdn.com/hls2/x/master.m3u8?t=abc&s=1\"};</script></html>";
        OtakudesuPlayback playback = OtakudesuMediaResolver.resolveHtml(html, null);
        assertTrue(playback.playable());
        assertEquals(OtakudesuPlayback.Kind.HLS, playback.sources.get(0).kind);
        assertTrue("Query token ikut terbawa", playback.sources.get(0).url.contains("t=abc"));
    }
}
