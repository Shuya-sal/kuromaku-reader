package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiStoreTest {
    private Context context;
    private ShinigamiStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("kuromaku_reader", Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new ShinigamiStore(context);
    }

    @Test
    public void theStateLivesInItsOwnFile() {
        store.setProgress("chapter-1", 40);
        SharedPreferences novels =
                context.getSharedPreferences("kuromaku_reader", Context.MODE_PRIVATE);
        assertFalse("progres manga tidak boleh masuk ke store novel",
                novels.contains("progress:chapter-1"));
        assertEquals(40, store.progress("chapter-1"));
    }

    @Test
    public void theProgressOfOneChapterIsNotTheProgressOfAnother() {
        store.setProgress("chapter-1", 40);
        store.setProgress("chapter-2", 90);
        assertEquals(40, store.progress("chapter-1"));
        assertEquals(90, store.progress("chapter-2"));
        assertEquals("chapter yang belum dibaca harus nol", 0, store.progress("chapter-3"));
    }

    @Test
    public void theProgressIsClampedToWhatCanBeShown() {
        store.setProgress("chapter-1", 250);
        assertEquals(100, store.progress("chapter-1"));
        store.setProgress("chapter-2", -20);
        assertEquals(0, store.progress("chapter-2"));
    }

    @Test
    public void aFavouriteIsAddedAndRemovedAgain() {
        assertFalse(store.isFavorite("manga-1"));
        assertTrue(store.toggleFavorite("manga-1"));
        assertTrue(store.isFavorite("manga-1"));
        assertFalse(store.toggleFavorite("manga-1"));
        assertFalse(store.isFavorite("manga-1"));
    }

    @Test
    public void theHistoryKeepsTheChapterThatWasRead() {
        store.recordRead("manga-1", "Judul Satu", "https://cover/1.jpg", "chapter-1", 1);
        store.recordRead("manga-2", "Judul Dua", "https://cover/2.jpg", "chapter-7", 7);

        List<ShinigamiStore.HistoryEntry> history = store.history();
        assertEquals(2, history.size());
        assertEquals("manga-2", history.get(0).mangaId);
        assertEquals("chapter-7", history.get(0).chapterId);
        assertEquals(7d, history.get(0).chapterNumber, 0.0001d);
        assertEquals("manga-1", history.get(1).mangaId);
    }

    @Test
    public void readingTheSameChapterAgainDoesNotDuplicateIt() {
        store.recordRead("manga-1", "Judul Satu", "https://cover/1.jpg", "chapter-1", 1);
        store.recordRead("manga-2", "Judul Dua", "https://cover/2.jpg", "chapter-7", 7);
        store.recordRead("manga-1", "Judul Satu", "https://cover/1.jpg", "chapter-2", 2);

        List<ShinigamiStore.HistoryEntry> history = store.history();
        assertEquals("entri yang sama tidak boleh terduplikasi", 2, history.size());
        assertEquals("manga-1", history.get(0).mangaId);
        assertEquals("chapter-2", history.get(0).chapterId);
    }

    @Test
    public void theTitleAndCoverOfAMangaAreKept() {
        store.setMangaMeta("manga-1", "Judul Satu", "https://cover/1.jpg");
        assertEquals("Judul Satu", store.mangaTitle("manga-1"));
        assertEquals("https://cover/1.jpg", store.mangaCover("manga-1"));
    }

    @Test
    public void theLastChapterIsRememberedPerManga() {
        store.setLastChapter("manga-1", "chapter-3", 3);
        store.setLastChapter("manga-2", "chapter-9", 9);
        assertEquals("chapter-3", store.lastChapter("manga-1"));
        assertEquals("chapter-9", store.lastChapter("manga-2"));
        assertEquals("manga-2", store.lastManga());
    }

    @Test
    public void aDecimalChapterNumberSurvivesTheHistory() {
        store.recordRead("manga-1", "Judul Satu", "https://cover/1.jpg", "chapter-a", 166.6923d);
        assertEquals(166.6923d, store.history().get(0).chapterNumber, 0.00001d);
    }

    @Test
    public void twoChaptersWithTheSameNumberKeepSeparateProgress() {
        store.setProgress("chapter-a", 30);
        store.setProgress("chapter-b", 80);
        store.setPage("chapter-a", 4);
        store.setPage("chapter-b", 11);
        assertEquals(30, store.progress("chapter-a"));
        assertEquals(80, store.progress("chapter-b"));
        assertEquals(4, store.page("chapter-a"));
        assertEquals(11, store.page("chapter-b"));
    }

    @Test
    public void aCorruptHistoryIsReadAsEmptyInsteadOfCrashing() {
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().putString("history", "{bukan json").commit();
        assertTrue(store.history().isEmpty());
    }

    @Test
    public void favouritesAreRemovedWithoutTouchingTheHistory() {
        store.toggleFavorite("manga-1");
        store.recordRead("manga-1", "Judul Satu", "", "chapter-1", 1);
        store.removeFavorite("manga-1");
        assertFalse(store.isFavorite("manga-1"));
        assertEquals(1, store.history().size());
    }
}
