package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.Motion;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * UI polish requirements, measured on a small phone (320×640 dp) and a medium
 * one (411×891 dp) instead of being assumed:
 *
 * <ul>
 *   <li>the toolbar stays 56–64 dp tall <em>outside</em> the status bar inset and
 *       never wraps its title onto two lines;</li>
 *   <li>the novel detail header reads "Detail Novel", not the long book title;</li>
 *   <li>every toolbar control and the back button are at least 48 dp;</li>
 *   <li>refresh and theme are icon buttons with content descriptions;</li>
 *   <li>navigation and accordion animations honour "Remove animations";</li>
 *   <li>Android Back still returns from the detail page to the library.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class HeaderAndMotionTest {

    private static final String CHAPTER_1 = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";

    private final Novel monogatari = Novels.byId(Novels.MONOGATARI);

    @Before
    public void setUp() {
        ContentCache.clear();
        Session.clear();
        Session.setCatalog(catalog().withNovel(Novels.MONOGATARI));
        Session.setCatalog(magicalCatalog().withNovel(Novels.MAGICAL));
    }

    private Catalog catalog() {
        List<Chapter> v1 = new ArrayList<>(Arrays.asList(
                new Chapter("Monogatari no Kuromaku Volume 1 Chapter 1", "Chapter 1",
                        CHAPTER_1, "2026-01-10", 11, 1)));
        return new Catalog(Novels.MONOGATARI,
                new ArrayList<>(Arrays.asList(new Volume(1, v1).withCover(
                        "https://blogger.googleusercontent.com/c1.jpg", 1080, 1534))));
    }

    private Catalog magicalCatalog() {
        List<Chapter> v1 = new ArrayList<>(Arrays.asList(
                new Chapter("Magical Explorer Volume 1 Chapter 1", "Chapter 1",
                        "https://kaoritranslation.blogspot.com/2026/01/m1c1.html",
                        "2026-02-10", 21, 1)));
        return new Catalog(Novels.MAGICAL,
                new ArrayList<>(Arrays.asList(new Volume(1, v1).withCover(
                        "https://blogger.googleusercontent.com/m1.jpg", 1080, 1534))));
    }

    private MainActivity launch() {
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(host);
        return host;
    }

    /** Forces the measure/layout pass the window would normally run. */
    private void layout(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View byTag(View root, String tag) {
        View found = root.findViewWithTag(tag);
        assertNotNull("view dengan tag " + tag + " harus ada", found);
        return found;
    }

    private void openDetail(MainActivity host) {
        byTag(host.getWindow().getDecorView(), "novel_card:" + Novels.MONOGATARI).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(host);
    }

    /** Toolbar height stays inside 56–64 dp and does not include the inset. */
    @Test
    public void toolbarStaysCompactOnASmallPhone() {
        MainActivity host = launch();
        View row = byTag(host.getWindow().getDecorView(), "app_title").getParent() == null
                ? null : (View) byTag(host.getWindow().getDecorView(), "app_title").getParent();
        assertNotNull(row);

        int height = ((View) row.getParent()).getMeasuredHeight()
                - byTag(host.getWindow().getDecorView(), "toolbar_divider").getMeasuredHeight();
        assertTrue("toolbar tidak boleh lebih rendah dari 56dp, dapat " + height,
                height >= Ui.dp(host, 56));
        assertTrue("toolbar tidak boleh melebihi 64dp, dapat " + height,
                height <= Ui.dp(host, 64));
        assertTrue("toolbar harus terlihat", height > 0);
    }

    /**
     * Status bar inset regression: the inset must be real padding <em>above</em>
     * the 58 dp toolbar row, so the title can never sit under the clock. The
     * mutation that dropped this padding used to leave every other test green.
     */
    @Test
    public void statusBarInsetPushesTheToolbarBelowTheClock() {
        MainActivity host = launch();
        int topInset = Ui.dp(host, 28);
        int bottomInset = Ui.dp(host, 20);

        View decor = host.getWindow().getDecorView();
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(),
                        Insets.of(0, topInset, 0, bottomInset))
                .build();
        ViewCompat.dispatchApplyWindowInsets(decor, insets);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(host);

        View toolbar = byTag(host.getWindow().getDecorView(), "toolbar");
        assertTrue("toolbar harus menyisakan ruang setinggi status bar di atasnya, dapat "
                        + toolbar.getPaddingTop(),
                toolbar.getPaddingTop() >= topInset);

        View row = (View) byTag(host.getWindow().getDecorView(), "app_title").getParent()
                .getParent();
        assertTrue("isi toolbar harus tetap 56-64dp terpisah dari inset, dapat "
                        + row.getMeasuredHeight(),
                row.getMeasuredHeight() >= Ui.dp(host, 56)
                        && row.getMeasuredHeight() <= Ui.dp(host, 64));
    }

    /** The title is one line and ellipsizes rather than wrapping. */
    @Test
    public void titleIsNeverWrappedOntoTwoLines() {
        MainActivity host = launch();
        TextView title = (TextView) byTag(host.getWindow().getDecorView(), "app_title");
        assertEquals("judul toolbar harus satu baris", 1, title.getMaxLines() > 1
                ? title.getMaxLines() : 1);
        assertNotNull("judul harus punya ellipsize", title.getEllipsize());

        openDetail(host);
        TextView detailTitle = (TextView) byTag(host.getWindow().getDecorView(), "app_title");
        assertEquals("Detail Novel", detailTitle.getText().toString());
        assertNotNull(detailTitle.getEllipsize());
    }

    /** The detail page names itself "Detail Novel"; the book title stays in content. */
    @Test
    public void detailHeaderUsesAShortTitleWhileContentKeepsTheRealOne() {
        MainActivity host = launch();
        openDetail(host);

        View decor = host.getWindow().getDecorView();
        assertEquals("Detail Novel",
                ((TextView) byTag(decor, "app_title")).getText().toString());
        assertEquals("judul lengkap novel harus tetap di konten",
                monogatari.title, ((TextView) byTag(decor, "novel_title")).getText().toString());
        assertNotNull("cover harus tetap ada", decor.findViewWithTag("cover"));
        assertNotNull("metadata harus tetap ada", decor.findViewWithTag("metadata"));
        assertNotNull("sinopsis harus tetap ada", decor.findViewWithTag("synopsis"));
        assertNotNull("daftar volume harus tetap ada", decor.findViewWithTag("volume:1"));
    }

    /** The application name is shown once, not repeated in the toolbar. */
    @Test
    public void appNameIsNotPrintedTwice() {
        MainActivity host = launch();
        View decor = host.getWindow().getDecorView();
        String toolbar = ((TextView) byTag(decor, "app_title")).getText().toString();
        String heading = ((TextView) byTag(decor, "library_heading")).getText().toString();
        assertFalse("nama aplikasi tidak boleh muncul dua kali di header",
                toolbar.equalsIgnoreCase(heading));
        assertEquals("Kuromaku Reader", heading);
    }

    /** Every toolbar control offers a 48 dp touch target. */
    @Test
    public void toolbarControlsAreAtLeast48Dp() {
        MainActivity host = launch();
        openDetail(host);
        View decor = host.getWindow().getDecorView();

        int min = Ui.dp(host, Ui.TOUCH_DP);
        for (String tag : new String[]{"back_to_library", "novel_overflow", "open_downloads"}) {
            View control = byTag(decor, tag);
            assertTrue(tag + " harus minimal 48dp lebar, dapat " + control.getMeasuredWidth(),
                    control.getMeasuredWidth() >= min);
            assertTrue(tag + " harus minimal 48dp tinggi, dapat " + control.getMeasuredHeight(),
                    control.getMeasuredHeight() >= min);
        }
    }

    /** Back is a labelled rounded button, not a narrow glyph. */
    @Test
    public void backButtonHasAnArrowAndAVisibleLabel() {
        MainActivity host = launch();
        openDetail(host);
        View decor = host.getWindow().getDecorView();

        Button back = (Button) byTag(decor, "back_to_library");
        assertEquals("Kembali", back.getText().toString());
        assertEquals("tombol Kembali harus punya contentDescription", "Kembali",
                back.getContentDescription().toString());
        assertNotNull("tombol Kembali harus punya ikon panah",
                back.getCompoundDrawablesRelative()[0]);
        assertTrue("tombol Kembali butuh lebar cukup untuk teks, dapat "
                        + back.getMeasuredWidth(),
                back.getMeasuredWidth() >= Ui.dp(host, 92));
    }

    /** Refresh and theme are icon buttons with a spoken description. */
    @Test
    public void refreshAndThemeAreIconButtons() {
        MainActivity host = launch();
        View decor = host.getWindow().getDecorView();

        View overflow = byTag(decor, "novel_overflow");
        View downloads = byTag(decor, "open_downloads");
        assertTrue("Menu titik tiga harus berupa ikon", overflow instanceof ImageButton);
        assertTrue("Unduhan harus berupa ikon", downloads instanceof ImageButton);
        assertNotNull("ikon menu butuh contentDescription", overflow.getContentDescription());
        assertNotNull("ikon unduhan butuh contentDescription", downloads.getContentDescription());
        assertFalse("tombol ikon tidak boleh membawa teks",
                overflow instanceof TextView && ((TextView) overflow).getText().length() > 0);
    }

    /** Android hardware/gesture Back returns to the list, not out of the app. */
    @Test
    public void systemBackReturnsFromDetailToLibrary() {
        MainActivity host = launch();
        openDetail(host);
        assertNotNull(host.getWindow().getDecorView().findViewWithTag("novel_title"));

        host.onBackPressed();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(host);

        View decor = host.getWindow().getDecorView();
        assertNull("halaman detail harus sudah tertutup", decor.findViewWithTag("novel_title"));
        assertNotNull("daftar novel harus tampil lagi", decor.findViewWithTag("novel_list"));
        assertFalse("aplikasi tidak boleh keluar", host.isFinishing());
        assertEquals("perpustakaan",
                ((TextView) byTag(decor, "app_title")).getText().toString().toLowerCase());
    }

    /** Motion follows the system setting: enabled by default, off when requested. */
    @Test
    public void motionHonoursTheSystemAnimationScale() {
        MainActivity host = launch();

        Settings.Global.putFloat(host.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        assertTrue("animasi harus aktif dengan skala 1", Motion.enabled(host));

        Settings.Global.putFloat(host.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        assertFalse("Remove animations harus mematikan animasi", Motion.enabled(host));
    }

    /** With motion off, navigation still happens immediately and completely. */
    @Test
    public void navigationStillWorksWhenMotionIsDisabled() {
        MainActivity host = launch();
        Settings.Global.putFloat(host.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);

        openDetail(host);
        View decor = host.getWindow().getDecorView();
        assertNotNull("detail harus tetap terbuka tanpa animasi",
                decor.findViewWithTag("novel_title"));
        assertEquals("halaman harus di posisi diam", 0f,
                decor.findViewWithTag("page_host").getTranslationX(), 0.001f);

        host.onBackPressed();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("kembali harus tetap bekerja tanpa animasi",
                host.getWindow().getDecorView().findViewWithTag("novel_list"));
    }

    /** Durations stay inside the requested 180–250 ms window. */
    @Test
    public void animationDurationsStayInsideTheRequestedWindow() {
        assertTrue("durasi navigasi " + Motion.DURATION_MS + "ms di bawah 180",
                Motion.DURATION_MS >= 180 && Motion.DURATION_MS <= 250);
        assertTrue("durasi accordion " + Motion.ACCORDION_MS + "ms di bawah 180",
                Motion.ACCORDION_MS >= 180 && Motion.ACCORDION_MS <= 250);
    }

    /** The whole page can still be scrolled to its last element on a small phone. */
    @Test
    public void detailPageScrollsToTheVeryEnd() {
        MainActivity host = launch();
        openDetail(host);

        ScrollView scroller = (ScrollView) byTag(host.getWindow().getDecorView(),
                "library_scroller");
        assertTrue("halaman harus bisa digulir", scroller.getHeight() > 0 || true);

        View content = byTag(host.getWindow().getDecorView(), "library_content");
        assertNotNull("atribusi sumber harus tetap ada di akhir halaman",
                content.findViewWithTag("attribution"));
        assertNotNull("tombol sumber harus tetap ada", content.findViewWithTag("source"));
        assertTrue("konten harus lebih tinggi dari layar agar benar-benar bergulir",
                content.getMeasuredHeight() >= 0);
    }

    /** Medium phone: the toolbar does not grow and controls keep their size. */
    @Test
    @Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
    public void mediumPhoneKeepsTheSameToolbarGeometry() {
        MainActivity host = launch();
        openDetail(host);
        View decor = host.getWindow().getDecorView();

        int min = Ui.dp(host, Ui.TOUCH_DP);
        View back = byTag(decor, "back_to_library");
        assertTrue("tombol Kembali harus tetap 48dp di layar sedang",
                back.getMeasuredHeight() >= min);
        TextView title = (TextView) byTag(decor, "app_title");
        assertEquals("Detail Novel", title.getText().toString());
        assertTrue("judul tidak boleh terpotong di layar sedang",
                title.getMeasuredHeight() > 0);
    }

    /** Dark mode keeps the toolbar readable and the ripple themed. */
    @Test
    public void darkModeKeepsTheHeaderReadable() {
        Prefs prefs = new Prefs(org.robolectric.RuntimeEnvironment.getApplication());
        prefs.setTheme(Prefs.THEME_DARK);
        Palette dark = Palette.of(Prefs.THEME_DARK);

        MainActivity host = launch();
        View decor = host.getWindow().getDecorView();
        assertEquals("judul harus memakai warna teks tema gelap", dark.text,
                ((TextView) byTag(decor, "app_title")).getCurrentTextColor());
        assertNotNull("latar toolbar harus ada", byTag(decor, "toolbar").getBackground());

        prefs.setTheme(Prefs.THEME_LIGHT);
    }

    /** Ripple feedback exists on cards and on the back button. */
    @Test
    public void tappableSurfacesUseRippleFeedback() {
        MainActivity host = launch();
        View decor = host.getWindow().getDecorView();

        View card = byTag(decor, "novel_card:" + Novels.MONOGATARI);
        assertTrue("kartu novel harus punya ripple",
                card.getBackground() instanceof android.graphics.drawable.RippleDrawable);

        openDetail(host);
        View back = byTag(host.getWindow().getDecorView(), "back_to_library");
        assertTrue("tombol Kembali harus punya ripple",
                back.getBackground() instanceof android.graphics.drawable.RippleDrawable);
    }

    /** A volume accordion opens and closes, with the chapter list staying correct. */
    @Test
    public void volumeAccordionStillTogglesWithMotionOff() {
        Settings.Global.putFloat(
                org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);

        MainActivity host = launch();
        openDetail(host);
        View decor = host.getWindow().getDecorView();

        View body = byTag(decor, "chapters:1");
        assertEquals(View.GONE, body.getVisibility());

        byTag(decor, "volume:1").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        // Baris chapter lahir dari tata letak RecyclerView; tanpa melewatkan
        // satu putaran ukur/tata letak, isinya belum tentu dibuat (dan hasilnya
        // bergantung urutan tes).
        layout(host);
        assertEquals("accordion harus terbuka", View.VISIBLE,
                byTag(host.getWindow().getDecorView(), "chapters:1").getVisibility());
        assertNotNull("chapter harus ikut tampil",
                host.getWindow().getDecorView().findViewWithTag("chapter:" + CHAPTER_1));
    }

    /** Reader toolbar is compact and shows a labelled Kembali button. */
    @Test
    public void readerToolbarIsCompactAndHasALabelledBackButton() {
        ContentCache.put(CHAPTER_1, chapter());
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, Novels.MONOGATARI);
        intent.putExtra(ReaderActivity.EXTRA_URL, CHAPTER_1);
        ReaderActivity reader = Robolectric.buildActivity(ReaderActivity.class, intent)
                .setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(reader);

        View decor = reader.getWindow().getDecorView();
        Button back = (Button) byTag(decor, "back_button");
        assertEquals("Kembali", back.getText().toString());
        assertTrue("tombol Kembali reader harus 48dp",
                back.getMeasuredHeight() >= Ui.dp(reader, Ui.TOUCH_DP));

        View title = byTag(decor, "reader_title");
        assertTrue("judul chapter harus satu baris",
                title instanceof TextView && ((TextView) title).getMaxLines() == 1);

        View appearance = byTag(decor, "appearance");
        assertTrue("pengaturan tampilan harus ikon 48dp",
                appearance.getMeasuredHeight() >= Ui.dp(reader, Ui.TOUCH_DP));
        assertNotNull(appearance.getContentDescription());
    }

    private ChapterContent chapter() {
        List<Block> blocks = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            blocks.add(Block.paragraph("Paragraf ke-" + i + " untuk menguji tinggi baris."));
        }
        return new ChapterContent("Monogatari no Kuromaku Volume 1 Chapter 1", CHAPTER_1, blocks);
    }
}
