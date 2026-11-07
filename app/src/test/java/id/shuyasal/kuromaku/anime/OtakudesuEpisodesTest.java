package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
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
}