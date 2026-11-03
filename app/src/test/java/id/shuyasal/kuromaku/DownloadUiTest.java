package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.ui.DownloadsPage;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The download controls on the novel detail page and the "Unduhan" page.
 *
 * <p>Everything here is measured on a small phone (320 dp wide) because the
 * buttons must fit beside a chapter title without pushing it off screen, and
 * because "Download Semua" sits under the volume row where space is tight.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class DownloadUiTest {

    private static final String NOVEL = Novels.MONOGATARI;
    private static final String CH1 = "https://kaoritranslation.blogspot.com/2026/04/v1c1.html";
    private static final String CH2 = "https://kaoritranslation.blogspot.com/2026/04/v1c2.html";

    private File root;
    private OfflineStore store;

    private final List<String> downloaded = new ArrayList<>();
    private final List<String> deleted = new ArrayList<>();
    private final List<Integer> volumesDownloaded = new ArrayList<>();
    private final List<Integer> volumesCancelled = new ArrayList<>();

    @Before
    public void setUp() {
        DownloadStatusStore.reset();
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(),
                "download-ui-" + System.nanoTime());
        OfflineStore.useRoot(root);
        store = OfflineStore.get(RuntimeEnvironment.getApplication());

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                return "<html><body><div class='post-body entry-content'>"
                        + "<p>Paragraf satu yang cukup panjang untuk dianggap teks nyata.</p>"
                        + "<p>Paragraf dua dari chapter contoh ini.</p>"
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
        try (InputStream in = DownloadUiTest.class.getClassLoader()
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

    private Catalog catalog() {
        List<Chapter> v1 = new ArrayList<>(Arrays.asList(
                new Chapter("Volume 1 Chapter 1", "Chapter 1", CH1, "2026-04-01", 11, 1),
                new Chapter("Volume 1 Chapter 2", "Chapter 2", CH2, "2026-04-02", 12, 1)));
        List<Volume> volumes = new ArrayList<>(Arrays.asList(
                new Volume(1, v1).withCover(
                        "https://blogger.googleusercontent.com/c1.jpg", 1080, 1534)));
        return new Catalog(NOVEL, volumes);
    }

    private Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        new Prefs(activity).scoped(NOVEL).toggleExpanded("1");
        return activity;
    }

    private LibraryPage.DownloadListener downloadListener() {
        return new LibraryPage.DownloadListener() {
            @Override
            public void onDownloadChapter(Chapter chapter) {
                downloaded.add(chapter.url);
            }

            @Override
            public void onDeleteChapter(Chapter chapter) {
                deleted.add(chapter.url);
            }

            @Override
            public void onDownloadVolume(Volume volume) {
                volumesDownloaded.add(volume.number);
            }

            @Override
            public void onCancelVolume(Volume volume) {
                volumesCancelled.add(volume.number);
            }
        };
    }

    private LibraryPage.DownloadState state(final Catalog catalog) {
        return new LibraryPage.DownloadState() {
            @Override
            public int state(Chapter chapter) {
                return DownloadStatusStore.resolve(NOVEL, chapter.url, store);
            }

            @Override
            public String statusLabel(Chapter chapter) {
                int state = DownloadStatusStore.resolve(NOVEL, chapter.url, store);
                switch (state) {
                    case DownloadStatusStore.SAVED:
                        return "Tersimpan";
                    case DownloadStatusStore.DOWNLOADING:
                        return "Mengunduh…";
                    case DownloadStatusStore.FAILED:
                        return "Gagal";
                    default:
                        return "Belum diunduh";
                }
            }

            @Override
            public int[] volumeProgress(Volume volume) {
                int saved = store.savedCountInVolume(NOVEL, volume.number);
                return new int[]{saved, volume.chapterCount()};
            }

            @Override
            public boolean isVolumeRunning(Volume volume) {
                return false;
            }
        };
    }

    private LinearLayout buildDetail(Activity activity, Catalog catalog, boolean withDownloads) {
        Palette palette = Palette.of(Prefs.THEME_DARK);
        LibraryPage page = new LibraryPage(activity, new Prefs(activity).scoped(NOVEL), palette,
                Novels.byId(NOVEL), catalog, false, listener());
        if (withDownloads) page.setDownload(downloadListener(), state(catalog));
        LinearLayout content = page.build();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, width, content.getMeasuredHeight());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return content;
    }

    private LibraryPage.Listener listener() {
        return new LibraryPage.Listener() {
            @Override
            public void onOpenChapter(Chapter chapter) {
            }

            @Override
            public void onToggleSynopsis() {
            }

            @Override
            public void onToggleVolume(String volumeKey) {
            }

            @Override
            public void onOpenSource() {
            }

            @Override
            public void onOpenCover(String url, String caption) {
            }
        };
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

    // ------------------------------------------------------------- detail page

    /** Every chapter row offers a download control with a 48 dp target. */
    @Test
    public void chapterRowsOfferADownloadButtonWithATouchTarget() {
        Activity activity = activity();
        Catalog catalog = catalog();
        LinearLayout content = buildDetail(activity, catalog, true);

        View button = content.findViewWithTag("download_chapter:" + CH1);
        assertNotNull("tombol unduh chapter harus ada", button);
        assertTrue("area sentuh minimal 48 dp, dapat " + button.getMinimumHeight(),
                button.getMinimumHeight() >= dp(activity, 48));
        assertEquals("harus punya deskripsi aksesibilitas", "Unduh chapter ini",
                button.getContentDescription());
    }

    /** Status text is printed for each chapter, starting as "Belum diunduh". */
    @Test
    public void chapterRowShowsTheNotDownloadedStatus() {
        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), true);

        View status = content.findViewWithTag("download_status:" + CH1);
        assertNotNull("label status harus ada", status);
        assertTrue(status instanceof TextView);
        assertEquals("Belum diunduh", ((TextView) status).getText().toString());
    }

    /** Once stored, the same row switches to "Tersimpan" and a delete action. */
    @Test
    public void savedChapterShowsTersimpanAndOffersDelete() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);

        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), true);

        TextView status = (TextView) content.findViewWithTag("download_status:" + CH1);
        assertEquals("Tersimpan", status.getText().toString());
        assertNotNull("harus ada tombol hapus",
                content.findViewWithTag("download_delete:" + CH1));
        assertNull("tidak boleh masih menawarkan unduh",
                content.findViewWithTag("download_chapter:" + CH1));
    }

    /** A failed chapter offers "Coba lagi" instead of the download arrow. */
    @Test
    public void failedChapterOffersRetry() {
        DownloadStatusStore.set(DownloadStatusStore.key(NOVEL, CH1),
                DownloadStatusStore.FAILED, "HTTP 500");

        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), true);

        View retry = content.findViewWithTag("download_retry:" + CH1);
        assertNotNull("tombol Coba lagi harus ada saat gagal", retry);
        assertTrue(retry instanceof Button);
        assertEquals("Coba lagi", ((Button) retry).getText().toString());

        retry.performClick();
        assertEquals("tekan Coba lagi harus memicu unduh ulang", 1, downloaded.size());
        assertEquals(CH1, downloaded.get(0));
    }

    /** The delete action reports the chapter it belongs to. */
    @Test
    public void deleteActionReportsItsChapter() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);
        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), true);

        content.findViewWithTag("download_delete:" + CH1).performClick();
        assertEquals(1, deleted.size());
        assertEquals(CH1, deleted.get(0));
    }

    /** A volume offers "Download Semua" and the counts of what is stored. */
    @Test
    public void volumeOffersDownloadAllWithCounts() {
        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), true);

        View downloadAll = content.findViewWithTag("volume_download:1");
        assertNotNull("tombol Download Semua harus ada", downloadAll);
        assertEquals("Download Semua", ((Button) downloadAll).getText().toString());
        assertTrue("area sentuh minimal 48 dp",
                downloadAll.getMinimumHeight() >= dp(activity, 48));

        downloadAll.performClick();
        assertEquals("tekan harus memicu unduh volume", 1, volumesDownloaded.size());
        assertEquals(Integer.valueOf(1), volumesDownloaded.get(0));
    }

    /** Partial progress is printed as "x dari y tersimpan". */
    @Test
    public void volumeShowsPartialProgress() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);

        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), true);

        View summary = content.findViewWithTag("volume_summary:1");
        assertNotNull(summary);
        assertEquals("1 dari 2 tersimpan", ((TextView) summary).getText().toString());
    }

    /** With no download layer wired, the page still renders as before. */
    @Test
    public void pageWithoutDownloadLayerHasNoDownloadControls() {
        Activity activity = activity();
        LinearLayout content = buildDetail(activity, catalog(), false);

        assertNull(content.findViewWithTag("download_chapter:" + CH1));
        assertNull(content.findViewWithTag("volume_download:1"));
        assertNotNull("daftar chapter harus tetap ada",
                content.findViewWithTag("chapters:1"));
    }

    // -------------------------------------------------------------- unduhan page

    /** The downloads page lists novel, volume, chapter count and size. */
    @Test
    public void downloadsPageListsNovelVolumeCountAndSize() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);

        Activity activity = activity();
        DownloadsPage page = new DownloadsPage(activity, Palette.of(Prefs.THEME_DARK),
                store, downloadsListener(new ArrayList<String>(), new ArrayList<String>())).withNovels(Novels.all());
        LinearLayout content = page.build();
        content.measure(View.MeasureSpec.makeMeasureSpec(
                        activity.getResources().getDisplayMetrics().widthPixels,
                        View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());

        assertNotNull("judul halaman ada", content.findViewWithTag("downloads_heading"));
        assertNotNull("blok novel ada", content.findViewWithTag("downloads_novel:" + NOVEL));
        assertNotNull("blok volume ada",
                content.findViewWithTag("downloads_volume:" + NOVEL + ":1"));

        TextView summary = (TextView) content.findViewWithTag("downloads_summary");
        assertTrue("ringkasan harus menyebut jumlah chapter, dapat " + summary.getText(),
                summary.getText().toString().contains("1 chapter"));
        assertTrue("ringkasan harus menyebut ukuran",
                summary.getText().toString().matches(".*\\d (B|KB|MB).*"));
    }

    /** Deleting is offered at volume and chapter level, each with its own tag. */
    @Test
    public void downloadsPageOffersChapterAndVolumeDelete() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH2, 1,
                "Chapter 2", () -> false);

        Activity activity = activity();
        List<String> deletedChapters = new ArrayList<>();
        List<String> deletedVolumes = new ArrayList<>();
        DownloadsPage page = new DownloadsPage(activity, Palette.of(Prefs.THEME_DARK), store,
                downloadsListener(deletedChapters, deletedVolumes)).withNovels(Novels.all());
        LinearLayout content = page.build();

        View volumeDelete = content.findViewWithTag("delete_volume:" + NOVEL + ":1");
        assertNotNull("tombol hapus volume harus ada", volumeDelete);
        assertTrue("area sentuh minimal 48 dp",
                volumeDelete.getMinimumHeight() >= dp(activity, 48));
        volumeDelete.performClick();
        assertEquals(1, deletedVolumes.size());

        View chapterDelete = content.findViewWithTag("delete_chapter:" + CH1);
        assertNotNull("tombol hapus chapter harus ada", chapterDelete);
        chapterDelete.performClick();
        assertEquals(1, deletedChapters.size());
        assertEquals(CH1, deletedChapters.get(0));
    }

    /** Nothing stored shows an empty state, not a blank screen. */
    @Test
    public void downloadsPageShowsEmptyStateWhenNothingIsStored() {
        Activity activity = activity();
        DownloadsPage page = new DownloadsPage(activity, Palette.of(Prefs.THEME_DARK), store,
                downloadsListener(new ArrayList<String>(), new ArrayList<String>())).withNovels(Novels.all());
        LinearLayout content = page.build();

        View empty = content.findViewWithTag("downloads_empty");
        assertNotNull("keadaan kosong harus ada", empty);
        // The summary states the fact; the empty block carries the icon, the
        // explanation and the way back to the chapters.
        TextView summary = (TextView) content.findViewWithTag("downloads_summary");
        assertTrue("ringkasan harus menyatakan belum ada unduhan, dapat " + summary.getText(),
                summary.getText().toString().contains("Belum ada chapter yang diunduh"));
        assertNotNull("ikon unduhan harus ada pada keadaan kosong",
                content.findViewWithTag("downloads_empty_icon"));
        assertNotNull("tombol Lihat Chapter harus ada",
                content.findViewWithTag("downloads_browse"));

        StringBuilder text = new StringBuilder();
        for (TextView view : findAll(empty, TextView.class)) {
            text.append(view.getText()).append('\n');
        }
        assertTrue("keadaan kosong harus menjelaskan pilihannya, dapat: " + text,
                text.toString().contains("Pilih chapter atau volume"));
        assertNull("tidak boleh ada blok novel kosong",
                content.findViewWithTag("downloads_novel:" + NOVEL));
    }

    /** Deleting every chapter from the page makes the empty state appear again. */
    @Test
    public void deletingEverythingReturnsToTheEmptyState() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);
        store.deleteChapter(NOVEL, CH1);

        Activity activity = activity();
        DownloadsPage page = new DownloadsPage(activity, Palette.of(Prefs.THEME_DARK), store,
                downloadsListener(new ArrayList<String>(), new ArrayList<String>())).withNovels(Novels.all());
        LinearLayout content = page.build();
        assertNotNull(content.findViewWithTag("downloads_empty"));
        assertEquals("penyimpanan harus kembali ke nol", 0, store.totalBytes());
    }

    private DownloadsPage.Listener downloadsListener(final List<String> deletedChapters,
                                                     final List<String> deletedVolumes) {
        return new DownloadsPage.Listener() {
            @Override
            public void onDeleteChapter(Novel novel, Chapter chapter) {
                deletedChapters.add(chapter.url);
            }

            @Override
            public void onDeleteVolume(Novel novel, int volume, int chapterCount) {
                deletedVolumes.add(novel.id + ":" + volume);
            }

            @Override
            public void onOpenStoredChapter(Novel novel, Chapter chapter) {
            }
        };
    }

    private static int dp(Activity activity, int value) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }

    // ------------------------------------------------------ reading progress label

    /**
     * A started chapter says how far it has been read, as a plain sentence.
     *
     * <p>The label used to be the bare percentage glued to the download state on the
     * same line, which is what produced "67%Mengunduh…" on the reported screenshot.
     * The percentage now lives in its own view, so this test reads it on its own.
     */
    @Test
    public void aStartedChapterSaysHowFarItHasBeenRead() {
        Activity activity = activity();
        new Prefs(activity).scoped(NOVEL).setProgress(CH1, 67);

        LinearLayout content = buildDetail(activity, catalog(), true);

        View read = content.findViewWithTag("chapter_read:" + CH1);
        assertNotNull("label progres baca harus ada", read);
        assertEquals("67% dibaca", ((TextView) read).getText().toString());
    }

    /** A chapter read to the end says so in words, not as a number above 95. */
    @Test
    public void aFinishedChapterSaysSelesaiDibaca() {
        Activity activity = activity();
        new Prefs(activity).scoped(NOVEL).setProgress(CH1, 100);

        LinearLayout content = buildDetail(activity, catalog(), true);

        View read = content.findViewWithTag("chapter_read:" + CH1);
        assertNotNull("label selesai harus ada", read);
        assertEquals("Selesai dibaca", ((TextView) read).getText().toString());
    }

    /** An untouched chapter shows no reading label at all; 0% is not a state. */
    @Test
    public void anUnopenedChapterShowsNoReadingLabel() {
        Activity activity = activity();

        LinearLayout content = buildDetail(activity, catalog(), true);

        assertNull("chapter yang belum dibuka tidak boleh punya label progres",
                content.findViewWithTag("chapter_read:" + CH1));
    }

    /**
     * Reading progress and offline state are two different facts and must stay two
     * different views. Concatenating them is the reported bug, and a single view
     * carrying both strings would bring it back.
     */
    @Test
    public void progressAndDownloadStateNeverShareOneLabel() {
        Activity activity = activity();
        new Prefs(activity).scoped(NOVEL).setProgress(CH1, 67);

        LinearLayout content = buildDetail(activity, catalog(), true);

        View read = content.findViewWithTag("chapter_read:" + CH1);
        View status = content.findViewWithTag("download_status:" + CH1);
        assertNotNull(read);
        assertNotNull(status);
        assertTrue("dua view terpisah", read != status);

        String readText = ((TextView) read).getText().toString();
        String statusText = ((TextView) status).getText().toString();
        assertFalse("label baca tidak memuat status unduhan", readText.contains(statusText));
        assertFalse("status unduhan tidak memuat persen", statusText.contains("%"));
        // The reader scrolls right past this row: the two facts must not run together
        // as one string even if a future layout puts them on one line.
        assertFalse("jangan pernah menyambung", (readText + statusText).contains("67%Belum"));
    }
}
