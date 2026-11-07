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

import java.util.List;

/** Favorit, riwayat, dan progres episode yang bertahan di antara pembukaan aplikasi. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuStoreTest {

    private Context context;
    private SharedPreferences prefs;
    private OtakudesuStore store;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(OtakudesuStore.FILE_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new OtakudesuStore(context);
    }

    @Test public void favoritMenyimpanJudulDanPoster() {
        assertTrue(store.toggleFavorite("uchi-otouto", "Uchi no Otouto", "https://gambar/1.jpg"));
        assertTrue(store.isFavorite("uchi-otouto"));

        List<OtakudesuStore.Favorite> entries = store.favoriteEntries();
        assertEquals(1, entries.size());
        assertEquals("Uchi no Otouto", entries.get(0).title);
        assertEquals("https://gambar/1.jpg", entries.get(0).posterUrl);

        assertFalse(store.toggleFavorite("uchi-otouto"));
        assertFalse(store.isFavorite("uchi-otouto"));
        assertTrue(store.favoriteEntries().isEmpty());
    }

    @Test public void favoritLamaTanpaMetadataTetapTerbaca() {
        // Format versi sebelumnya: hanya himpunan ID.
        prefs.edit().putStringSet("favorites",
                new java.util.LinkedHashSet<>(java.util.Arrays.asList("naruto", "bleach")))
                .commit();

        List<OtakudesuStore.Favorite> entries = store.favoriteEntries();
        assertEquals(2, entries.size());
        assertTrue(store.isFavorite("naruto"));
        assertTrue(store.isFavorite("bleach"));
    }

    @Test public void menambahFavoritBaruTidakMenghapusFavoritLama() {
        store.toggleFavorite("a", "A", "");
        store.toggleFavorite("b", "B", "");
        assertEquals(2, store.favoriteEntries().size());
        // Menghapus satu tidak menyentuh yang lain.
        store.toggleFavorite("a");
        assertEquals(1, store.favoriteEntries().size());
        assertTrue(store.isFavorite("b"));
    }

    @Test public void riwayatSatuBarisPerAnime() {
        store.recordWatched("a", "Anime A", "https://g/1.jpg", "Episode 3",
                "https://otakudesu.blog/episode/a-ep-3/", 3, 12);
        store.recordWatched("b", "Anime B", "https://g/2.jpg", "Episode 1",
                "https://otakudesu.blog/episode/b-ep-1/", 1, 24);
        store.recordWatched("a", "Anime A", "https://g/1.jpg", "Episode 4",
                "https://otakudesu.blog/episode/a-ep-4/", 4, 12);

        List<OtakudesuStore.HistoryEntry> history = store.history();
        assertEquals(2, history.size());
        assertEquals("a", history.get(0).animeId);
        assertEquals("Episode 4", history.get(0).episodeLabel);

        OtakudesuStore.HistoryEntry a = store.historyFor("a");
        assertNotNull(a);
        assertEquals(4, a.number);
        assertEquals(12, a.total);
        assertEquals(33, a.progressPercent());
        assertNull(store.historyFor("tidak-ada"));
    }

    @Test public void progresTidakMundurSaatEpisodeLamaDibukaLagi() {
        store.recordWatched("a", "Anime A", "", "Episode 8", "https://otakudesu.blog/episode/a-8/", 8, 12);
        store.recordWatched("a", "Anime A", "", "Episode 2", "https://otakudesu.blog/episode/a-2/", 2, 12);

        OtakudesuStore.HistoryEntry entry = store.historyFor("a");
        assertNotNull(entry);
        assertEquals("Label mengikuti episode terakhir", "Episode 2", entry.episodeLabel);
        assertEquals("Nomor progres tidak boleh mundur", 8, entry.number);
    }

    @Test public void tandaSudahDibukaBertahan() {
        assertFalse(store.isWatched("a", "a-ep-1"));
        store.markWatched("a", "a-ep-1");
        store.markWatched("a", "a-ep-1");
        assertTrue(store.isWatched("a", "a-ep-1"));
        assertEquals(1, store.watched("a").size());
        // Anime lain tidak ikut bertanda.
        assertFalse(store.isWatched("b", "a-ep-1"));
    }

    @Test public void menontonMenandaiEpisodeDanMengisiRiwayat() {
        store.recordWatched("a", "Anime A", "", "Episode 5",
                "https://otakudesu.blog/episode/a-5/", 5, 10);
        assertTrue(store.isWatched("a", "https://otakudesu.blog/episode/a-5/"));
        assertEquals(50, store.historyFor("a").progressPercent());
    }

    @Test public void persenAmanUntukTotalTidakDiketahui() {
        assertEquals(-1, OtakudesuStore.percent(3, 0));
        assertEquals(-1, OtakudesuStore.percent(-1, 10));
        assertEquals(0, OtakudesuStore.percent(0, 10));
        assertEquals(100, OtakudesuStore.percent(10, 10));
        assertEquals(100, OtakudesuStore.percent(40, 10));
    }

    @Test public void penyimpananBerbedaDariShinigamiDanNovel() {
        assertEquals("kuromaku_otakudesu", OtakudesuStore.FILE_NAME);
        assertFalse(OtakudesuStore.FILE_NAME.equals("kuromaku_shinigami"));
    }
}