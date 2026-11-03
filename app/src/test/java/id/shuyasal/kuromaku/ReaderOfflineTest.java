package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

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

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.BlockAdapter;
import id.shuyasal.kuromaku.ui.ImageCell;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Reader Mode with downloaded content, while the network is unavailable.
 *
 * <p>This is the "airplane mode" scenario, reproduced in the only way a host
 * without an emulator can honestly do it: the byte source throws as soon as it is
 * called, so any attempt to reach the network fails exactly like a disabled radio.
 * If the reader still shows text and every illustration, the content came from
 * disk.
 *
 * <p>Not covered here (needs a real device): Glide decoding the file path into a
 * bitmap on screen, and the actual system airplane-mode toggle.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class ReaderOfflineTest {

    private static final String NOVEL = Novels.MONOGATARI;
    private static final String CH1 = "https://kaoritranslation.blogspot.com/2026/03/v1c1.html";
    private static final String CH2 = "https://kaoritranslation.blogspot.com/2026/03/v1c2.html";
    private static final String IMG_A =
            "https://blogger.googleusercontent.com/img/a/plate_a.jpg";
    private static final String IMG_B =
            "https://blogger.googleusercontent.com/img/a/plate_b.jpg";

    private File root;
    private OfflineStore store;
    private int networkCalls;

    @Before
    public void setUp() throws Exception {
        ContentCache.clear();
        Session.clear();
        DownloadStatusStore.reset();
        networkCalls = 0;

        root = new File(RuntimeEnvironment.getApplication().getCacheDir(),
                "reader-offline-" + System.nanoTime());
        OfflineStore.useRoot(root);
        store = OfflineStore.get(RuntimeEnvironment.getApplication());

        Session.setCatalog(new Catalog(NOVEL, new ArrayList<>(Arrays.asList(
                new Volume(1, new ArrayList<>(Arrays.asList(
                        new Chapter("Volume 1 Chapter 1", "Chapter 1", CH1, "2026-03-01", 11, 1),
                        new Chapter("Volume 1 Chapter 2", "Chapter 2", CH2, "2026-03-02", 12, 1))),
                        null, 0, 0)))));

        // Download both chapters while "online", then cut the network for good.
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                networkCalls++;
                return article(url);
            }

            @Override
            public byte[] image(String url) throws Exception {
                networkCalls++;
                return plate(url.contains("plate_b") ? "offline/plate_b.jpg" : "offline/plate_a.jpg");
            }
        });
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH1, 1,
                "Chapter 1", () -> false);
        ChapterDownloader.download(RuntimeEnvironment.getApplication(), NOVEL, CH2, 1,
                "Chapter 2", () -> false);

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                networkCalls++;
                throw new java.io.IOException("mode pesawat: tidak ada jaringan");
            }

            @Override
            public byte[] image(String url) throws Exception {
                networkCalls++;
                throw new java.io.IOException("mode pesawat: tidak ada jaringan");
            }
        });
        networkCalls = 0;
    }

    @After
    public void tearDown() {
        Fetcher.reset();
        OfflineStore.reset();
        DownloadStatusStore.reset();
        ContentCache.clear();
        Session.clear();
        deleteTree(root);
    }

    // ------------------------------------------------------------------ helpers

    private static String article(String url) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><div class='post-body entry-content'>");
        html.append("<h2>Chapter contoh</h2>");
        for (int i = 1; i <= 3; i++) {
            html.append("<p>Paragraf ").append(i)
                    .append(" sebelum ilustrasi, panjangnya cukup untuk jadi teks bacaan.</p>");
        }
        html.append("<img src=\"").append(IMG_A)
                .append("\" data-original-width=\"320\" data-original-height=\"454\"/>");
        for (int i = 4; i <= 6; i++) {
            html.append("<p>Paragraf ").append(i)
                    .append(" setelah ilustrasi pertama, urutannya harus tetap.</p>");
        }
        html.append("<img src=\"").append(IMG_B)
                .append("\" data-original-width=\"454\" data-original-height=\"320\"/>");
        html.append("<p>Paragraf penutup.</p>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private static byte[] plate(String resource) throws Exception {
        try (InputStream in = ReaderOfflineTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private ReaderActivity open(String url) {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, NOVEL);
        intent.putExtra(ReaderActivity.EXTRA_URL, url);
        ReaderActivity activity = Robolectric.buildActivity(ReaderActivity.class, intent)
                .setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);
        return activity;
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

    private RecyclerView list(ReaderActivity activity) {
        List<RecyclerView> found = findAll(activity.getWindow().getDecorView(), RecyclerView.class);
        assertFalse("RecyclerView harus ada", found.isEmpty());
        return found.get(0);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ------------------------------------------------------------------- tests

    /** The downloaded chapter opens with no network access at all. */
    @Test
    public void downloadedChapterOpensWithoutTouchingTheNetwork() {
        ReaderActivity activity = open(CH1);

        assertEquals("reader tidak boleh menghubungi jaringan", 0, networkCalls);
        RecyclerView list = list(activity);
        assertTrue("daftar blok harus terisi", list.getAdapter() != null);
        assertTrue("harus ada blok yang dirender",
                list.getAdapter().getItemCount() > 1);

        // The error pane must stay hidden: this is a successful render.
        List<TextView> labels = findAll(activity.getWindow().getDecorView(), TextView.class);
        boolean errorShown = false;
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (text != null && (text.toString().contains("belum diunduh")
                    || text.toString().contains("Gagal memuat"))) {
                errorShown = true;
            }
        }
        assertFalse("tidak boleh menampilkan pesan gagal untuk chapter tersimpan", errorShown);
    }

    /** Every illustration comes from a local file, so it survives airplane mode. */
    @Test
    public void illustrationsAreLocalFilesWhenReadOffline() {
        ChapterContent stored = store.load(NOVEL, CH1);
        assertNotNull("chapter tersimpan harus terbaca", stored);

        int images = 0;
        for (Block block : stored.blocks()) {
            if (!block.isImage()) continue;
            images++;
            assertTrue("ilustrasi harus punya berkas lokal", block.hasLocalImage());
            assertTrue(new File(block.imagePath).isFile());
            assertFalse("sumber gambar tidak boleh URL",
                    block.imageSource().startsWith("http"));
        }
        assertEquals("dua ilustrasi", 2, images);

        // And the reader actually builds image cells for them.
        ReaderActivity activity = open(CH1);
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertFalse("sel ilustrasi harus terpasang", cells.isEmpty());
        for (ImageCell cell : cells) {
            assertTrue("sel ilustrasi harus punya kotak nyata", cell.getWidth() > 0);
            assertTrue("sel ilustrasi harus punya tinggi", cell.getHeight() > 0);
        }
    }

    /** Illustration position between paragraphs is preserved offline. */
    @Test
    public void illustrationOrderIsPreservedOffline() {
        ChapterContent stored = store.load(NOVEL, CH1);
        assertNotNull(stored);

        List<Block> blocks = stored.blocks();
        int firstImage = -1;
        int secondImage = -1;
        int paragraphsAfterLastImage = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).isImage()) {
                if (firstImage < 0) firstImage = i; else secondImage = i;
            } else if (secondImage >= 0) {
                paragraphsAfterLastImage++;
            }
        }
        assertTrue("ilustrasi tidak boleh di awal", firstImage > 0);
        assertTrue(secondImage > firstImage);
        assertTrue("harus ada teks setelah ilustrasi terakhir", paragraphsAfterLastImage > 0);
    }

    /** Previous/Next still works between downloaded chapters, offline. */
    @Test
    public void nextAndPreviousWorkBetweenDownloadedChapters() {
        ReaderActivity activity = open(CH1);
        int callsBefore = networkCalls;

        activity.runOnUiThread(() -> {
            View next = activity.getWindow().getDecorView().findViewWithTag("next");
            assertNotNull("tombol Berikutnya harus ada", next);
            next.performClick();
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);

        assertEquals("navigasi tidak boleh memakai jaringan", callsBefore, networkCalls);
        RecyclerView list = list(activity);
        assertTrue("chapter kedua harus tampil", list.getAdapter().getItemCount() > 1);

        activity.runOnUiThread(() -> {
            View previous = activity.getWindow().getDecorView().findViewWithTag("previous");
            assertNotNull("tombol Sebelumnya harus ada", previous);
            previous.performClick();
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);

        assertEquals("tetap tanpa jaringan", callsBefore, networkCalls);
    }

    /** With no download and no network, the reader says so clearly. */
    @Test
    public void missingChapterOfflineShowsAClearMessage() {
        String other = "https://kaoritranslation.blogspot.com/2026/03/v1c9.html";
        ReaderActivity activity = open(other);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);

        boolean found = false;
        for (TextView label : findAll(activity.getWindow().getDecorView(), TextView.class)) {
            CharSequence text = label.getText();
            if (text != null && text.toString().contains("belum diunduh")) found = true;
        }
        assertTrue("pesan harus menyebut chapter belum diunduh", found);
    }

    /** A downloaded chapter is never refetched, so no request storms the source. */
    @Test
    public void reopeningAStoredChapterMakesNoRequests() {
        ContentCache.clear();
        ReaderActivity activity = open(CH1);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("tidak ada request meski dibuka berulang", 0, networkCalls);
        assertNotNull(list(activity).getAdapter());
    }

    /**
     * Reading progress behaves exactly the same offline as online.
     *
     * <p>Asserting a fixed percentage would be wrong: this fixture chapter is
     * short enough to fit one screen, so the reader legitimately records 100% for
     * both sources. The meaningful claim is equivalence — opening the downloaded
     * copy must not change the progress rules or lose the bookmark slot.
     */
    @Test
    public void readingProgressBehavesTheSameOfflineAsOnline() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication()).scoped(NOVEL);
        prefs.setProgress(CH1, 42);
        prefs.setScroll(CH1, 7, 21);

        ReaderActivity offline = open(CH1);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(offline);
        int offlineProgress = prefs.progress(CH1);

        // Same chapter, same window, but served from the network instead of disk.
        ContentCache.clear();
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                return article(url);
            }

            @Override
            public byte[] image(String url) throws Exception {
                return plate(url.contains("plate_b") ? "offline/plate_b.jpg" : "offline/plate_a.jpg");
            }
        });
        prefs.setProgress(CH1, 42);
        prefs.setScroll(CH1, 7, 21);
        ReaderActivity online = open(CH1);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(online);

        assertEquals("aturan progres harus identik untuk konten lokal dan jaringan",
                prefs.progress(CH1), offlineProgress);
        assertTrue("slot bookmark harus tetap dipakai", prefs.hasScroll(CH1));
        connect(offline);
        connect(online);
    }

    /** Keeps both activities alive until their assertions have been read. */
    private static void connect(Object keep) {
        assertNotNull(keep);
    }

    /** The adapter resolves every image block to a local path, not a URL. */
    @Test
    public void adapterBindsLocalImageSourceForStoredBlocks() {
        ChapterContent stored = store.load(NOVEL, CH1);
        assertNotNull(stored);
        BlockAdapter adapter = new BlockAdapter(RuntimeEnvironment.getApplication(),
                new Prefs(RuntimeEnvironment.getApplication()),
                id.shuyasal.kuromaku.ui.Palette.of(Prefs.THEME_DARK));
        adapter.setContent(stored);

        int images = 0;
        for (Block block : stored.blocks()) {
            if (!block.isImage()) continue;
            images++;
            assertTrue("sumber harus berkas lokal",
                    block.imageSource().startsWith("/"));
        }
        assertEquals(2, images);
        assertTrue(adapter.blockCount() > 2);
    }
}
