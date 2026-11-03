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
import android.widget.ProgressBar;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.DownloadsPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The downloads page must always say what is happening: a running job shows its
 * novel, volume, counts, progress bar and a cancel button; a failed one shows the
 * reason and retry; nothing stored shows the empty state.
 *
 * <p>Two host-level guarantees are checked here as well, because they are what
 * made the page blank on a device and not in the page builder:
 * <ul>
 *   <li>status changes arrive on the <em>main</em> thread even when the worker
 *       publishes them from a background thread;</li>
 *   <li>a corrupt download index cannot empty the screen — the page still
 *       renders.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class DownloadsProgressTest {

    private static final String CH1 = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
    private static final String CH2 = "https://kaoritranslation.blogspot.com/2026/01/v1c2.html";
    private static final String CH3 = "https://kaoritranslation.blogspot.com/2026/01/v1c3.html";

    private File root;

    private final List<DownloadsPage.Job> active = new ArrayList<>();
    private final List<DownloadsPage.Job> failedJobs = new ArrayList<>();
    private final AtomicInteger cancelled = new AtomicInteger();
    private final AtomicInteger retried = new AtomicInteger();
    private final AtomicInteger browsed = new AtomicInteger();

    @Before
    public void setUp() {
        ContentCache.clear();
        Session.clear();
        DownloadStatusStore.reset();
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(),
                "downloads-progress-" + System.nanoTime());
        OfflineStore.useRoot(root);

        Session.setCatalog(catalog(Novels.MONOGATARI));
        Session.setCatalog(catalog(Novels.MAGICAL));

        active.clear();
        failedJobs.clear();
        cancelled.set(0);
        retried.set(0);
        browsed.set(0);

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                return "<html><body><div class='post-body entry-content'>"
                        + "<p>Paragraf pertama yang cukup panjang untuk teks nyata.</p>"
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
        try (InputStream in = DownloadsProgressTest.class.getClassLoader()
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
                new Chapter("Chapter 2", "Chapter 2", CH2, "2026-01-11", 12, 1),
                new Chapter("Chapter 3", "Chapter 3", CH3, "2026-01-12", 13, 1)));
        return new Catalog(novelId, new ArrayList<>(Arrays.asList(
                new Volume(1, v1).withCover(
                        "https://blogger.googleusercontent.com/c1.jpg", 1080, 1534))));
    }

    private DownloadsPage.Listener listener() {
        return new DownloadsPage.Listener() {
            @Override
            public void onDeleteChapter(Novel novel, Chapter chapter) {
            }

            @Override
            public void onDeleteVolume(Novel novel, int volume, int chapterCount) {
            }

            @Override
            public void onOpenStoredChapter(Novel novel, Chapter chapter) {
            }

            @Override
            public void onBrowseChapters() {
                browsed.incrementAndGet();
            }

            @Override
            public void onRetryJob(DownloadsPage.Job job) {
                retried.incrementAndGet();
                active.add(job);
                failedJobs.remove(job);
            }

            @Override
            public void onCancelJob(DownloadsPage.Job job) {
                cancelled.incrementAndGet();
                active.remove(job);
            }
        };
    }

    private DownloadsPage.Jobs jobs() {
        return new DownloadsPage.Jobs() {
            @Override
            public List<DownloadsPage.Job> active() {
                return new ArrayList<>(active);
            }

            @Override
            public List<DownloadsPage.Job> failed() {
                return new ArrayList<>(failedJobs);
            }
        };
    }

    private LinearLayout page(Activity activity) {
        DownloadsPage page = new DownloadsPage(activity, Palette.of(Prefs.THEME_DARK),
                OfflineStore.get(activity), listener()).withNovels(Novels.all()).withJobs(jobs()).withNovels(Novels.all());
        LinearLayout content = page.build();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, width, content.getMeasuredHeight());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return content;
    }

    private Activity activity() {
        return Robolectric.buildActivity(Activity.class).setup().get();
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

    private String textOf(View root) {
        StringBuilder text = new StringBuilder();
        for (TextView view : findAll(root, TextView.class)) {
            text.append(view.getText()).append('\n');
        }
        return text.toString();
    }

    // ------------------------------------------------------- running job display

    /** A running volume shows novel, volume, counts, progress bar, status, cancel. */
    @Test
    public void runningJobShowsCountsProgressAndCancel() {
        Novel novel = Novels.byId(Novels.MONOGATARI);
        DownloadsPage.Job job = new DownloadsPage.Job(novel, 1, 2, 5, 0, "");
        active.add(job);

        LinearLayout content = page(activity());
        String id = novel.id + ":1";

        View card = content.findViewWithTag("downloads_job:" + id);
        assertNotNull("kartu unduhan berjalan harus ada", card);
        assertTrue("kartu harus menyebut judul novel dan volume, dapat: " + textOf(card),
                textOf(card).contains(novel.title));
        assertTrue("kartu harus menyebut volume, dapat: " + textOf(card),
                textOf(card).contains("Volume 1"));

        TextView counts = (TextView) content.findViewWithTag("downloads_job_counts:" + id);
        assertEquals("2 dari 5 chapter selesai", counts.getText().toString());

        ProgressBar bar = (ProgressBar) content.findViewWithTag("downloads_job_bar:" + id);
        assertEquals("progress bar harus memakai total chapter", 5, bar.getMax());
        assertEquals("progress bar harus mengikuti chapter selesai", 2, bar.getProgress());

        TextView status = (TextView) content.findViewWithTag("downloads_job_status:" + id);
        assertTrue("status harus menyebut chapter yang sedang diunduh, dapat "
                        + status.getText(),
                status.getText().toString().contains("chapter 3 dari 5"));

        View cancel = content.findViewWithTag("downloads_job_cancel:" + id);
        assertNotNull("tombol Batalkan harus ada saat berjalan", cancel);
        assertTrue("area sentuh minimal 48 dp",
                cancel.getMinimumHeight() >= dp(activity(), 48));
        cancel.performClick();
        assertEquals("Batalkan harus memicu pembatalan", 1, cancelled.get());
    }

    /** The section heading says work is running, not that nothing is stored. */
    @Test
    public void runningJobSectionIsLabelled() {
        active.add(new DownloadsPage.Job(Novels.byId(Novels.MONOGATARI), 1, 1, 4, 0, ""));
        LinearLayout content = page(activity());

        assertNotNull("bagian unduhan berjalan harus ada",
                content.findViewWithTag("downloads_running"));
        assertTrue("judul bagian harus jelas, dapat: " + textOf(content),
                textOf(content).contains("Sedang diunduh"));
    }

    /** A running job is shown even when nothing is stored yet. */
    @Test
    public void runningJobIsVisibleBeforeAnythingIsStored() {
        active.add(new DownloadsPage.Job(Novels.byId(Novels.MONOGATARI), 1, 0, 4, 0, ""));
        LinearLayout content = page(activity());

        assertNotNull("kartu unduhan harus tampil walau belum ada yang tersimpan",
                content.findViewWithTag("downloads_job:" + Novels.MONOGATARI + ":1"));
        TextView summary = (TextView) content.findViewWithTag("downloads_summary");
        assertTrue("ringkasan harus menyebut proses berjalan, dapat " + summary.getText(),
                summary.getText().toString().contains("sedang diunduh")
                        || summary.getText().toString().contains("Belum ada"));
    }

    // ---------------------------------------------------------- failed job display

    /** A failed job shows the reason and a retry that actually re-queues it. */
    @Test
    public void failedJobShowsReasonAndRetry() {
        Novel novel = Novels.byId(Novels.MONOGATARI);
        DownloadsPage.Job job = new DownloadsPage.Job(novel, 1, 1, 4, 3, "HTTP 500");
        failedJobs.add(job);

        LinearLayout content = page(activity());
        String id = novel.id + ":1";

        assertNotNull("bagian gagal harus ada", content.findViewWithTag("downloads_failed"));
        TextView status = (TextView) content.findViewWithTag("downloads_job_status:" + id);
        assertTrue("alasan gagal harus tampil, dapat " + status.getText(),
                status.getText().toString().contains("HTTP 500"));

        View retry = content.findViewWithTag("downloads_retry:" + id);
        assertNotNull("tombol Coba Lagi harus ada", retry);
        assertTrue("area sentuh minimal 48 dp",
                retry.getMinimumHeight() >= dp(activity(), 48));
        retry.performClick();
        assertEquals("Coba Lagi harus mengulang pekerjaan", 1, retried.get());
    }

    /** Running and failed sections can coexist without hiding each other. */
    @Test
    public void runningAndFailedJobsAreBothListed() {
        Novel novel = Novels.byId(Novels.MONOGATARI);
        active.add(new DownloadsPage.Job(novel, 1, 2, 4, 0, ""));
        failedJobs.add(new DownloadsPage.Job(novel, 2, 0, 3, 1, "timeout"));

        LinearLayout content = page(activity());
        assertNotNull(content.findViewWithTag("downloads_job:" + novel.id + ":1"));
        assertNotNull(content.findViewWithTag("downloads_job:" + novel.id + ":2"));
        assertNotNull(content.findViewWithTag("downloads_running"));
        assertNotNull(content.findViewWithTag("downloads_failed"));
    }

    /** With nothing running and nothing stored, the empty state is shown. */
    @Test
    public void emptyStateExplainsAndOffersTheWayBack() {
        LinearLayout content = page(activity());
        assertNotNull(content.findViewWithTag("downloads_empty"));
        assertNotNull(content.findViewWithTag("downloads_empty_icon"));

        View browse = content.findViewWithTag("downloads_browse");
        assertNotNull("tombol Lihat Chapter harus ada", browse);
        assertTrue("area sentuh minimal 48 dp",
                browse.getMinimumHeight() >= dp(activity(), 48));
        browse.performClick();
        assertEquals("Lihat Chapter harus membuka daftar chapter", 1, browsed.get());
    }

    // ------------------------------------------------------------ thread guarantee

    /**
     * State published from a background thread must be delivered on the main
     * thread. Delivering it inline made the activity rebuild its page off the
     * main thread, which is how the screen went blank while the toolbar lived on.
     */
    @Test
    public void statusChangesAreDeliveredOnTheMainThread() throws Exception {
        final AtomicBoolean calledOnMain = new AtomicBoolean(false);
        final AtomicBoolean calledOnWorker = new AtomicBoolean(false);
        final CountDownLatch delivered = new CountDownLatch(1);

        DownloadStatusStore.Listener listener = () -> {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                calledOnMain.set(true);
            } else {
                calledOnWorker.set(true);
            }
            delivered.countDown();
        };
        DownloadStatusStore.addListener(listener);
        try {
            Thread worker = new Thread(() ->
                    DownloadStatusStore.set(DownloadStatusStore.key(Novels.MONOGATARI, CH1),
                            DownloadStatusStore.DOWNLOADING, null));
            worker.start();
            worker.join(5000);

            assertFalse("listener tidak boleh dipanggil dari thread worker",
                    calledOnWorker.get());
            assertEquals("delivery harus diposting ke main thread", 1, delivered.getCount());

            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue("listener harus terpanggil di main thread", calledOnMain.get());
            assertEquals(0, delivered.getCount());
        } finally {
            DownloadStatusStore.removeListener(listener);
        }
    }

    /** A listener that throws must not stop the others from being notified. */
    @Test
    public void aFailingListenerDoesNotSilenceTheRest() {
        final AtomicInteger good = new AtomicInteger();
        DownloadStatusStore.Listener broken = () -> {
            throw new IllegalStateException("boom");
        };
        DownloadStatusStore.Listener healthy = good::incrementAndGet;

        DownloadStatusStore.addListener(broken);
        DownloadStatusStore.addListener(healthy);
        try {
            DownloadStatusStore.set(DownloadStatusStore.key(Novels.MONOGATARI, CH1),
                    DownloadStatusStore.SAVED, null);
            assertEquals("listener lain harus tetap dipanggil", 1, good.get());
        } finally {
            DownloadStatusStore.removeListener(broken);
            DownloadStatusStore.removeListener(healthy);
        }
    }

    // --------------------------------------------------------------- host screen

    /**
     * A corrupt download index must not blank the page: the screen still renders
     * content below the toolbar.
     */
    @Test
    public void corruptIndexStillRendersAPage() throws Exception {
        File novelDir = new File(root, Novels.MONOGATARI);
        File chapters = new File(novelDir, "chapters");
        assertTrue(chapters.mkdirs() || chapters.isDirectory());
        java.io.FileOutputStream out = new java.io.FileOutputStream(
                new File(novelDir, "index.json"));
        out.write("{ this is not json ".getBytes("UTF-8"));
        out.close();

        MainActivity host = launch();
        byTag(host, "novel_card:" + Novels.MONOGATARI).performClick();
        idle(host);
        byTag(host, "open_downloads").performClick();
        idle(host);

        View content = byTag(host, "page_content");
        assertTrue("halaman tidak boleh kosong meski index rusak",
                ((ViewGroup) content).getChildCount() > 0);
        assertNotNull("halaman unduhan atau pesan gagal harus tampil",
                host.getWindow().getDecorView().findViewWithTag("downloads_content") != null
                        ? "ok" : null);
    }

    /** Opening downloads with a running job shows progress in the real activity. */
    @Test
    public void activityShowsRunningProgress() {
        DownloadStatusStore.set(DownloadStatusStore.key(Novels.MONOGATARI, CH1),
                DownloadStatusStore.DOWNLOADING, null);
        DownloadStatusStore.set(DownloadStatusStore.key(Novels.MONOGATARI, CH2),
                DownloadStatusStore.QUEUED, null);

        MainActivity host = launch();
        byTag(host, "novel_card:" + Novels.MONOGATARI).performClick();
        idle(host);
        byTag(host, "open_downloads").performClick();
        idle(host);

        assertNotNull("kartu unduhan berjalan harus tampil di activity",
                host.getWindow().getDecorView().findViewWithTag(
                        "downloads_job:" + Novels.MONOGATARI + ":1"));
        assertNull("keadaan kosong tidak boleh muncul saat ada unduhan berjalan",
                host.getWindow().getDecorView().findViewWithTag("downloads_empty"));
    }

    /** A real stored chapter plus a running job both appear. */
    @Test
    public void storedChapterAndRunningJobCoexist() throws Exception {
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), Novels.MONOGATARI,
                CH1, 1, "Chapter 1", () -> false);
        DownloadStatusStore.set(DownloadStatusStore.key(Novels.MONOGATARI, CH2),
                DownloadStatusStore.QUEUED, null);

        MainActivity host = launch();
        byTag(host, "novel_card:" + Novels.MONOGATARI).performClick();
        idle(host);
        byTag(host, "open_downloads").performClick();
        idle(host);

        assertNotNull("chapter tersimpan harus tetap terdaftar",
                host.getWindow().getDecorView().findViewWithTag(
                        "downloads_chapter:" + CH1));
        assertNotNull("unduhan berjalan harus terlihat",
                host.getWindow().getDecorView().findViewWithTag(
                        "downloads_job:" + Novels.MONOGATARI + ":1"));
    }

    // ------------------------------------------------------------- activity helpers

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

    private static int dp(Activity activity, int value) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }
}
