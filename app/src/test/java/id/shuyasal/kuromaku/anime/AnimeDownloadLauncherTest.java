package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pemilihan server untuk unduhan dari daftar episode.
 *
 * <p>Aturan yang diuji: berkas langsung dipakai lebih dulu, hanya mirror yang
 * benar-benar menyediakan berkas yang dipilih, dan tidak ada pemilihan palsu
 * untuk server yang hanya berupa pemutar bertaut.
 */
public class AnimeDownloadLauncherTest {

    private static String fixture(String name) {
        try (InputStream in = AnimeDownloadLauncherTest.class.getResourceAsStream(
                "/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("fixture: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static OtakudesuPlayback playback(String fixture) throws Exception {
        return OtakudesuMediaResolver.resolveHtml(fixture(fixture), null,
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");
    }

    private static AnimeDownloadLauncher.Probe probe(String server,
                                                     OtakudesuPlayback.Source source) {
        return new AnimeDownloadLauncher.Probe(server,
                new OtakudesuServerPicker.Probed(OtakudesuServerPicker.Status.READY,
                        source, ""));
    }

    @Test public void berkasLangsungDipilihLebihDulu() throws Exception {
        OtakudesuPlayback playback = playback("episode_native_mp4.html");
        AnimeDownloadLauncher.Pick pick = AnimeDownloadLauncher.choose(playback, null);

        assertNotNull("harus ada sumber yang bisa diunduh", pick);
        assertEquals("Halaman episode", pick.server);
        assertTrue("berupa berkas mp4", pick.source.url.endsWith(".mp4"));
        assertTrue("berupa berkas langsung",
                pick.source.kind == OtakudesuPlayback.Kind.PROGRESSIVE);
    }

    @Test public void hlsTidakDipilihSebagaiUnduhan() throws Exception {
        OtakudesuPlayback playback = playback("episode_native_hls.html");
        assertNull("manifest HLS bukan berkas yang bisa disimpan",
                AnimeDownloadLauncher.choose(playback, null));
    }

    @Test public void mirrorPemutarSajaTidakDipilih() throws Exception {
        OtakudesuPlayback playback = playback("episode_provider_only.html");
        List<AnimeDownloadLauncher.Probe> probes = new ArrayList<>();
        probes.add(probe("720p Vidhide · embed", new OtakudesuPlayback.Source(
                "https://otakudesu.blog/embed/abc", OtakudesuPlayback.Kind.PROGRESSIVE, "720p")));
        assertNull("halaman embed bukan berkas",
                AnimeDownloadLauncher.choose(playback, probes));
    }

    @Test public void mirrorYangMenyediakanBerkasDipilih() throws Exception {
        List<AnimeDownloadLauncher.Probe> probes = new ArrayList<>();
        probes.add(probe("720p Vidhide · embed", null));
        probes.add(probe("720p ODCDN",
                new OtakudesuPlayback.Source("https://cdn.contoh.test/a_720p.mp4",
                        OtakudesuPlayback.Kind.PROGRESSIVE, "720p")));

        AnimeDownloadLauncher.Pick pick = AnimeDownloadLauncher.choose(null, probes);
        assertNotNull(pick);
        assertEquals("server yang benar-benar menyediakan berkas", "720p ODCDN", pick.server);
        assertTrue(pick.source.url.endsWith(".mp4"));
    }

    @Test public void mirrorHlsTidakDipilih() throws Exception {
        List<AnimeDownloadLauncher.Probe> probes = new ArrayList<>();
        probes.add(probe("360p Vidhide",
                new OtakudesuPlayback.Source("https://cdn.contoh.test/master.m3u8",
                        OtakudesuPlayback.Kind.HLS, "360p")));
        assertNull(AnimeDownloadLauncher.choose(null, probes));
    }

    @Test public void tanpaSumberDanTanpaMirrorTidakAdaPilihan() {
        assertNull(AnimeDownloadLauncher.choose(null, null));
        assertNull(AnimeDownloadLauncher.choose(null, new ArrayList<>()));
    }

    @Test public void batasPemeriksaanMirrorMasukAkal() {
        // Alasan batas ini: memeriksa seluruh mirror sekaligus berarti
        // menembaki penyedia. Angka kecil dan eksplisit.
        assertTrue(AnimeDownloadLauncher.MAX_PROBES >= 1);
        assertTrue(AnimeDownloadLauncher.MAX_PROBES <= 5);
    }

    @Test public void pemilihanTidakPernahMenghasilkanSumberKosong() throws Exception {
        List<AnimeDownloadLauncher.Probe> probes = new ArrayList<>();
        probes.add(probe("kosong", new OtakudesuPlayback.Source(
                "", OtakudesuPlayback.Kind.PROGRESSIVE, "720p")));
        AnimeDownloadLauncher.Pick pick = AnimeDownloadLauncher.choose(null, probes);
        assertNull("alamat kosong tidak boleh dipilih", pick);
        assertFalse(pick != null);
    }
}