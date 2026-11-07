package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import id.shuyasal.kuromaku.shinigami.ShinigamiStore;
import id.shuyasal.kuromaku.ui.Prefs;

/** Backup satu berkas untuk Novel, Manga Shinigami, dan Anime Otakudesu. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuBackupTest {

    private SharedPreferences novels;
    private SharedPreferences manga;
    private SharedPreferences anime;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        novels = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
        manga = context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE);
        anime = context.getSharedPreferences(OtakudesuStore.FILE_NAME, Context.MODE_PRIVATE);
        novels.edit().clear().commit();
        manga.edit().clear().commit();
        anime.edit().clear().commit();
    }

    @Test public void tigaSumberMasukSatuBerkas() throws Exception {
        novels.edit().putString("monogatari:last_url", "https://contoh.test/a.html").commit();
        manga.edit().putInt("progress:chapter-1", 42).commit();
        anime.edit().putString("history_v2", "[{\"anime_id\":\"a\"}]").commit();

        String document = ReadingBackup.export(novels, manga, anime);
        JSONObject root = new JSONObject(document);
        assertEquals("Format tidak berubah, file lama tetap terbaca",
                ReadingBackup.FORMAT, root.getString("format"));
        assertTrue(root.has("anime_preferences"));

        novels.edit().clear().commit();
        manga.edit().clear().commit();
        anime.edit().clear().commit();

        int restored = ReadingBackup.restore(novels, document, manga, anime);
        assertTrue(restored >= 3);
        assertEquals("https://contoh.test/a.html", novels.getString("monogatari:last_url", ""));
        assertEquals(42, manga.getInt("progress:chapter-1", 0));
        assertEquals("[{\"anime_id\":\"a\"}]", anime.getString("history_v2", ""));
    }

    @Test public void berkasLamaTanpaBagianAnimeTetapDipulihkan() throws Exception {
        // Dokumen yang dibuat aplikasi sebelum fitur anime ada.
        novels.edit().putString("monogatari:last_url", "https://contoh.test/a.html").commit();
        manga.edit().putInt("progress:chapter-1", 7).commit();
        String lama = ReadingBackup.export(novels, manga);

        novels.edit().clear().commit();
        manga.edit().clear().commit();

        int restored = ReadingBackup.restore(novels, lama, manga, anime);
        assertEquals(2, restored);
        assertEquals("https://contoh.test/a.html", novels.getString("monogatari:last_url", ""));
        assertEquals(7, manga.getInt("progress:chapter-1", 0));
        assertTrue("Tidak ada data anime yang ditulis dari berkas lama", anime.getAll().isEmpty());
    }

    @Test public void restoreAnimeTidakMenghapusDataLainYangSudahAda() throws Exception {
        novels.edit().putString("monogatari:last_url", "lama").commit();
        anime.edit().putString("favorites_v2", "[{\"id\":\"a\"}]").commit();
        String document = ReadingBackup.export(novels, manga, anime);

        // Data pengguna baru yang tidak ada di berkas harus tetap utuh.
        anime.edit().putString("history_v2", "[{\"anime_id\":\"b\"}]").commit();
        ReadingBackup.restore(novels, document, manga, anime);

        assertEquals("[{\"id\":\"a\"}]", anime.getString("favorites_v2", ""));
        assertEquals("[{\"anime_id\":\"b\"}]", anime.getString("history_v2", ""));
    }

    @Test public void formatAsingDitolakTanpaMenyentuhData() {
        anime.edit().putString("favorites_v2", "[{\"id\":\"a\"}]").commit();
        try {
            ReadingBackup.restore(novels, "{\"format\":\"lain-v9\"}", manga, anime);
            org.junit.Assert.fail("Format asing harus ditolak");
        } catch (Exception expected) {
            assertEquals("[{\"id\":\"a\"}]", anime.getString("favorites_v2", ""));
        }
    }

    @Test public void animTanpaBagianAnimeTidakMenggagalkanExport() throws Exception {
        anime.edit().clear().commit();
        String document = ReadingBackup.export(novels, manga, anime);
        JSONObject root = new JSONObject(document);
        assertFalse("Bagian kosong tidak perlu ditulis", root.has("anime_preferences"));
    }
}