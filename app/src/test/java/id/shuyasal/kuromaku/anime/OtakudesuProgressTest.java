package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Posisi menonton yang bertahan setelah aplikasi ditutup dan dibuka lagi. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuProgressTest {

    private Context context;
    private OtakudesuStore store;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                OtakudesuStore.FILE_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new OtakudesuStore(context);
    }

    @Test public void posisiTersimpanDanTerbacaLagi() {
        store.saveProgress("fxskc", "fxskc-episode-1-sub-indo", 420_000L, 1_440_000L, false);

        // Store baru membaca berkas yang sama, seperti setelah aplikasi ditutup.
        OtakudesuStore reopened = new OtakudesuStore(context);
        OtakudesuStore.Progress progress = reopened.progress("fxskc",
                "fxskc-episode-1-sub-indo");
        assertNotNull(progress);
        assertEquals(420_000L, progress.positionMs);
        assertEquals(1_440_000L, progress.durationMs);
        assertFalse(progress.finished);
        assertEquals(29, progress.percent());
        assertEquals(420_000L, progress.resumePositionMs());
    }

    @Test public void posisiTidakMundurSaatDisimpanLebihKecil() {
        store.saveProgress("a", "ep1", 600_000L, 1_000_000L, false);
        store.saveProgress("a", "ep1", 120_000L, 1_000_000L, false);
        assertEquals(600_000L, store.progress("a", "ep1").positionMs);
    }

    @Test public void durasiTidakHilangSaatPosisiDiperbarui() {
        store.saveProgress("a", "ep1", 10_000L, 900_000L, false);
        store.saveProgress("a", "ep1", 20_000L, 0L, false);
        assertEquals(900_000L, store.progress("a", "ep1").durationMs);
    }

    @Test public void mendekatiAkhirDianggapSelesai() {
        store.saveProgress("a", "ep1", 1_435_000L, 1_440_000L, false);
        OtakudesuStore.Progress progress = store.progress("a", "ep1");
        assertTrue(progress.finished);
        assertEquals("Selesai berarti mulai dari awal", 0L, progress.resumePositionMs());
    }

    @Test public void posisiTerlaluKecilTidakDilanjutkan() {
        store.saveProgress("a", "ep2", 2_000L, 1_440_000L, false);
        assertEquals(0L, store.progress("a", "ep2").resumePositionMs());
    }

    @Test public void tiapEpisodePunyaPosisiSendiri() {
        store.saveProgress("a", "ep1", 100_000L, 1_000_000L, false);
        store.saveProgress("a", "ep2", 200_000L, 1_000_000L, false);

        assertEquals(100_000L, store.progress("a", "ep1").positionMs);
        assertEquals(200_000L, store.progress("a", "ep2").positionMs);
        assertEquals(2, store.progressKeys("a").size());

        // Episode yang belum pernah diputar tidak punya posisi.
        assertNull(store.progress("a", "ep3"));
        assertNull(store.progress("b", "ep1"));
    }

    @Test public void progresTidakMundurSaatEpisodeLamaDibukaLagi() {
        store.saveProgress("a", "ep8", 900_000L, 1_440_000L, false);
        store.recordWatched("a", "Anime A", "", "Episode 8",
                "https://otakudesu.blog/episode/a-8/", 8, 24);
        store.recordWatched("a", "Anime A", "", "Episode 3",
                "https://otakudesu.blog/episode/a-3/", 3, 24);

        OtakudesuStore.HistoryEntry entry = store.historyFor("a");
        assertNotNull(entry);
        assertEquals("Progres episode tidak boleh mundur", 8, entry.number);
    }

    @Test public void persenAmanTanpaDurasi() {
        store.saveProgress("a", "ep1", 5_000L, 0L, false);
        assertEquals(-1, store.progress("a", "ep1").percent());
    }

    @Test public void posisiTidakMenimpaFavoritDanRiwayat() {
        store.toggleFavorite("a", "Anime A", "https://g/1.jpg");
        store.recordWatched("a", "Anime A", "https://g/1.jpg", "Episode 1",
                "https://otakudesu.blog/episode/a-1/", 1, 12);
        store.saveProgress("a", "a-1", 60_000L, 1_440_000L, false);

        assertTrue(store.isFavorite("a"));
        assertEquals(1, store.history().size());
        assertEquals(60_000L, store.progress("a", "a-1").positionMs);
    }
}