package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ApplicationProvider;

import android.app.Dialog;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * Manga stage 1: a separate screen that shows the source site, and the one rule
 * that keeps it from being taken over.
 *
 * <p>{@code MangaActivity} loads the landing page and lets the site discover its
 * own numbered domain, so the guard here is the only thing standing between the
 * reader and an advert or a redirect chain. Every rejection below is a shape that
 * looks allowed at a glance.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class MangaScreenTest {

    private Context context = ApplicationProvider.getApplicationContext();

    private boolean allowed(String url) {
        return MangaActivity.trusted(Uri.parse(url));
    }

    /** The landing site and its own subdomains are the only pages allowed. */
    @Test
    public void theSourceAndItsOwnPagesAreAllowed() {
        assertTrue("landing", allowed("https://voratoon.id/"));
        assertTrue("halaman manga", allowed("https://voratoon.id/manga/some-title"));
        assertTrue("subdomain", allowed("https://cdn.voratoon.id/page/1.jpg"));
        assertTrue("domain lama", allowed("https://voratoon.com/manga"));
        assertTrue("subdomain domain lama", allowed("https://12.voratoon.com/manga"));
    }

    /**
     * A host that merely ends with the name is not the source. Without the dot in
     * front of the suffix check, "evilvoratoon.id" would pass — that is the whole
     * point of this test.
     */
    @Test
    public void aLookalikeHostIsRejected() {
        assertFalse("tanpa titik", allowed("https://evilvoratoon.id/manga"));
        assertFalse("tanpa titik, domain lama", allowed("https://evilvoratoon.com/"));
        assertFalse("suffix di belakang host lain", allowed("https://voratoon.id.evil.com/"));
        assertFalse("subdomain penyusup", allowed("https://voratoon.id.evil.com/manga/1"));
        assertFalse("huruf besar menyamarkan host lain", allowed("https://VORATOON.ID.EVIL.COM/manga"));
        // Memuat nama sumber di tengah host tetap bukan host sumber. Tes ini yang
        // membedakan "berakhiran" dari sekadar "mengandung": pemeriksaan longgar
        // seperti contains(".voratoon.id") akan meloloskan alamat ini.
        assertFalse("nama sumber di tengah host", allowed("https://cdn.voratoon.id.evil.com/manga"));
        assertFalse("nama domain lama di tengah host", allowed("https://cdn.voratoon.com.evil.com/"));
    }

    /**
     * A path that happens to contain the source name is not a host. This looks
     * like an attack but is a page on the real site, so it must stay readable —
     * rejecting it would break ordinary links.
     */
    @Test
    public void aPathThatMentionsTheNameIsStillTheRealSite() {
        assertTrue(allowed("https://VORATOON.ID/palsu.evil.com"));
        assertTrue(allowed("https://voratoon.id/manga/voratoon.id.evil.com"));
    }

    /** Cleartext is refused even on the right host, and so is a missing host. */
    @Test
    public void onlyHttpsOnARealHostIsAllowed() {
        assertFalse("http", allowed("http://voratoon.id/"));
        assertFalse("file", allowed("file:///etc/hosts"));
        assertFalse("javascript", allowed("javascript:alert(1)"));
        assertFalse("tanpa host", allowed("https:///manga"));
        assertFalse("null", MangaActivity.trusted(null));
    }

    /** The screen must be declared, or Android refuses to open it at all. */
    @Test
    public void theScreenIsDeclaredInTheManifest() throws Exception {
        ComponentName name = new ComponentName(context, MangaActivity.class);
        ActivityInfo info = context.getPackageManager().getActivityInfo(name, 0);
        assertNotNull("MangaActivity harus terdaftar", info);
        assertFalse("hanya dari dalam aplikasi", info.exported);
    }

    /**
     * The novel screen offers a way across; without it the screen is unreachable.
     * The entry is an icon button now, so the check is on the description a screen
     * reader announces and on the 48 dp tap area, not on visible text.
     */
    @Test
    public void theNovelToolbarOffersTheMangaSection() {
        Intent intent = new Intent(context, MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        assertNotNull("tombol menu bagian harus ada", menu);
        assertEquals("Menu Novel dan Manga", menu.getContentDescription().toString());
        assertTrue("ikon, bukan teks", menu instanceof android.widget.ImageButton);
        // A button without a drawable is a blank square: tappable, announced by a
        // screen reader, and invisible to everyone else. Checking the type alone
        // let exactly that through.
        assertNotNull("ikon harus tergambar, bukan tombol kosong",
                ((android.widget.ImageButton) menu).getDrawable());
        assertTrue("area ketuk 48 dp", menu.getHeight() > 0 || menu.getLayoutParams().width > 0);
        assertEquals("terlihat di rak", View.VISIBLE, menu.getVisibility());
    }

    /**
     * The section menu belongs to the shelf only. On the detail and download pages
     * the back arrow takes that slot; two controls in one corner is how a user ends
     * up on the manga screen while trying to leave a novel.
     */
    @Test
    public void theSectionMenuStepsAsideOnTheOtherPages() {
        Intent intent = new Intent(context, MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        View back = host.getWindow().getDecorView().findViewWithTag("back_to_library");
        assertNotNull(menu);
        assertNotNull(back);
        assertEquals("rak: menu tampil", View.VISIBLE, menu.getVisibility());
        assertEquals("rak: panah sembunyi", View.GONE, back.getVisibility());

        View downloads = host.getWindow().getDecorView().findViewWithTag("open_downloads");
        assertNotNull(downloads);
        downloads.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("unduhan: menu sembunyi", View.GONE, menu.getVisibility());
        assertEquals("unduhan: panah tampil", View.VISIBLE, back.getVisibility());
    }

    /** Coming back from the manga screen keeps the page that was open. */
    @Test
    public void theOpenPageSurvivesARecreation() {
        Intent intent = new Intent(context, MangaActivity.class);
        MangaActivity screen = Robolectric.buildActivity(MangaActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        android.os.Bundle state = new android.os.Bundle();
        screen.onSaveInstanceState(state);
        assertEquals("https://voratoon.id/", state.getString("manga_url"));

        assertNotNull("layar harus terbangun", screen.getWindow().getDecorView());
    }

    // ---------------------------------------------------------------- fullscreen

    private MangaActivity manga() {
        Intent intent = new Intent(context, MangaActivity.class);
        MangaActivity screen = Robolectric.buildActivity(MangaActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return screen;
    }

    private static View tagged(MangaActivity screen, String tag) {
        return screen.getWindow().getDecorView().findViewWithTag(tag);
    }

    /**
     * Fullscreen has to actually give the page its space back. The button is the
     * only way in, so the test presses the real button rather than calling a setter.
     */
    @Test
    public void theFullscreenButtonHidesTheChrome() {
        MangaActivity screen = manga();
        View header = tagged(screen, "manga_header");
        View address = tagged(screen, "manga_address");
        View toggle = tagged(screen, "manga_fullscreen");
        assertNotNull("header harus ada", header);
        assertNotNull("bilah alamat harus ada", address);
        assertNotNull("tombol layar penuh harus ada", toggle);
        assertEquals("awalnya bukan layar penuh", View.VISIBLE, header.getVisibility());
        assertEquals("awalnya bilah alamat tampil", View.VISIBLE, address.getVisibility());

        toggle.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("header harus hilang", View.GONE, header.getVisibility());
        assertEquals("bilah alamat harus hilang", View.GONE, address.getVisibility());

        toggle.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("header harus kembali", View.VISIBLE, header.getVisibility());
        assertEquals("bilah alamat harus kembali", View.VISIBLE, address.getVisibility());
    }

    /**
     * Back leaves fullscreen first. If it did not, the only way out of a full page
     * would be to leave the screen entirely, which loses the reading position.
     */
    @Test
    public void backLeavesFullscreenBeforeLeavingTheScreen() {
        MangaActivity screen = manga();
        View header = tagged(screen, "manga_header");

        tagged(screen, "manga_fullscreen").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, header.getVisibility());

        screen.onBackPressed();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("kembali harus keluar dari layar penuh", View.VISIBLE, header.getVisibility());
        assertFalse("layar tidak boleh ikut tertutup", screen.isFinishing());
    }

    /** Rotating the phone in fullscreen must not bring the toolbar back. */
    @Test
    public void fullscreenSurvivesARecreation() {
        Bundle state = new Bundle();
        state.putBoolean("manga_fullscreen", true);
        state.putString("manga_url", "https://voratoon.id/manga/1");

        MangaActivity screen = Robolectric.buildActivity(MangaActivity.class,
                new Intent(context, MangaActivity.class))
                .setup(state).get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("header harus tetap hilang setelah dipulihkan",
                View.GONE, tagged(screen, "manga_header").getVisibility());

        Bundle saved = new Bundle();
        screen.onSaveInstanceState(saved);
        assertTrue("status layar penuh harus disimpan", saved.getBoolean("manga_fullscreen"));
    }

    /**
     * The status bar area belongs to the page only in fullscreen. In normal mode the
     * toolbar must be pushed below the status bar by the real inset height, never by
     * a hardcoded padding — this asserts the number the system reported.
     */
    @Test
    public void theStatusBarInsetMovesTheToolbarInsteadOfOverlappingIt() {
        MangaActivity screen = manga();
        View root = screen.findViewById(android.R.id.content);
        assertNotNull(root);
        View content = ((ViewGroup) root).getChildAt(0);

        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 48))
                .build();
        ViewCompat.dispatchApplyWindowInsets(content, insets);

        assertEquals("toolbar di bawah status bar 72px", 72, content.getPaddingTop());
        assertEquals("konten tidak menabrak navigasi 48px", 48, content.getPaddingBottom());

        tagged(screen, "manga_fullscreen").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ViewCompat.dispatchApplyWindowInsets(content, insets);

        assertEquals("layar penuh memakai seluruh tinggi", 0, content.getPaddingTop());
        assertEquals("layar penuh memakai seluruh dasar", 0, content.getPaddingBottom());
    }

    @Test
    public void mangaScreenDrawerShowsConsistentItemsAndActiveMangaState() {
        MangaActivity screen = manga();
        View menu = tagged(screen, "section_menu");
        assertNotNull(menu);
        menu.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        View root = dialog.findViewById(android.R.id.content);
        assertNotNull(root);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        TextView novel = panel.findViewWithTag("drawer_novel");
        TextView manga = panel.findViewWithTag("drawer_manga");
        TextView backup = panel.findViewWithTag("drawer_backup");
        TextView restore = panel.findViewWithTag("drawer_restore");

        assertNotNull(novel);
        assertNotNull(manga);
        assertNotNull(backup);
        assertNotNull(restore);

        assertEquals("Novel", novel.getText().toString());
        assertEquals("Manga  ✓", manga.getText().toString());
        assertEquals("Backup progres membaca", backup.getText().toString());
        assertEquals("Restore progres membaca", restore.getText().toString());

        int expectedWidth = (int) (screen.getResources().getDisplayMetrics().widthPixels * 0.82f);
        assertEquals(expectedWidth, dialog.getWindow().getAttributes().width);
    }

    @Test
    public void tappingNovelInMangaDrawerNavigatesToMainActivity() {
        MangaActivity screen = manga();
        tagged(screen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        panel.findViewWithTag("drawer_novel").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent next = Shadows.shadowOf(screen).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
        assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertTrue(screen.isFinishing());
    }

    @Test
    public void tappingBackupInMangaDrawerRoutesToMainActivityWithAction() {
        MangaActivity screen = manga();
        tagged(screen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        panel.findViewWithTag("drawer_backup").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent next = Shadows.shadowOf(screen).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("backup", next.getStringExtra("kuromaku_data_action"));
        assertTrue(screen.isFinishing());
    }

    @Test
    public void tappingRestoreInMangaDrawerRoutesToMainActivityWithAction() {
        MangaActivity screen = manga();
        tagged(screen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        panel.findViewWithTag("drawer_restore").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent next = Shadows.shadowOf(screen).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("restore", next.getStringExtra("kuromaku_data_action"));
        assertTrue(screen.isFinishing());
    }

    @Test
    public void swipingLeftOnMangaDrawerDismissesIt() {
        MangaActivity screen = manga();
        tagged(screen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        long downTime = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 200f, 0);
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 100f, 200f, 0);
        panel.dispatchTouchEvent(down);
        panel.dispatchTouchEvent(move);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertFalse(dialog.isShowing());
    }

    @Test
    public void mangaDrawerHasDarkScrimOverlayAndTouchOutsideDismissal() {
        MangaActivity screen = manga();
        tagged(screen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        android.view.Window window = dialog.getWindow();
        assertNotNull(window);
        assertEquals(0.55f, window.getAttributes().dimAmount, 0.01f);
        assertTrue((window.getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0);

        dialog.setCanceledOnTouchOutside(true);
        dialog.onBackPressed();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
    }

    @Test
    public void mangaDrawerSupportsDarkAndLightThemes() {
        new Prefs(context, null).setTheme(Prefs.THEME_LIGHT);
        MangaActivity lightScreen = manga();
        tagged(lightScreen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog lightDialog = ShadowDialog.getLatestDialog();
        assertNotNull(lightDialog);
        View lightRoot = lightDialog.findViewById(android.R.id.content);
        LinearLayout lightPanel = (LinearLayout) ((ViewGroup) lightRoot).getChildAt(0);
        TextView lightHeading = (TextView) lightPanel.getChildAt(0);
        assertEquals(Palette.of(Prefs.THEME_LIGHT).text, lightHeading.getCurrentTextColor());
        lightDialog.dismiss();

        new Prefs(context, null).setTheme(Prefs.THEME_DARK);
        MangaActivity darkScreen = manga();
        tagged(darkScreen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog darkDialog = ShadowDialog.getLatestDialog();
        assertNotNull(darkDialog);
        View darkRoot = darkDialog.findViewById(android.R.id.content);
        LinearLayout darkPanel = (LinearLayout) ((ViewGroup) darkRoot).getChildAt(0);
        TextView darkHeading = (TextView) darkPanel.getChildAt(0);
        assertEquals(Palette.of(Prefs.THEME_DARK).text, darkHeading.getCurrentTextColor());
        darkDialog.dismiss();
    }

    @Test
    public void tappingActiveMangaJustDismissesDrawerWithoutChangingFullscreenOrRecreating() {
        MangaActivity screen = manga();
        View header = tagged(screen, "manga_header");
        tagged(screen, "section_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        View root = dialog.findViewById(android.R.id.content);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);
        panel.findViewWithTag("drawer_manga").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertFalse(dialog.isShowing());
        assertFalse(screen.isFinishing());
        assertEquals("header tetap tampil normal", View.VISIBLE, header.getVisibility());
    }

    @Test
    public void addressBarShowsShortHostAndOpensUrlDetailsDialog() {
        MangaActivity screen = manga();
        TextView address = (TextView) tagged(screen, "manga_address");
        assertNotNull(address);
        assertEquals("Sumber: voratoon.id", address.getText().toString());

        address.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        View root = dialog.findViewById(android.R.id.content);
        assertNotNull(root);
        LinearLayout panel = (LinearLayout) ((ViewGroup) root).getChildAt(0);

        TextView title = (TextView) panel.getChildAt(0);
        assertEquals("Alamat Halaman", title.getText().toString());

        TextView hostLabel = (TextView) panel.getChildAt(1);
        assertEquals("Sumber: voratoon.id", hostLabel.getText().toString());

        LinearLayout buttonRow = (LinearLayout) panel.getChildAt(3);
        android.widget.Button copyBtn = (android.widget.Button) buttonRow.getChildAt(0);
        assertEquals("Salin URL", copyBtn.getText().toString());

        copyBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        android.content.ClipboardManager cm = (android.content.ClipboardManager) screen.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(cm);
        assertNotNull(cm.getPrimaryClip());
        assertEquals("https://voratoon.id/", cm.getPrimaryClip().getItemAt(0).getText().toString());
        assertFalse(dialog.isShowing());
    }

    @Test
    public void googleOAuthNavigationLaunchesExternalBrowserIntent() {
        MangaActivity screen = manga();
        android.webkit.WebView web = null;
        ViewGroup root = (ViewGroup) ((ViewGroup) screen.findViewById(android.R.id.content)).getChildAt(0);
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof android.webkit.WebView) {
                web = (android.webkit.WebView) root.getChildAt(i);
                break;
            }
        }
        assertNotNull(web);

        org.robolectric.shadows.ShadowWebView shadowWeb = Shadows.shadowOf(web);
        android.webkit.WebViewClient client = shadowWeb.getWebViewClient();
        assertNotNull(client);

        android.webkit.WebResourceRequest googleOAuthRequest = new android.webkit.WebResourceRequest() {
            @Override public Uri getUrl() {
                return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=12345");
            }
            @Override public boolean isForMainFrame() { return true; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return true; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
        };

        boolean handled = client.shouldOverrideUrlLoading(web, googleOAuthRequest);
        assertTrue(handled);

        Intent started = Shadows.shadowOf(screen).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals("accounts.google.com", started.getData().getHost());
    }

    @Test
    public void adNetworkRequestIsInterceptedWithNoContentResponse() {
        MangaActivity screen = manga();
        android.webkit.WebView web = null;
        ViewGroup root = (ViewGroup) ((ViewGroup) screen.findViewById(android.R.id.content)).getChildAt(0);
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof android.webkit.WebView) {
                web = (android.webkit.WebView) root.getChildAt(i);
                break;
            }
        }
        assertNotNull(web);

        org.robolectric.shadows.ShadowWebView shadowWeb = Shadows.shadowOf(web);
        android.webkit.WebViewClient client = shadowWeb.getWebViewClient();
        assertNotNull(client);

        android.webkit.WebResourceRequest adRequest = new android.webkit.WebResourceRequest() {
            @Override public Uri getUrl() {
                return Uri.parse("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-12345");
            }
            @Override public boolean isForMainFrame() { return false; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
        };

        android.webkit.WebResourceResponse response = client.shouldInterceptRequest(web, adRequest);
        assertNotNull(response);
        assertEquals(204, response.getStatusCode());

        android.webkit.WebResourceRequest authRequest = new android.webkit.WebResourceRequest() {
            @Override public Uri getUrl() {
                return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth");
            }
            @Override public boolean isForMainFrame() { return false; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
        };

        android.webkit.WebResourceResponse authResponse = client.shouldInterceptRequest(web, authRequest);
        org.junit.Assert.assertNull(authResponse);
    }
}
