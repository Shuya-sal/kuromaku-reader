package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Ingatan hasil pemeriksaan server.
 *
 * <p>Dua hal yang dijaga: hasil episode lain tidak boleh bocor, dan hasil yang
 * sudah kedaluwarsa tidak boleh dipakai — alamat berkas penyedia bisa berhenti
 * berlaku kapan saja.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ServerProbeCacheTest {

    private static final String EPISODE_A = "https://otakudesu.blog/episode/a/";
    private static final String EPISODE_B = "https://otakudesu.blog/episode/b/";

    @After public void bersihkan() {
        ServerProbeCache.clear();
    }

    private static OtakudesuServerPicker.Probed siap() {
        return new OtakudesuServerPicker.Probed(OtakudesuServerPicker.Status.READY,
                new OtakudesuPlayback.Source("https://cdn/x.mp4",
                        OtakudesuPlayback.Kind.PROGRESSIVE, "720p"), "");
    }

    @Test public void hasilSegarDipakaiLagi() {
        ServerProbeCache.put(EPISODE_A, "mirror-1", siap(), 1_000L);
        assertNotNull(ServerProbeCache.get(EPISODE_A, "mirror-1", 2_000L));
    }

    @Test public void hasilKedaluwarsaTidakDipakai() {
        ServerProbeCache.put(EPISODE_A, "mirror-1", siap(), 1_000L);
        long lewat = 1_000L + ServerProbeCache.TTL_MS + 1;
        assertNull("harus diperiksa ulang setelah TTL", 
                ServerProbeCache.get(EPISODE_A, "mirror-1", lewat));
    }

    @Test public void episodeLainTidakMelihatHasilEpisodeIni() {
        ServerProbeCache.put(EPISODE_A, "mirror-1", siap(), 1_000L);
        assertNull("kunci menyertakan alamat episode",
                ServerProbeCache.get(EPISODE_B, "mirror-1", 2_000L));
    }

    @Test public void lupakanSatuEpisodeSaja() {
        ServerProbeCache.put(EPISODE_A, "mirror-1", siap(), 1_000L);
        ServerProbeCache.put(EPISODE_B, "mirror-1", siap(), 1_000L);

        ServerProbeCache.forget(EPISODE_A);

        assertNull(ServerProbeCache.get(EPISODE_A, "mirror-1", 2_000L));
        assertNotNull("episode lain tidak ikut dibuang",
                ServerProbeCache.get(EPISODE_B, "mirror-1", 2_000L));
    }

    @Test public void hasilGagalJugaDiingat() {
        OtakudesuServerPicker.Probed gagal = new OtakudesuServerPicker.Probed(
                OtakudesuServerPicker.Status.PROTECTED_SERVER, null, "pemutar penyedia");
        ServerProbeCache.put(EPISODE_A, "mirror-2", gagal, 1_000L);

        OtakudesuServerPicker.Probed hasil = ServerProbeCache.get(EPISODE_A, "mirror-2", 1_500L);
        assertNotNull(hasil);
        assertEquals(OtakudesuServerPicker.Status.PROTECTED_SERVER, hasil.status);
    }

    @Test public void jumlahEntriDibatasi() {
        for (int i = 0; i < 260; i++) {
            ServerProbeCache.put(EPISODE_A, "mirror-" + i, siap(), 1_000L);
        }
        assertEquals("entri lama dibuang", 200, ServerProbeCache.size());
        assertNotNull("entri terbaru tetap ada",
                ServerProbeCache.get(EPISODE_A, "mirror-259", 1_100L));
    }
}