package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The reported crash: tapping the download icon in the detail page header leaves
 * the screen below the toolbar empty.
 *
 * <p>These tests drive the real {@link MainActivity} — the same click listener the
 * user taps — because the blank page came from the host screen, not from
 * {@code DownloadsPage} in isolation. A page builder tested alone can look perfect
 * while the activity throws away its content before rendering it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class DownloadsNavigationTest {

    private static final String CH1 = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
    private static final String CH2 = "https://kaoritranslation.blogspot.com/2026/01/v1c2.html";

    private File root;

    @Before
    public void setUp() {
        ContentCache.clear();
        Session.clear();
        DownloadStatusStore.reset();
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(),
                "downloads-nav-" + System.nanoTime());
        OfflineStore.useRoot(root);

        Session.setCatalog(catalog(Novels.MONOGATARI));
        Session.setCatalog(catalog(Novels.MAGICAL));

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                return "<html><body><div class='post-body entry-content'>"
                        + "<p>Paragraf pertama chapter contoh yang cukup panjang.</p>"
                        + "<p>Paragraf kedua chapter contoh.</p>"
                        + "</div></body></html>";
            }

            @Override
            public byte[] image(String url) throws Exception {
                return plate();
            }
        });
    }

    @After
    public void tearDown() {
        Fetcher.reset();
        OfflineStore.reset();
        DownloadStatusStore.reset();
        deleteTree(root);
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] plate() throws Exception {
        try (InputStream in = DownloadsNavigationTest.class.getClassLoader()
                .getResourceAsStream("offline/plate_a.jpg")) {
            assertNotNull(in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private Catalog catalog(String novelId) {
        List<Chapter> v1 = new ArrayList<>(Arrays.asList(
                new Chapter("Chapter 1", "Chapter 1", CH1, "2026-01-10", 11, 1),
                new Chapter("Chapter 2", "Chapter 2", CH2, "2026-01-11", 12, 1)));
        return new Catalog(novelId, new ArrayList<>(Arrays.asList(
                new Volume(1, v1).withCover(
                        "https://blogger.googleusercontent.com/c1.jpg", 1080, 1534))));
    }

    private MainActivity launch() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        idle(host);
        return host;
    }

    private void idle(Activity activity) {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View decor = activity.getWindow().getDecorView();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View byTag(Activity activity, String tag) {
        View found = activity.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull("view dengan tag " + tag + " harus ada", found);
        return found;
    }

    private void openDetail(MainActivity host) {
        byTag(host, "novel_card:" + Novels.MONOGATARI).performClick();
        idle(host);
    }

    /** Taps the download icon in the toolbar, exactly as the user does. */
    private void tapDownloadsIcon(MainActivity host) {
        byTag(host, "open_downloads").performClick();
        idle(host);
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> List<T> findAll(View root, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(root)) out.add((T) root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                out.addAll(findAll(group.getChildAt(i), type));
            }
        }
        return out;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }

    /** Every visible text on screen, for "page is not blank" assertions. */
    private String visibleText(Activity activity) {
        StringBuilder text = new StringBuilder();
        for (TextView view : findAll(activity.getWindow().getDecorView(), TextView.class)) {
            if (view.getVisibility() != View.VISIBLE) continue;
            CharSequence value = view.getText();
            if (value != null && value.length() > 0) text.append(value).append('\n');
        }
        return text.toString();
    }

    // ------------------------------------------------------------------- tests

    /**
     * The reported bug: after tapping the icon the page area must hold something.
     * A header alone is the failure.
     */
    @Test
    public void tappingDownloadIconRendersTheDownloadsPage() {
        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);

        View content = byTag(host, "page_content");
        assertTrue("halaman di bawah header tidak boleh kosong",
                ((ViewGroup) content).getChildCount() > 0);
        assertNotNull("blok unduhan harus dirender", byTag(host, "downloads_content"));
        assertTrue("isi halaman harus punya anak",
                ((ViewGroup) byTag(host, "downloads_content")).getChildCount() > 0);
        assertNotNull("toolbar harus berjudul Unduhan",
                host.getWindow().getDecorView().findViewWithTag("app_title"));
        assertEquals("Unduhan", ((TextView) byTag(host, "app_title")).getText().toString());
    }

    /** The page is visible, not merely present at alpha 0 or translated away. */
    @Test
    public void downloadsPageIsActuallyVisible() {
        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);

        View pageHost = byTag(host, "page_host");
        assertEquals("halaman tidak boleh transparan", 1f, pageHost.getAlpha(), 0.01f);
        assertEquals("halaman tidak boleh tergeser", 0f, pageHost.getTranslationX(), 0.01f);
        assertTrue("halaman harus terlihat", pageHost.isShown());
        assertTrue("halaman harus menempati ruang",
                pageHost.getWidth() > 0 && pageHost.getHeight() > 0);
    }

    /** With nothing stored the empty state explains itself instead of staying blank. */
    @Test
    public void emptyDownloadsPageExplainsWhatToDo() {
        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);

        String text = visibleText(host);
        assertTrue("harus menyebut belum ada chapter terunduh, dapat: " + text,
                text.contains("Belum ada chapter yang diunduh")
                        || text.contains("Belum ada chapter yang tersimpan"));
        assertTrue("harus menjelaskan cara mengunduh, dapat: " + text,
                text.contains("Pilih chapter atau volume"));
        assertNotNull("tombol Lihat Chapter harus ada", byTag(host, "downloads_browse"));
        assertNotNull("ikon unduhan pada keadaan kosong harus ada",
                byTag(host, "downloads_empty_icon"));
    }

    /** "Lihat Chapter" returns to the chapter list of the novel. */
    @Test
    public void browseButtonReturnsToTheNovelChapters() {
        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);

        byTag(host, "downloads_browse").performClick();
        idle(host);

        assertEquals("harus kembali ke Detail Novel", "Detail Novel",
                ((TextView) byTag(host, "app_title")).getText().toString());
        assertNotNull("daftar chapter harus tampil lagi",
                byTag(host, "chapters:1"));
        assertNull("halaman unduhan harus tertutup",
                host.getWindow().getDecorView().findViewWithTag("downloads_content"));
    }

    /** Back from the downloads page returns to the detail page, not the library. */
    @Test
    public void backFromDownloadsReturnsToTheDetailPage() {
        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);

        host.onBackPressed();
        idle(host);

        assertEquals("Back harus kembali ke Detail Novel", "Detail Novel",
                ((TextView) byTag(host, "app_title")).getText().toString());
        assertNotNull("konten detail novel tidak boleh hilang", byTag(host, "chapters:1"));
    }

    /** The detail page keeps its scroll position after a round trip to downloads. */
    @Test
    public void detailScrollPositionSurvivesTheRoundTrip() {
        MainActivity host = launch();
        openDetail(host);
        idle(host);

        View scroller = byTag(host, "library_scroller");
        scroller.scrollTo(0, 240);
        idle(host);

        tapDownloadsIcon(host);
        byTag(host, "downloads_browse").performClick();
        idle(host);

        assertEquals("posisi gulir detail harus dipulihkan", 240,
                ((android.widget.ScrollView) byTag(host, "library_scroller")).getScrollY());
    }

    /** Android Back from the library itself still leaves the app. */
    @Test
    public void backFromLibraryStillLeavesTheApp() {
        MainActivity host = launch();
        assertFalse("activity tidak boleh langsung selesai", host.isFinishing());
        host.onBackPressed();
        assertTrue("Back dari perpustakaan harus menutup aplikasi", host.isFinishing());
    }

    /** A stored chapter appears in the downloads page with its real label. */
    @Test
    public void storedChapterIsListedOnTheDownloadsPage() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), Novels.MONOGATARI,
                CH1, 1, "Chapter 1", () -> false);

        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);

        String text = visibleText(host);
        assertTrue("chapter tersimpan harus tercantum, dapat: " + text,
                text.contains("Chapter 1"));
        assertTrue("jumlah chapter harus disebut, dapat: " + text,
                text.contains("1 chapter"));
        assertNotNull("blok novel harus ada", byTag(host, "downloads_novel:" + Novels.MONOGATARI));
        assertNull("keadaan kosong tidak boleh muncul",
                host.getWindow().getDecorView().findViewWithTag("downloads_empty"));
    }

    /** Opening the downloads page never touches the network. */
    @Test
    public void downloadsPageMakesNoRequests() {
        final int[] requests = {0};
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                requests[0]++;
                return "";
            }

            @Override
            public byte[] image(String url) {
                requests[0]++;
                return new byte[0];
            }
        });

        MainActivity host = launch();
        openDetail(host);
        int before = requests[0];
        tapDownloadsIcon(host);

        assertEquals("halaman unduhan harus membaca penyimpanan lokal saja",
                before, requests[0]);
    }

    /**
     * The reported blank page, reproduced exactly: page building throws. Before
     * the fix, {@code render()} cleared the content first, so the toolbar survived
     * and everything below it disappeared with no message. Now the failure keeps
     * a readable error and a retry on screen.
     */
    @Test
    public void aPageBuildFailureNeverLeavesABlankScreen() {
        MainActivity host = launch();
        openDetail(host);

        MainActivity.pageFault = () -> {
            throw new IllegalStateException("index unduhan rusak");
        };
        try {
            tapDownloadsIcon(host);
        } finally {
            MainActivity.pageFault = null;
        }

        View content = byTag(host, "page_content");
        assertTrue("layar tidak boleh kosong saat pembangunan halaman gagal",
                ((ViewGroup) content).getChildCount() > 0);
        assertNotNull("pesan kegagalan harus tampil", byTag(host, "page_failure"));
        TextView reason = (TextView) byTag(host, "page_failure_reason");
        assertEquals("index unduhan rusak", reason.getText().toString());

        View retry = byTag(host, "page_failure_retry");
        assertTrue("area sentuh minimal 48 dp",
                retry.getMinimumHeight() >= dp(host, 48));

        // Recovering: with the fault gone, retry brings the real page back.
        retry.performClick();
        idle(host);
        assertNotNull("Coba lagi harus membangun halaman unduhan",
                byTag(host, "downloads_content"));
        assertNull("pesan kegagalan harus hilang",
                host.getWindow().getDecorView().findViewWithTag("page_failure"));
    }

    /** The detail page itself is unchanged after the round trip. */
    @Test
    public void detailPageStillHasCoverAndSynopsisAfterTheRoundTrip() {
        MainActivity host = launch();
        openDetail(host);
        tapDownloadsIcon(host);
        host.onBackPressed();
        idle(host);

        String text = visibleText(host);
        assertTrue("cover/metadata harus tetap ada, dapat: " + text,
                text.contains("Monogatari"));
        assertNotNull("judul novel harus tetap ada",
                host.getWindow().getDecorView().findViewWithTag("novel_title"));
        assertTrue("sinopsis harus tetap ada", text.contains("Sinopsis"));
    }
}
