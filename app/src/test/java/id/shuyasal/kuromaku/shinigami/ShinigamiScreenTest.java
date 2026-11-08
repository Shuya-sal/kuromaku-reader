package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiScreenTest {

    private Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private ShinigamiActivity open() {
        ShinigamiActivity host = Robolectric.buildActivity(ShinigamiActivity.class).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return host;
    }

    private View tagged(ShinigamiActivity host, String tag) {
        View found = host.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull("kontrol '" + tag + "' harus ada", found);
        return found;
    }

    @Test
    public void thePageShowsTheCatalogueAndTheSearchField() {
        ShinigamiActivity host = open();

        assertNotNull("kolom pencarian harus ada", tagged(host, "shinigami_search"));
        assertNotNull("daftar manga harus ada", tagged(host, "shinigami_grid"));
        assertNotNull("tombol cari harus ada", tagged(host, "shinigami_search_button"));
        assertNotNull("menu titik tiga harus ada", tagged(host, "shinigami_overflow"));

        View search = tagged(host, "shinigami_search");
        assertTrue("kolom pencarian harus bisa diketik", search instanceof EditText);
    }

    @Test
    public void theSearchFieldAcceptsAWord() {
        ShinigamiActivity host = open();
        EditText search = (EditText) tagged(host, "shinigami_search");
        search.setText("Solo Leveling");
        assertEquals("Solo Leveling", search.getText().toString());
    }

    @Test
    public void theDrawerOffersEverySectionAndMarksThisOne() {
        ShinigamiActivity host = open();
        tagged(host, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("drawer harus terbuka", dialog);
        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        TextView novel = panel.findViewWithTag("drawer_novel");
        TextView voratoon = panel.findViewWithTag("drawer_manga_voratoon");
        TextView shinigami = panel.findViewWithTag("drawer_manga_shinigami");
        TextView backup = panel.findViewWithTag("drawer_backup");
        TextView restore = panel.findViewWithTag("drawer_restore");

        assertNotNull(novel);
        assertNotNull(voratoon);
        assertNotNull(shinigami);
        assertNotNull(backup);
        assertNotNull(restore);

        assertEquals("Novel", novel.getText().toString());
        assertEquals("Manga Server 1", voratoon.getText().toString());
        assertEquals("Manga Server 2  ✓", shinigami.getText().toString());
        assertEquals("Backup progres membaca", backup.getText().toString());
        assertEquals("Restore progres membaca", restore.getText().toString());
    }

    @Test
    public void theVoratoonEntryLeadsToTheWebViewPage() {
        ShinigamiActivity host = open();
        tagged(host, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);
        panel.findViewWithTag("drawer_manga_voratoon").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull("halaman voratoon harus dibuka", started);
        assertEquals(id.shuyasal.kuromaku.MangaActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void tappingTheActiveEntryClosesTheDrawerAndStays() {
        ShinigamiActivity host = open();
        tagged(host, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);
        panel.findViewWithTag("drawer_manga_shinigami").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertFalse("drawer harus tertutup", dialog.isShowing());
        assertFalse("halaman tidak boleh ditutup", host.isFinishing());
    }
}
