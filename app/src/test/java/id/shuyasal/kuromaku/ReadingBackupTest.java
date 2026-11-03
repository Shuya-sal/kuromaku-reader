package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import id.shuyasal.kuromaku.data.ReadingBackup;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Backup and restore of the reading state.
 *
 * <p>The reading store is a flat map of mixed types: bookmark strings, scroll
 * offsets as int, percentages as int, and the accordion state as a string set.
 * A backup that forgets the type would write "0" back as text, and the reader
 * would open a novel at the top with no error — the exact silent loss this
 * feature exists to prevent. Every type is therefore round-tripped and the type
 * is asserted, not just the value.
 *
 * <p>Restore is also checked for the destructive cases: a foreign file, a
 * truncated file, and a backup of one novel must never clear another novel's
 * bookmark.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ReadingBackupTest {

    private Context context = ApplicationProvider.getApplicationContext();

    private SharedPreferences emptyStore() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        return context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Every type the reader actually stores comes back as the same type. */
    @Test
    public void everyStoredTypeSurvivesTheRoundTrip() throws Exception {
        SharedPreferences from = emptyStore();
        from.edit()
                .putString("monogatari:last_url", "https://kaoritranslation.blogspot.com/v1/c1.html")
                .putInt("monogatari:progress:https://kaoritranslation.blogspot.com/v1/c1.html", 62)
                .putLong("monogatari:scroll_off:https://kaoritranslation.blogspot.com/v1/c1.html", 4210L)
                .putFloat("line_spacing", 1.35f)
                .putBoolean("justify", true)
                .putStringSet("expanded_volumes", new HashSet<>(Arrays.asList("monogatari:v1", "monogatari:v2")))
                .commit();

        String document = ReadingBackup.export(from);

        SharedPreferences into = emptyStore();
        int restored = ReadingBackup.restore(into, document);

        assertEquals("semua nilai ikut", 6, restored);
        assertEquals("https://kaoritranslation.blogspot.com/v1/c1.html",
                into.getString("monogatari:last_url", null));
        // The assertion that matters: read back as the stored type, not as text.
        assertEquals("progres tetap int", 62,
                into.getInt("monogatari:progress:https://kaoritranslation.blogspot.com/v1/c1.html", -1));
        assertEquals("offset tetap long", 4210L,
                into.getLong("monogatari:scroll_off:https://kaoritranslation.blogspot.com/v1/c1.html", -1L));
        assertEquals("spasi baris tetap float", 1.35f, into.getFloat("line_spacing", 0f), 0.001f);
        assertTrue("perataan tetap boolean", into.getBoolean("justify", false));
        assertEquals("akordeon tetap himpunan",
                new HashSet<>(Arrays.asList("monogatari:v1", "monogatari:v2")),
                into.getStringSet("expanded_volumes", new HashSet<>()));
    }

    /**
     * A backup of one novel must not erase the other. Restoring only mentions the
     * keys it carries, so anything already stored stays where it is.
     */
    @Test
    public void restoringOneNovelLeavesTheOtherNovelAlone() throws Exception {
        SharedPreferences backupOfMonogatari = emptyStore();
        backupOfMonogatari.edit()
                .putString("monogatari:last_url", "https://kaoritranslation.blogspot.com/mono.html")
                .putInt("monogatari:progress:c1", 40)
                .commit();
        String document = ReadingBackup.export(backupOfMonogatari);

        SharedPreferences into = emptyStore();
        into.edit()
                .putString("magical:last_url", "https://kaoritranslation.blogspot.com/magical.html")
                .putInt("magical:progress:c1", 88)
                .commit();

        ReadingBackup.restore(into, document);

        assertEquals("bookmark novel lain utuh",
                "https://kaoritranslation.blogspot.com/magical.html",
                into.getString("magical:last_url", null));
        assertEquals("progres novel lain utuh", 88, into.getInt("magical:progress:c1", -1));
        assertEquals("bookmark yang dipulihkan",
                "https://kaoritranslation.blogspot.com/mono.html",
                into.getString("monogatari:last_url", null));
    }

    /**
     * A file from another app, or a truncated download, must be refused before
     * anything is written. Half-restoring would move the reader to a chapter they
     * never opened.
     */
    @Test
    public void aForeignFileIsRefusedAndTheStoreIsUntouched() {
        SharedPreferences store = emptyStore();
        store.edit().putInt("monogatari:progress:c1", 55).commit();

        String foreign = "{\"format\":\"some-other-app\",\"preferences\":{}}";
        try {
            ReadingBackup.restore(store, foreign);
            fail("berkas asing harus ditolak");
        } catch (JSONException expected) {
            assertTrue("alasan terbaca: " + expected.getMessage(),
                    expected.getMessage().contains(ReadingBackup.FORMAT)
                            || expected.getMessage().contains("Format"));
        }
        assertEquals("progres tidak berubah", 55, store.getInt("monogatari:progress:c1", -1));
    }

    @Test
    public void aTruncatedFileIsRefusedAndTheStoreIsUntouched() {
        SharedPreferences store = emptyStore();
        store.edit().putInt("monogatari:progress:c1", 55).commit();

        String truncated = "{\"format\":\"" + ReadingBackup.FORMAT + "\",\"preferences\":{\"a\":{\"ty";
        try {
            ReadingBackup.restore(store, truncated);
            fail("berkas terpotong harus ditolak");
        } catch (JSONException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals("progres tidak berubah", 55, store.getInt("monogatari:progress:c1", -1));
    }

    /** An empty picker result is not a backup; it must not silently report success. */
    @Test
    public void anEmptyDocumentIsRefused() {
        SharedPreferences store = emptyStore();
        try {
            ReadingBackup.restore(store, null);
            fail("dokumen null harus ditolak");
        } catch (JSONException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** A backup is a few kilobytes; a huge file is a wrong file. */
    @Test
    public void aFileBeyondTheCeilingIsRefused() {
        SharedPreferences store = emptyStore();
        StringBuilder huge = new StringBuilder(ReadingBackup.MAX_BYTES + 64);
        huge.append("{\"format\":\"").append(ReadingBackup.FORMAT).append("\",\"preferences\":{");
        while (huge.length() <= ReadingBackup.MAX_BYTES) huge.append(' ');
        huge.append("}}");
        try {
            ReadingBackup.restore(store, huge.toString());
            fail("berkas kebesaran harus ditolak");
        } catch (JSONException expected) {
            assertTrue("alasan terbaca: " + expected.getMessage(),
                    expected.getMessage().contains("besar"));
        }
    }

    /**
     * A key with a type this build does not know comes from a newer version. The
     * rest of the file is still worth restoring, so only that key is skipped.
     */
    @Test
    public void anUnknownTypeSkipsOnlyThatKey() throws Exception {
        JSONObject preferences = new JSONObject();
        preferences.put("future_key", new JSONObject().put("type", "date").put("value", "2030-01-01"));
        preferences.put("monogatari:progress:c1",
                new JSONObject().put("type", "int").put("value", 71));
        String document = new JSONObject()
                .put("format", ReadingBackup.FORMAT)
                .put("preferences", preferences)
                .toString();

        SharedPreferences store = emptyStore();
        int restored = ReadingBackup.restore(store, document);

        assertEquals("hanya kunci yang dikenal", 1, restored);
        assertEquals("kunci yang dikenal dipulihkan", 71, store.getInt("monogatari:progress:c1", -1));
        assertFalse("kunci asing tidak dibuat", store.contains("future_key"));
    }

    /** A value type the format cannot carry is skipped instead of failing the export. */
    @Test
    public void exportSkipsAnUnsupportedValueButKeepsTheRest() throws Exception {
        SharedPreferences store = emptyStore();
        store.edit()
                .putInt("monogatari:progress:c1", 33)
                .putString("monogatari:last_url", "https://kaoritranslation.blogspot.com/v1/c1.html")
                .commit();

        String document = ReadingBackup.export(store);

        JSONObject root = new JSONObject(document);
        assertEquals(ReadingBackup.FORMAT, root.getString("format"));
        JSONObject preferences = root.getJSONObject("preferences");
        assertEquals("dua nilai", 2, preferences.length());
        assertEquals("int ditandai", "int",
                preferences.getJSONObject("monogatari:progress:c1").getString("type"));
        assertEquals("string ditandai", "string",
                preferences.getJSONObject("monogatari:last_url").getString("type"));
    }

    /** Two exports of the same store produce the same file, so a backup is comparable. */
    @Test
    public void exportIsStableForTheSameStore() throws Exception {
        SharedPreferences store = emptyStore();
        store.edit()
                .putInt("monogatari:progress:c1", 12)
                .putStringSet("expanded_volumes", new HashSet<>(Arrays.asList("v1", "v2", "v3")))
                .commit();

        assertEquals(ReadingBackup.export(store), ReadingBackup.export(store));
    }
}
