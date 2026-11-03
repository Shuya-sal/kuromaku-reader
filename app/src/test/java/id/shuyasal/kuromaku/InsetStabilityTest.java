package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.ui.Motion;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Status bar inset stability: the toolbar must stay below the status bar in
 * every theme, after any number of theme switches, after opening and closing a
 * novel detail page, and after the activity is recreated with a stored dark
 * theme.
 *
 * <p>Root cause this class was written to catch: the inset listener compared the
 * measured bars against instance fields that survived a shell rebuild, so after a
 * theme change the freshly created toolbar never received its status bar padding
 * and the title slid under the clock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class InsetStabilityTest {

    private static final String CHAPTER_1 =
            "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
    private static final int TOP_INSET_DP = 28;
    private static final int BOTTOM_INSET_DP = 20;

    @Before
    public void setUp() {
        ContentCache.clear();
        Session.clear();
        Session.setCatalog(catalog());
        // Both novels must be registered: MainActivity only skips the background
        // fetch when every catalogue is already cached. Leaving the second one out
        // started a real network thread that later replaced Session and broke
        // unrelated tests depending on class order.
        Session.setCatalog(otherNovelCatalog());
        new Prefs(org.robolectric.RuntimeEnvironment.getApplication())
                .setTheme(Prefs.THEME_LIGHT);
    }

    private Catalog catalog() {
        List<Chapter> v1 = new ArrayList<>(Arrays.asList(
                new Chapter("Monogatari no Kuromaku Volume 1 Chapter 1", "Chapter 1",
                        CHAPTER_1, "2026-01-10", 11, 1)));
        return new Catalog(Novels.MONOGATARI,
                new ArrayList<>(Arrays.asList(new Volume(1, v1).withCover(
                        "https://blogger.googleusercontent.com/c1.jpg", 1080, 1534))));
    }

    private Catalog otherNovelCatalog() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("Magical Explorer Volume 1 Chapter 1", "Chapter 1",
                        "https://kaoritranslation.blogspot.com/2026/02/m1c1.html",
                        "2026-02-10", 21, 1)));
        return new Catalog(Novels.MAGICAL,
                new ArrayList<>(Arrays.asList(new Volume(1, chapters).withCover(
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

    private void layout(android.app.Activity activity) {
        View decor = activity.getWindow().getDecorView();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /**
     * Delivers the window insets the platform would deliver: called once at
     * launch and again every time a new view tree is attached (which is exactly
     * what a theme change does here).
     */
    private void deliverInsets(MainActivity activity) {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(),
                        Insets.of(0, Ui.dp(activity, TOP_INSET_DP),
                                0, Ui.dp(activity, BOTTOM_INSET_DP)))
                .build();
        ViewCompat.dispatchApplyWindowInsets(activity.getWindow().getDecorView(), insets);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);
    }

    private View byTag(View root, String tag) {
        View found = root.findViewWithTag(tag);
        assertNotNull("view dengan tag " + tag + " harus ada", found);
        return found;
    }

    /** The single toolbar row inside the toolbar container. */
    private View toolbarRow(MainActivity host) {
        View title = byTag(host.getWindow().getDecorView(), "app_title");
        return (View) title.getParent().getParent();
    }

    private int topInsetPx(MainActivity host) {
        return Ui.dp(host, TOP_INSET_DP);
    }

    private void clickTheme(MainActivity host) {
        byTag(host.getWindow().getDecorView(), "theme").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        // The platform re-delivers insets when the new tree is attached.
        deliverInsets(host);
    }

    /** A fresh launch puts the toolbar below the status bar. */
    @Test
    public void firstLaunchAppliesTheStatusBarInset() {
        MainActivity host = launch();
        deliverInsets(host);
        assertEquals("toolbar harus menerima padding status bar",
                topInsetPx(host), byTag(host.getWindow().getDecorView(), "toolbar")
                        .getPaddingTop());
    }

    /** THE BUG: switching light → dark must not lose the inset. */
    @Test
    public void themeChangeKeepsTheToolbarBelowTheStatusBar() {
        MainActivity host = launch();
        deliverInsets(host);
        assertEquals(topInsetPx(host),
                byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());

        clickTheme(host);

        View toolbar = byTag(host.getWindow().getDecorView(), "toolbar");
        assertEquals("setelah ganti tema toolbar tidak boleh kehilangan padding status bar",
                topInsetPx(host), toolbar.getPaddingTop());
    }

    /**
     * The faithful reproduction of the reported bug.
     *
     * <p>On a device the platform delivers the <em>same</em> insets the listener
     * already saw, so a "only when it changed" guard decides nothing changed and
     * never re-applies the padding to the freshly built toolbar. This test models
     * that: switch the theme and assert the padding is already correct before any
     * further inset dispatch is allowed to arrive.
     */
    @Test
    public void aRebuiltShellReappliesKnownInsetsWithoutWaitingForADispatch() {
        MainActivity host = launch();
        deliverInsets(host);
        assertEquals(topInsetPx(host),
                byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());

        // Theme switch only. No deliverInsets(): the rebuilt tree must carry the
        // inset itself.
        byTag(host.getWindow().getDecorView(), "theme").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(host);

        View toolbar = byTag(host.getWindow().getDecorView(), "toolbar");
        assertEquals("shell yang dibangun ulang harus langsung memakai inset yang sudah diketahui",
                topInsetPx(host), toolbar.getPaddingTop());
    }

    /** Ten switches in a row: never lost, never doubled. */
    @Test
    public void tenThemeChangesNeverLoseOrDoubleTheInset() {
        MainActivity host = launch();
        deliverInsets(host);

        for (int i = 1; i <= 10; i++) {
            clickTheme(host);
            View toolbar = byTag(host.getWindow().getDecorView(), "toolbar");
            assertEquals("pergantian tema ke-" + i + " kehilangan padding",
                    topInsetPx(host), toolbar.getPaddingTop());
        }
    }

    /** The inset is applied once, not stacked on every dispatch. */
    @Test
    public void repeatedInsetDeliveriesDoNotDoubleThePadding() {
        MainActivity host = launch();
        for (int i = 0; i < 5; i++) deliverInsets(host);

        View toolbar = byTag(host.getWindow().getDecorView(), "toolbar");
        assertEquals("padding tidak boleh menumpuk", topInsetPx(host), toolbar.getPaddingTop());
    }

    /** Header height stays inside 56–64 dp and never grows with the theme changes. */
    @Test
    public void headerHeightStaysStableAcrossThemeChanges() {
        MainActivity host = launch();
        deliverInsets(host);

        int firstRowHeight = toolbarRow(host).getMeasuredHeight();
        assertTrue("baris toolbar di luar rentang 56-64dp: " + firstRowHeight,
                firstRowHeight >= Ui.dp(host, 56) && firstRowHeight <= Ui.dp(host, 64));

        for (int i = 1; i <= 10; i++) {
            clickTheme(host);
            int rowHeight = toolbarRow(host).getMeasuredHeight();
            assertEquals("tinggi baris toolbar berubah pada pergantian ke-" + i,
                    firstRowHeight, rowHeight);
            assertEquals("padding status bar berubah pada pergantian ke-" + i,
                    topInsetPx(host),
                    byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());
        }
    }

    /** Dark theme stored on disk: a relaunch still insets the new tree. */
    @Test
    public void storedDarkThemeOnRelaunchStillAppliesTheInset() {
        new Prefs(org.robolectric.RuntimeEnvironment.getApplication())
                .setTheme(Prefs.THEME_DARK);
        MainActivity host = launch();
        deliverInsets(host);

        assertEquals("tema gelap tersimpan tidak boleh menghilangkan padding",
                topInsetPx(host),
                byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());
    }

    /** Detail page → back → another theme change: still correct. */
    @Test
    public void detailPageThenBackThenThemeChangeKeepsTheInset() {
        MainActivity host = launch();
        deliverInsets(host);

        byTag(host.getWindow().getDecorView(), "novel_card:" + Novels.MONOGATARI)
                .performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        deliverInsets(host);
        assertEquals("halaman detail harus tetap di bawah status bar", topInsetPx(host),
                byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());

        host.onBackPressed();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        deliverInsets(host);
        assertNotNull("harus kembali ke perpustakaan",
                host.getWindow().getDecorView().findViewWithTag("novel_list"));

        clickTheme(host);
        assertEquals("ganti tema setelah kembali harus tetap benar", topInsetPx(host),
                byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());
    }

    /** Light theme → dark status bar icons; dark theme → light icons. */
    @Test
    public void statusBarIconsFollowTheTheme() {
        MainActivity host = launch();
        deliverInsets(host);

        assertTrue("tema terang butuh ikon status bar gelap", isLightStatusBar(host));

        clickTheme(host); // light → dark
        assertFalse("tema gelap butuh ikon status bar terang", isLightStatusBar(host));

        clickTheme(host); // dark → sepia
        clickTheme(host); // sepia → light
        assertTrue("kembali ke tema terang harus memakai ikon gelap", isLightStatusBar(host));
    }

    private boolean isLightStatusBar(MainActivity host) {
        return WindowCompat.getInsetsController(host.getWindow(),
                host.getWindow().getDecorView()).isAppearanceLightStatusBars();
    }

    /** The navigation animation may move the page, never the toolbar. */
    @Test
    public void navigationAnimationNeverMovesTheToolbar() {
        MainActivity host = launch();
        deliverInsets(host);

        byTag(host.getWindow().getDecorView(), "novel_card:" + Novels.MONOGATARI)
                .performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View decor = host.getWindow().getDecorView();
        View page = byTag(decor, "page_host");
        View toolbar = byTag(decor, "toolbar");
        assertTrue("halaman memang dianimasikan", page.getTranslationX() >= 0f);
        assertEquals("toolbar tidak boleh ikut bergeser", 0f,
                toolbar.getTranslationX(), 0.001f);
        assertEquals("root tidak boleh ikut bergeser", 0f, decor.getTranslationX(), 0.001f);
        assertEquals("padding status bar tidak boleh berubah saat animasi", topInsetPx(host),
                toolbar.getPaddingTop());
    }

    /** Content still clears the navigation bar and the page still scrolls. */
    @Test
    public void contentKeepsTheNavigationBarInsetAndStillScrolls() {
        MainActivity host = launch();
        deliverInsets(host);

        View decor = host.getWindow().getDecorView();
        ScrollView scroller = (ScrollView) byTag(decor, "library_scroller");
        assertTrue("halaman harus tetap bisa digulir", scroller.getHeight() > 0);

        byTag(decor, "novel_card:" + Novels.MONOGATARI).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        deliverInsets(host);

        View content = byTag(host.getWindow().getDecorView(), "page_content");
        assertTrue("konten butuh padding bawah melebihi inset navigasi, dapat "
                        + content.getPaddingBottom(),
                content.getPaddingBottom() > Ui.dp(host, BOTTOM_INSET_DP));
    }

    /** Reader Mode: switching the theme repaints, it must not drop the inset. */
    @Test
    public void readerToolbarKeepsItsInsetAfterThemeChange() {
        ContentCache.put(CHAPTER_1, chapter());
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, Novels.MONOGATARI);
        intent.putExtra(ReaderActivity.EXTRA_URL, CHAPTER_1);
        ReaderActivity reader = Robolectric.buildActivity(ReaderActivity.class, intent)
                .setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(reader);

        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(),
                        Insets.of(0, Ui.dp(reader, TOP_INSET_DP),
                                0, Ui.dp(reader, BOTTOM_INSET_DP)))
                .build();
        ViewCompat.dispatchApplyWindowInsets(reader.getWindow().getDecorView(), insets);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View topBar = byTag(reader.getWindow().getDecorView(), "reader_toolbar");
        assertEquals("toolbar reader harus di bawah status bar",
                Ui.dp(reader, TOP_INSET_DP), topBar.getPaddingTop());

        // Open the appearance panel and switch to the dark theme.
        byTag(reader.getWindow().getDecorView(), "appearance").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Button dark = findButtonByText(reader.getWindow().getDecorView(), "Gelap");
        assertNotNull("tombol tema Gelap harus ada di panel", dark);
        dark.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("ganti tema di reader tidak boleh menghilangkan padding",
                Ui.dp(reader, TOP_INSET_DP),
                byTag(reader.getWindow().getDecorView(), "reader_toolbar").getPaddingTop());
        assertEquals("tombol Kembali harus tetap 48dp",
                true, byTag(reader.getWindow().getDecorView(), "back_button")
                        .getMeasuredHeight() >= Ui.dp(reader, Ui.TOUCH_DP));
    }

    private Button findButtonByText(View root, String text) {
        if (root instanceof Button
                && text.contentEquals(((Button) root).getText())) return (Button) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButtonByText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private ChapterContent chapter() {
        List<Block> blocks = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            blocks.add(Block.paragraph("Paragraf ke-" + i + " untuk menguji inset."));
        }
        return new ChapterContent("Monogatari no Kuromaku Volume 1 Chapter 1", CHAPTER_1, blocks);
    }

    /** Motion must not be smuggled in as the cause of a layout shift. */
    @Test
    public void themeChangeWithMotionDisabledKeepsTheSameGeometry() {
        android.provider.Settings.Global.putFloat(
                org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        try {
            MainActivity host = launch();
            deliverInsets(host);
            int before = toolbarRow(host).getMeasuredHeight();

            clickTheme(host);

            assertEquals("tinggi baris toolbar tidak boleh berubah tanpa animasi",
                    before, toolbarRow(host).getMeasuredHeight());
            assertEquals(topInsetPx(host),
                    byTag(host.getWindow().getDecorView(), "toolbar").getPaddingTop());
        } finally {
            android.provider.Settings.Global.putFloat(
                    org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        }
    }
}
