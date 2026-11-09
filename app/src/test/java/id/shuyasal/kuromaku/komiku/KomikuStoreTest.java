package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/** Riwayat, progres, dan favorit Manga Server 3 tidak boleh saling merusak. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KomikuStoreTest {

    private KomikuStore store;

    @Before public void siapkan() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new KomikuStore(context);
    }

    @Test public void riwayatMenyimpanChapterTerakhir() {
        store.recordChapter("black-clover", "Black Clover", "https://img.komiku.org/a.jpg",
                "https://komiku.org/black-clover-chapter-1/", "Chapter 1", 1, 0.4);
        store.recordChapter("black-clover", "Black Clover", "https://img.komiku.org/a.jpg",
                "https://komiku.org/black-clover-chapter-2/", "Chapter 2", 2, 0.1);

        List<KomikuStore.Entry> history = store.history();
        assertEquals("satu baris per judul", 1, history.size());
        assertEquals("chapter terbaru menang", "Chapter 2", history.get(0).chapterLabel);
        assertEquals(2.0, history.get(0).number, 0.001);
    }

    @Test public void riwayatUrutDariYangTerakhirDibaca() {
        store.recordChapter("a", "A", "", "https://komiku.org/a-chapter-1/", "Chapter 1", 1, 0);
        store.recordChapter("b", "B", "", "https://komiku.org/b-chapter-1/", "Chapter 1", 1, 0);

        List<KomikuStore.Entry> history = store.history();
        assertEquals("b", history.get(0).mangaId);
        assertEquals("a", history.get(1).mangaId);
        assertNotNull(store.lastFor("a"));
        assertNull(store.lastFor("tidak-ada"));
    }

    @Test public void progresPerChapterTanpaSalingMenimpa() {
        store.setProgress("https://komiku.org/x-chapter-1/", 0.25);
        store.setProgress("https://komiku.org/x-chapter-2/", 0.8);

        assertEquals(0.25, store.progress("https://komiku.org/x-chapter-1/"), 0.001);
        assertEquals(0.8, store.progress("https://komiku.org/x-chapter-2/"), 0.001);
        assertEquals("chapter yang belum dibaca", 0.0,
                store.progress("https://komiku.org/x-chapter-3/"), 0.001);
    }

    @Test public void progresDibatasiAntaraNolDanSatu() {
        store.setProgress("https://komiku.org/y-chapter-1/", 3.5);
        assertEquals(1.0, store.progress("https://komiku.org/y-chapter-1/"), 0.001);

        store.setProgress("https://komiku.org/y-chapter-1/", -2);
        assertEquals(0.0, store.progress("https://komiku.org/y-chapter-1/"), 0.001);
    }

    @Test public void favoritBisaDitambahDanDilepas() {
        assertFalse(store.isFavorite("black-clover"));

        assertTrue(store.toggleFavorite("black-clover", "Black Clover",
                "https://img.komiku.org/a.jpg", "https://komiku.org/manga/black-clover/"));
        assertTrue(store.isFavorite("black-clover"));
        assertEquals(1, store.favorites().size());
        assertEquals("Black Clover", store.favorites().get(0).title);

        assertFalse(store.toggleFavorite("black-clover", "Black Clover",
                "https://img.komiku.org/a.jpg", "https://komiku.org/manga/black-clover/"));
        assertFalse(store.isFavorite("black-clover"));
        assertTrue(store.favorites().isEmpty());
    }

    @Test public void riwayatBisaDihapusTanpaMenyentuhFavorit() {
        store.recordChapter("a", "A", "", "https://komiku.org/a-chapter-1/", "Chapter 1", 1, 0);
        store.toggleFavorite("a", "A", "", "https://komiku.org/manga/a/");

        store.clearHistory();

        assertTrue(store.history().isEmpty());
        assertTrue("favorit tetap ada", store.isFavorite("a"));
    }
}