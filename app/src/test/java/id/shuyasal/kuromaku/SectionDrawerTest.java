package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import id.shuyasal.kuromaku.data.ReadingBackup;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The section drawer and the backup it carries, driven through the real buttons.
 *
 * <p>Everything here goes through a control the reader can tap: the menu button in
 * the toolbar, then the item in the drawer. A test that called an activity method
 * directly would keep passing after the item stopped being clickable, which is the
 * regression this feature is most likely to suffer — a new entry added to a panel
 * and never wired up.
 *
 * <p>The file round trip is checked end to end: the export writes a real document
 * to a registered stream, and the restore reads the written bytes back through
 * {@code onActivityResult} and lands them in the store.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class SectionDrawerTest {

    private Context context = ApplicationProvider.getApplicationContext();

    private MainActivity open(Intent intent) {
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return host;
    }

    private MainActivity open() {
        return open(new Intent(context, MainActivity.class));
    }

    /** Drives the toolbar button and returns the drawer's own window view. */
    private View openDrawerDecor(MainActivity host) {
        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        assertNotNull("tombol menu bagian harus ada", menu);
        menu.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("drawer harus terbuka", dialog);
        assertNotNull("drawer harus punya window", dialog.getWindow());
        return dialog.getWindow().getDecorView();
    }

    /**
     * Same as {@link #openDrawerDecor}, but without letting the main looper run.
     *
     * <p>Draining the looper also finishes every pending animation, so the drawer's
     * starting position can only be observed before that happens. The click itself is
     * synchronous, so the dialog exists at this point.
     */
    private View openDrawerDecorBeforeIdle(MainActivity host) {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        assertNotNull("tombol menu bagian harus ada", menu);
        menu.performClick();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("drawer harus terbuka", dialog);
        assertNotNull("drawer harus punya window", dialog.getWindow());
        return dialog.getWindow().getDecorView();
    }

    /** Opens the drawer by tapping the toolbar button, and returns the dialog panel. */
    private LinearLayout openDrawer(MainActivity host) {
        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        assertNotNull("tombol menu bagian harus ada", menu);
        menu.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("drawer harus terbuka", dialog);
        assertTrue("drawer harus tampil", dialog.isShowing());
        View root = dialog.findViewById(android.R.id.content);
        assertNotNull(root);
        return (LinearLayout) ((ViewGroup) root).getChildAt(0);
    }

    private View item(LinearLayout panel, String tag) {
        View found = panel.findViewWithTag(tag);
        assertNotNull("item drawer '" + tag + "' harus ada", found);
        return found;
    }

    /** The toolbar button opens a drawer, and the drawer offers every section. */
    @Test
    public void theMenuButtonOpensADrawerWithEverySection() {
        MainActivity host = open();
        LinearLayout panel = openDrawer(host);

        View novel = item(panel, "drawer_novel");
        View voratoon = item(panel, "drawer_manga_voratoon");
        View shinigami = item(panel, "drawer_manga_shinigami");
        View backup = item(panel, "drawer_backup");
        View restore = item(panel, "drawer_restore");

        assertEquals("Novel  ✓", ((TextView) novel).getText().toString());
        assertEquals("Manga Voratoon", ((TextView) voratoon).getText().toString());
        assertEquals("Manga Shinigami", ((TextView) shinigami).getText().toString());
        assertEquals("Backup progres membaca", ((TextView) backup).getText().toString());
        assertEquals("Restore progres membaca", ((TextView) restore).getText().toString());
    }

    /**
     * An item without a click listener looks the same as a working one. Tapping the
     * backup entry must start the document picker, not just close the drawer.
     */
    @Test
    public void tappingBackupStartsTheDocumentPicker() {
        MainActivity host = open();
        LinearLayout panel = openDrawer(host);

        item(panel, "drawer_backup").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        ShadowActivity.IntentForResult requested = Shadows.shadowOf(host).getNextStartedActivityForResult();
        assertNotNull("pemilih dokumen harus dibuka", requested);
        Intent started = requested.intent;
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.getAction());
        assertEquals("application/json", started.getType());
        // The request code decides which branch of onActivityResult runs: with the
        // wrong one a saved backup would be read back as a restore.
        assertEquals(MainActivity.REQUEST_BACKUP, requested.requestCode);
        assertTrue("nama berkas disarankan",
                started.getStringExtra(Intent.EXTRA_TITLE) != null
                        && started.getStringExtra(Intent.EXTRA_TITLE).endsWith(".json"));
    }

    /** The restore entry opens a picker too, and it is a different request. */
    @Test
    public void tappingRestoreStartsTheFilePicker() {
        MainActivity host = open();
        LinearLayout panel = openDrawer(host);

        item(panel, "drawer_restore").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        ShadowActivity.IntentForResult requested = Shadows.shadowOf(host).getNextStartedActivityForResult();
        assertNotNull("pemilih berkas harus dibuka", requested);
        Intent started = requested.intent;
        assertEquals("kode permintaan restore", MainActivity.REQUEST_RESTORE, requested.requestCode);
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.getAction());
        assertEquals("application/json", started.getType());
        assertTrue("hanya berkas yang bisa dibuka",
                started.getCategories().contains(Intent.CATEGORY_OPENABLE));
    }

    /**
     * Export writes a document the restore understands. The store keeps a real
     * progress value, so a backup that quietly wrote an empty file fails here.
     */
    @Test
    public void theExportedDocumentCarriesTheReadingProgress() throws Exception {
        SharedPreferences store = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        store.edit()
                .putString("monogatari:last_url", "https://kaoritranslation.blogspot.com/mono.html")
                .putInt("monogatari:progress:https://kaoritranslation.blogspot.com/mono.html", 64)
                .commit();

        MainActivity host = open();
        LinearLayout panel = openDrawer(host);
        item(panel, "drawer_backup").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Uri target = Uri.parse("content://test/backup.json");
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        ShadowContentResolver content = Shadows.shadowOf(context.getContentResolver());
        content.registerOutputStream(target, written);

        Intent result = new Intent().setData(target);
        host.onActivityResult(MainActivity.REQUEST_BACKUP, Activity.RESULT_OK, result);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        String document = written.toString(StandardCharsets.UTF_8.name());
        assertTrue("berkas tidak kosong", document.length() > 0);
        JSONObject root = new JSONObject(document);
        assertEquals(ReadingBackup.FORMAT, root.getString("format"));
        JSONObject saved = root.getJSONObject("preferences")
                .getJSONObject("monogatari:progress:https://kaoritranslation.blogspot.com/mono.html");
        assertEquals("titik baca ikut", 64, saved.getInt("value"));
        assertTrue("pesan berhasil", ShadowToast.getTextOfLatestToast().contains("Backup"));
    }

    /**
     * The document written by export, read back through the real restore path,
     * puts the bookmark back. This is the whole feature in one test.
     */
    @Test
    public void aWrittenBackupRestoresIntoAFreshStore() throws Exception {
        SharedPreferences store = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        store.edit()
                .putString("monogatari:last_url", "https://kaoritranslation.blogspot.com/mono.html")
                .putInt("monogatari:progress:c1", 77)
                .commit();

        MainActivity host = open();
        String document = ReadingBackup.export(store);

        // Wipe the progress as if the app had been reinstalled.
        store.edit().clear().commit();
        assertEquals("progres kosong", -1, store.getInt("monogatari:progress:c1", -1));

        Uri source = Uri.parse("content://test/restore.json");
        ShadowContentResolver content = Shadows.shadowOf(context.getContentResolver());
        content.registerInputStream(source,
                new java.io.ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));

        host.onActivityResult(MainActivity.REQUEST_RESTORE, Activity.RESULT_OK,
                new Intent().setData(source));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("bookmark kembali", "https://kaoritranslation.blogspot.com/mono.html",
                store.getString("monogatari:last_url", null));
        assertEquals("progres kembali", 77, store.getInt("monogatari:progress:c1", -1));
        assertTrue("jumlah dilaporkan", ShadowToast.getTextOfLatestToast().contains("2"));
    }

    /**
     * A cancelled picker must not be treated as an empty backup: returning without
     * a file leaves the store exactly as it was and says nothing.
     */
    @Test
    public void aCancelledPickerLeavesTheProgressAlone() {
        SharedPreferences store = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        store.edit().putInt("monogatari:progress:c1", 41).commit();

        MainActivity host = open();
        host.onActivityResult(MainActivity.REQUEST_RESTORE, Activity.RESULT_CANCELED, null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("progres utuh", 41, store.getInt("monogatari:progress:c1", -1));
    }

    /**
     * A picker that returns a file this app cannot read must say so. Silence here
     * would look like a successful restore.
     */
    @Test
    public void aFileThatCannotBeOpenedIsReported() {
        SharedPreferences store = context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        store.edit().putInt("monogatari:progress:c1", 41).commit();

        MainActivity host = open();
        // No stream registered for this Uri: openInputStream returns null.
        Uri source = Uri.parse("content://test/missing.json");
        host.onActivityResult(MainActivity.REQUEST_RESTORE, Activity.RESULT_OK,
                new Intent().setData(source));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        String toast = ShadowToast.getTextOfLatestToast();
        assertNotNull("harus ada pesan", toast);
        assertTrue("pesan menyebut gagal: " + toast, toast.startsWith("Gagal"));
        assertEquals("progres utuh", 41, store.getInt("monogatari:progress:c1", -1));
    }

    // ------------------------------------------------------------------- motion

    /**
     * The drawer slides in, starting off screen and ending at rest.
     *
     * <p>A drawer that starts at zero never appears to open; one that is left
     * off screen is invisible. Both are single-character mistakes, so the start
     * position and the rest position are checked separately. The duration check
     * reads the animator the code actually started: a test of the constant alone
     * stays green when the call site hardcodes a different number.
     */
    @Test
    public void theDrawerSlidesInFromTheLeftEdge() {
        android.provider.Settings.Global.putFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        MainActivity host = open();

        View decor = openDrawerDecorBeforeIdle(host);
        // The animation has been started but not run: the panel is still off screen.
        assertTrue("drawer harus mulai di luar layar, dapat " + decor.getTranslationX(),
                decor.getTranslationX() < 0f);

        // `view.animate()` hands back the same animator the drawer just configured,
        // so this reads the duration the call site really used. Robolectric runs the
        // animation to completion, so a mid-flight position cannot be observed; the
        // configured duration is the honest thing to assert.
        long duration = decor.animate().getDuration();
        assertEquals("durasi geser drawer harus Motion.DURATION_MS",
                (long) id.shuyasal.kuromaku.ui.Motion.DURATION_MS, duration);
        assertTrue("durasi " + duration + " ms di luar jendela 180-250 ms",
                duration >= 180 && duration <= 250);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                java.time.Duration.ofMillis(id.shuyasal.kuromaku.ui.Motion.DURATION_MS + 60L));
        assertEquals("drawer harus berhenti di posisi diam", 0f, decor.getTranslationX(), 0.5f);
    }

    /**
     * With Android's "Remove animations" on, the drawer must be open on its first
     * frame. Moving it off screen and back would both ignore the setting and show
     * the reader an empty strip of screen before the panel arrives.
     *
     * <p>The position is read <em>before</em> the looper runs: draining it would
     * finish a pending animation, so a drawer that was pushed off screen and slid
     * back would look identical to one that was never moved. That is exactly the
     * mutation this test has to catch.
     */
    @Test
    public void withRemoveAnimationsOnTheDrawerIsAlreadyOpen() {
        android.provider.Settings.Global.putFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        MainActivity host = open();
        assertFalse("animasi harus mati",
                id.shuyasal.kuromaku.ui.Motion.enabled(host));

        View decor = openDrawerDecorBeforeIdle(host);

        assertEquals("tanpa animasi drawer harus langsung di tempatnya",
                0f, decor.getTranslationX(), 0.001f);
        // And the content must be there right away, not an empty panel.
        assertNotNull("isi drawer harus ada tanpa animasi",
                decor.findViewById(android.R.id.content));

        // Still at rest after the looper runs: nothing was queued to move it.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("tidak boleh ada animasi susulan",
                0f, decor.getTranslationX(), 0.001f);
    }

    @Test
    public void theShinigamiEntryOpensTheNativeCataloguePage() {
        MainActivity host = open();
        LinearLayout panel = openDrawer(host);
        item(panel, "drawer_manga_shinigami").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull("halaman shinigami harus dibuka", started);
        assertEquals(id.shuyasal.kuromaku.shinigami.ShinigamiActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void theVoratoonEntryStillOpensTheWebViewPage() {
        MainActivity host = open();
        LinearLayout panel = openDrawer(host);
        item(panel, "drawer_manga_voratoon").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(MangaActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void theBackupFileCarriesTheShinigamiSection() throws Exception {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        SharedPreferences manga = context.getSharedPreferences(
                id.shuyasal.kuromaku.shinigami.ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE);
        manga.edit().clear().putInt("progress:chapter-x", 66).commit();
        MainActivity host = open();
        Uri target = Uri.parse("content://test/backup-shinigami.json");
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        Shadows.shadowOf(context.getContentResolver()).registerOutputStream(target, written);
        host.onActivityResult(MainActivity.REQUEST_BACKUP, Activity.RESULT_OK, new Intent().setData(target));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        JSONObject root = new JSONObject(written.toString(StandardCharsets.UTF_8.name()));
        assertEquals(66, root.getJSONObject("shinigami_preferences")
                .getJSONObject("progress:chapter-x").getInt("value"));
        manga.edit().clear().commit();
    }
}
