package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.data.ReadingBackup;
import id.shuyasal.kuromaku.ui.Prefs;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiBackupTest {
    private Context context;
    private SharedPreferences novels;
    private SharedPreferences manga;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        novels = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
        manga = context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE);
        novels.edit().clear().commit();
        manga.edit().clear().commit();
    }

    @Test
    public void theProgressOfBothSourcesTravelsInOneFile() throws Exception {
        novels.edit().putString("monogatari:last_url", "https://contoh.test/a.html").commit();
        manga.edit().putInt("progress:chapter-1", 42).commit();

        String document = ReadingBackup.export(novels, manga);

        novels.edit().clear().commit();
        manga.edit().clear().commit();

        int restored = ReadingBackup.restore(novels, document, manga);
        assertEquals(2, restored);
        assertEquals("https://contoh.test/a.html",
                novels.getString("monogatari:last_url", ""));
        assertEquals(42, manga.getInt("progress:chapter-1", 0));
    }

    @Test
    public void aBackupFromBeforeThisSourceIsStillRestored() throws Exception {
        novels.edit().putString("monogatari:last_url", "https://contoh.test/a.html").commit();
        String oldDocument = ReadingBackup.export(novels);

        novels.edit().clear().commit();
        manga.edit().clear().commit();

        int restored = ReadingBackup.restore(novels, oldDocument, manga);
        assertEquals(1, restored);
        assertEquals("https://contoh.test/a.html",
                novels.getString("monogatari:last_url", ""));
        assertEquals("manga tidak boleh muncul dari backup lama",
                0, manga.getInt("progress:chapter-1", 0));
    }

    @Test
    public void theFormatOfTheDocumentDidNotChange() throws Exception {
        String document = ReadingBackup.export(novels, manga);
        JSONObject root = new JSONObject(document);
        assertEquals(ReadingBackup.FORMAT, root.getString("format"));
        assertTrue("bagian novel harus tetap ada", root.has("preferences"));
    }

    @Test
    public void theTwoStoresDoNotBleedIntoEachOther() throws Exception {
        manga.edit().putString("history", "[]").commit();
        novels.edit().putInt("progress:https://contoh.test/a.html", 55).commit();

        String document = ReadingBackup.export(novels, manga);

        novels.edit().clear().commit();
        manga.edit().clear().commit();
        ReadingBackup.restore(novels, document, manga);

        assertEquals(55, novels.getInt("progress:https://contoh.test/a.html", 0));
        assertEquals("sebab manga tidak boleh masuk ke store novel",
                0, novels.getInt("progress:chapter-1", 0));
        assertEquals("[]", manga.getString("history", ""));
    }

    @Test
    public void aBrokenMangaSectionLeavesBothStoresUntouched() throws Exception {
        novels.edit().putInt("progress:c1", 41).commit();
        manga.edit().putInt("progress:chapter-1", 12).commit();
        String broken = "{\"format\":\"" + ReadingBackup.FORMAT + "\","
                + "\"preferences\":{\"progress:c1\":{\"type\":\"int\",\"value\":99}},"
                + "\"shinigami_preferences\":{\"progress:chapter-1\":{\"type\":\"int\",\"value\":\"bukan angka\"}}}";
        try {
            ReadingBackup.restore(novels, broken, manga);
            org.junit.Assert.fail("backup rusak harus ditolak");
        } catch (org.json.JSONException expected) {
            assertEquals(41, novels.getInt("progress:c1", 0));
            assertEquals(12, manga.getInt("progress:chapter-1", 0));
        }
    }

    @Test
    public void favouritesAndHistoryOfTheMangaSourceAreRestored() throws Exception {
        ShinigamiStore store = new ShinigamiStore(context);
        store.toggleFavorite("manga-1");
        store.recordRead("manga-1", "Judul", "https://assets.shngm.id/c.jpg", "chapter-1", 166.6923d);
        store.setPage("chapter-1", 7);
        String document = ReadingBackup.export(novels, manga);
        manga.edit().clear().commit();
        ReadingBackup.restore(novels, document, manga);
        ShinigamiStore restored = new ShinigamiStore(context);
        assertTrue(restored.isFavorite("manga-1"));
        assertEquals(1, restored.history().size());
        assertEquals(166.6923d, restored.history().get(0).chapterNumber, 0.00001d);
        assertEquals(7, restored.page("chapter-1"));
    }

    @Test
    public void anEmptyMangaStoreWritesTheOldShapeExactly() throws Exception {
        novels.edit().putInt("progress:c1", 5).commit();
        JSONObject root = new JSONObject(ReadingBackup.export(novels, manga));
        assertTrue(!root.has("shinigami_preferences"));
        assertEquals(new JSONObject(ReadingBackup.export(novels)).toString(), root.toString());
    }
}
