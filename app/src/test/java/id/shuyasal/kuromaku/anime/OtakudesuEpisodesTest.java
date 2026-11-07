package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Daftar episode yang dibawa ke pemutar: urutan cerita dan tetangga episode. */
public class OtakudesuEpisodesTest {

    private static OtakudesuAnime.Episode episode(int number, String url) {
        return new OtakudesuAnime.Episode("ep-" + number, "Episode " + number, url,
                "1 Januari 2026", number);
    }

    private static List<OtakudesuAnime.Episode> sample() {
        List<OtakudesuAnime.Episode> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            list.add(episode(i, "https://otakudesu.blog/episode/e-" + i + "/"));
        }
        return list;
    }

    @Test public void daftarBertahanMelewatiIntent() {
        List<OtakudesuAnime.Episode> asli = sample();
        List<OtakudesuAnime.Episode> kembali =
                OtakudesuEpisodes.fromJson(OtakudesuEpisodes.toJson(asli));

        assertEquals(asli.size(), kembali.size());
        for (int i = 0; i < asli.size(); i++) {
            assertEquals(asli.get(i).url, kembali.get(i).url);
            assertEquals(asli.get(i).label, kembali.get(i).label);
            assertEquals(asli.get(i).number, kembali.get(i).number, 0.0001d);
        }
    }

    @Test public void tetanggaMengikutiUrutanCerita() {
        List<OtakudesuAnime.Episode> list = sample();
        assertEquals("Episode 4",
                OtakudesuEpisodes.neighbor(list, list.get(4).url, -1).label);
        assertEquals("Episode 2",
                OtakudesuEpisodes.neighbor(list, list.get(0).url, 1).label);
    }

    @Test public void ujungDaftarTidakPunyaTetangga() {
        List<OtakudesuAnime.Episode> list = sample();
        assertNull(OtakudesuEpisodes.neighbor(list, list.get(0).url, -1));
        assertNull(OtakudesuEpisodes.neighbor(list, list.get(4).url, 1));
        assertNull(OtakudesuEpisodes.neighbor(list, "https://tidak/ada/", 1));
        assertNull(OtakudesuEpisodes.neighbor(new ArrayList<>(), "x", 1));
    }

    @Test public void episiodeTanpaAlamatDibuang() {
        List<OtakudesuAnime.Episode> list = new ArrayList<>();
        list.add(episode(1, "https://otakudesu.blog/episode/e-1/"));
        list.add(episode(2, ""));
        assertEquals(1, OtakudesuEpisodes.fromJson(OtakudesuEpisodes.toJson(list)).size());
    }

    @Test public void jsonRusakMenjadiDaftarKosongBukanGalat() {
        assertTrue(OtakudesuEpisodes.fromJson("bukan json").isEmpty());
        assertTrue(OtakudesuEpisodes.fromJson(null).isEmpty());
        assertTrue(OtakudesuEpisodes.fromJson("").isEmpty());
    }

    /**
     * Tombol Berikutnya harus menuju episode yang nomornya lebih tinggi.
     *
     * <p>Akar bug: halaman episode Otakudesu menulis episode terbaru lebih dulu,
     * jadi daftarnya menurun. Memakai indeks daftar apa adanya membuat
     * "Berikutnya" dari Episode 1 tidak ada (ditolak karena di luar batas) dan
     * "Berikutnya" dari Episode 3 justru mundur ke Episode 2.
     */
    @Test public void berikutnyaNaikNomorDanSebelumnyaTurun() {
        java.util.List<OtakudesuAnime.Episode> descending = new java.util.ArrayList<>();
        for (int n = 6; n >= 1; n--) {
            descending.add(episode(n));
        }

        String ep1 = episode(1).url;
        OtakudesuAnime.Episode next = OtakudesuEpisodes.neighbor(descending, ep1, 1);
        org.junit.Assert.assertNotNull("Episode 1 harus punya Berikutnya", next);
        assertEquals(2d, next.number, 0.001);

        String ep2 = episode(2).url;
        OtakudesuAnime.Episode previous = OtakudesuEpisodes.neighbor(descending, ep2, -1);
        assertNotNull("Episode 2 harus punya Sebelumnya", previous);
        assertEquals(1d, previous.number, 0.001);

        // Episode terakhir tidak punya Berikutnya; episode pertama tidak punya
        // Sebelumnya. Tombolnya dinonaktifkan, bukan membuka episode yang sama.
        assertNull(OtakudesuEpisodes.neighbor(descending, episode(6).url, 1));
        assertNull(OtakudesuEpisodes.neighbor(descending, episode(1).url, -1));
    }

    /** Urutan cerita selalu menaik, apa pun urutan dari sumber. */
    @Test public void urutanCeritaSelaluMenaik() {
        java.util.List<OtakudesuAnime.Episode> descending = new java.util.ArrayList<>();
        for (int n = 6; n >= 1; n--) descending.add(episode(n));
        java.util.List<OtakudesuAnime.Episode> ordered =
                OtakudesuEpisodes.inStoryOrder(descending);
        assertEquals(6, ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            assertEquals(i + 1, ordered.get(i).number, 0.001);
        }
        assertEquals(1d, OtakudesuEpisodes.first(descending).number, 0.001);
        assertEquals(6d, OtakudesuEpisodes.last(descending).number, 0.001);
    }

    /** Daftar tanpa nomor tidak diacak dan tidak dibuang. */
    @Test public void daftarTanpaNomorTidakDiacak() {
        java.util.List<OtakudesuAnime.Episode> tanpaNomor = new java.util.ArrayList<>();
        tanpaNomor.add(new OtakudesuAnime.Episode("a", "Spesial A", "https://contoh/a", "", -1));
        tanpaNomor.add(new OtakudesuAnime.Episode("b", "Spesial B", "https://contoh/b", "", -1));
        java.util.List<OtakudesuAnime.Episode> urut = OtakudesuEpisodes.inStoryOrder(tanpaNomor);
        assertEquals(2, urut.size());
        assertEquals("Spesial A", urut.get(0).label);
        assertEquals("Spesial B", urut.get(1).label);
    }

    private static OtakudesuAnime.Episode episode(int nomor) {
        return new OtakudesuAnime.Episode("ep" + nomor, "Episode " + nomor,
                "https://otakudesu.blog/episode/contoh-episode-" + nomor + "-sub-indo/",
                "", nomor);
    }

}
