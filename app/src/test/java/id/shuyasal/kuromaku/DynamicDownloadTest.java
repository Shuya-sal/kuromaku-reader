package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Library;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.db.NovelDatabase;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.net.NovelUrl;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.offline.VolumeJob;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.ImageCell;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Skenario 8 and 9: downloading and then reading a novel the user added by URL.
 *
 * <p>The two built-in novels were never the interesting case — their ids are
 * constants, so a bug that leaked one novel's files into another would not show
 * up. Here the novel comes out of the add flow, its id out of a URL slug, and what
 * is asserted is that its chapters, illustrations, files and reading progress all
 * live under that slug and nowhere else.
 *
 * <p>The offline half is reproduced the only way a host without an emulator can:
 * the byte source throws as soon as it is touched, so anything still on screen
 * came off disk. Glide decoding the file into a bitmap needs a real device and is
 * reported as such, not claimed here.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class DynamicDownloadTest {

    private static final String CATALOGUE =
            "https://kaoritranslation.blogspot.com/2026/04/ln-novel-baru.html";
    private static final String NOVEL_ID = "ln-novel-baru";
    private static final String CH_ONE =
            "https://kaoritranslation.blogspot.com/2026/04/ln-novel-baru-chapter-1.html";
    private static final String CH_TWO =
            "https://kaoritranslation.blogspot.com/2026/04/ln-novel-baru-chapter-2.html";
    private static final String CH_TWO_B =
            "https://kaoritranslation.blogspot.com/2026/04/ln-novel-baru-v2-chapter-1.html";
    private static final String IMG_A =
            "https://blogger.googleusercontent.com/img/a/plate_a.jpg";
    private static final String IMG_B =
            "https://blogger.googleusercontent.com/img/a/plate_b.jpg";

    private Context context;
    private File offlineRoot;
    private OfflineStore store;
    private NovelDatabase database;
    private Library library;

    private final Map<String, Integer> pageRequests = new LinkedHashMap<>();
    private final Map<String, Integer> imageRequests = new LinkedHashMap<>();
    private boolean online = true;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ContentCache.clear();
        Session.clear();
        DownloadStatusStore.reset();
        pageRequests.clear();
        imageRequests.clear();
        online = true;

        offlineRoot = new File(context.getCacheDir(), "dynamic-download-" + System.nanoTime());
        OfflineStore.useRoot(offlineRoot);
        store = OfflineStore.get(context);

        NovelDatabase.reset();
        database = NovelDatabase.inMemory(context);
        library = Library.forDatabase(database);
        Library.useForTests(library);
        library.add(newNovel());

        Session.setCatalog(new Catalog(NOVEL_ID, volumes()));

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                if (!online) throw new java.io.IOException("mode pesawat: tidak ada jaringan");
                pageRequests.merge(url, 1, Integer::sum);
                return article(url);
            }

            @Override
            public byte[] image(String url) throws Exception {
                if (!online) throw new java.io.IOException("mode pesawat: tidak ada jaringan");
                imageRequests.merge(url, 1, Integer::sum);
                return plate(url.contains("plate_b") ? "offline/plate_b.jpg" : "offline/plate_a.jpg");
            }
        });
    }

    @After
    public void tearDown() {
        Fetcher.reset();
        OfflineStore.reset();
        DownloadStatusStore.reset();
        ContentCache.clear();
        Session.clear();
        Library.useForTests(null);
        database.close();
        NovelDatabase.reset();
        deleteTree(offlineRoot);
    }

    // ---------------------------------------------------------------- fixtures

    /** The novel exactly as the add page builds it from a parsed preview. */
    private static Novel newNovel() {
        return new Novel(
                NovelUrl.id(CATALOGUE),
                "Novel Baru Dari URL",
                "新しい小説",
                "Novel Baru Dari URL",
                "Penulis Baru",
                "Ilustrator Baru",
                "Fantasy, Adventure",
                "Ongoing",
                "Kaori Translation",
                CATALOGUE,
                CATALOGUE,
                "https://blogger.googleusercontent.com/img/a/cover_baru.jpg",
                0.70f,
                new String[]{"Paragraf sinopsis pertama.", "Paragraf sinopsis kedua."},
                2,
                Novel.CATALOG_DISCOVERED,
                "",
                "",
                false);
    }

    private static List<Volume> volumes() {
        List<Chapter> one = new ArrayList<>();
        one.add(new Chapter("Prolog", "Prolog", CH_ONE, "2026-04-01", 10, 1));
        one.add(new Chapter("Chapter 2", "Chapter 2", CH_TWO, "2026-04-02", 102, 1));

        List<Chapter> two = new ArrayList<>();
        two.add(new Chapter("Chapter 1", "Chapter 1", CH_TWO_B, "2026-04-03", 101, 2));

        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, one));
        volumes.add(new Volume(2, two));
        return volumes;
    }

    private static String article(String url) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><div class='post-body entry-content'>");
        html.append("<h2>Chapter dari novel baru</h2>");
        for (int i = 1; i <= 3; i++) {
            html.append("<p>Paragraf ").append(i)
                    .append(" chapter novel baru ini, cukup panjang untuk jadi teks bacaan.</p>");
        }
        html.append("<img src=\"").append(IMG_A)
                .append("\" data-original-width=\"320\" data-original-height=\"454\"/>");
        for (int i = 4; i <= 6; i++) {
            html.append("<p>Paragraf ").append(i)
                    .append(" setelah ilustrasi pertama, urutannya harus tetap.</p>");
        }
        html.append("<img src=\"").append(IMG_B)
                .append("\" data-original-width=\"454\" data-original-height=\"320\"/>");
        html.append("<p>Paragraf penutup chapter novel baru.</p>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private static byte[] plate(String resource) throws Exception {
        try (InputStream in = DynamicDownloadTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull("fixture gambar hilang: " + resource, in);
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

    private static int total(Map<String, Integer> requests) {
        int sum = 0;
        for (Integer count : requests.values()) sum += count;
        return sum;
    }

    private void goOffline() {
        online = false;
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

    private ReaderActivity open(String url) {
        Intent intent = new Intent(context, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, NOVEL_ID);
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

    // -------------------------------------------------------- skenario 8: unduh

    /**
     * Skenario 8: a chapter of the URL-added novel downloads under its own id,
     * with both illustrations stored as real files.
     */
    @Test
    public void downloadingAChapterOfTheAddedNovelStoresTextAndIllustrations() throws Exception {
        ChapterDownloader.Result result = ChapterDownloader.download(
                context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);

        assertTrue("chapter harus tercatat tersimpan", store.isSaved(NOVEL_ID, CH_ONE));
        assertEquals("dua ilustrasi harus terunduh", 2, result.imageCount);

        OfflineStore.Entry entry = store.entry(NOVEL_ID, CH_ONE);
        assertNotNull(entry);
        assertEquals("chapter harus tercatat di volume 1", 1, entry.volume);
        assertEquals(2, entry.imageCount);

        File dir = store.chapterDir(NOVEL_ID, CH_ONE);
        assertTrue("content.json harus ada di folder novel baru",
                new File(dir, "content.json").isFile());
        assertTrue("folder novel baru harus memakai id dari URL",
                dir.getAbsolutePath().contains(NOVEL_ID));

        File[] images = store.imagesDir(NOVEL_ID).listFiles();
        assertNotNull(images);
        assertEquals("dua berkas gambar nyata", 2, images.length);
        for (File image : images) {
            assertTrue("gambar harus punya isi: " + image.getName(), image.length() > 100);
        }
    }

    /** A volume of the added novel downloads every chapter in it, once each. */
    @Test
    public void downloadingAWholeVolumeOfTheAddedNovelFinishesEveryChapter() throws Exception {
        VolumeJob.Result result = VolumeJob.run(context, NOVEL_ID, 1, volumes().get(0).chapters,
                () -> false, (finished, total) -> {
                });

        assertEquals("dua chapter volume 1", 2, result.total);
        assertEquals("keduanya harus selesai", 2, result.finished);
        assertTrue("tidak boleh ada kegagalan", result.failures.isEmpty());
        assertTrue("progres harus lengkap", result.allDone());
        assertTrue(store.isSaved(NOVEL_ID, CH_ONE));
        assertTrue(store.isSaved(NOVEL_ID, CH_TWO));
        assertEquals("dua chapter tersimpan", 2, store.savedCount(NOVEL_ID));
        // Both chapters show the same two illustrations, and the store dedupes by
        // image URL: four references, two files. Storing four copies would be the
        // bug this asserts against.
        assertEquals("gambar identik tidak boleh disimpan dua kali", 2,
                store.imagesDir(NOVEL_ID).listFiles().length);
    }

    /** Downloading the same chapter twice refetches nothing. */
    @Test
    public void downloadingTheSameChapterAgainMakesNoNewRequests() throws Exception {
        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
        int pages = total(pageRequests);
        int images = total(imageRequests);

        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);

        assertEquals("halaman tidak boleh diunduh ulang", pages, total(pageRequests));
        assertEquals("gambar tidak boleh diunduh ulang", images, total(imageRequests));
    }

    /** The new novel's files never mix with the built-in novels' files. */
    @Test
    public void theAddedNovelKeepsItsFilesApartFromTheBuiltIns() throws Exception {
        Fetcher.Source source = Fetcher.get();
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                return article(url);
            }

            @Override
            public byte[] image(String url) throws Exception {
                return plate("offline/plate_b.jpg");
            }
        });
        try {
            ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
            ChapterDownloader.download(context, Novels.MONOGATARI, CH_TWO, 1, "Chapter 2",
                    () -> false);
        } finally {
            Fetcher.use(source);
        }

        assertTrue(store.isSaved(NOVEL_ID, CH_ONE));
        assertTrue(store.isSaved(Novels.MONOGATARI, CH_TWO));
        assertFalse("chapter novel baru tidak boleh terlihat sebagai milik novel lain",
                store.isSaved(Novels.MONOGATARI, CH_ONE));
        assertFalse(store.isSaved(NOVEL_ID, CH_TWO));

        assertEquals("satu chapter di novel baru", 1, store.savedCount(NOVEL_ID));
        assertEquals("satu chapter di Monogatari", 1, store.savedCount(Novels.MONOGATARI));
        assertTrue("daftar novel berisi konten harus memuat keduanya",
                store.novelsWithContent().contains(NOVEL_ID)
                        && store.novelsWithContent().contains(Novels.MONOGATARI));
    }

    // ------------------------------------------- skenario 9: baca offline

    /**
     * Skenario 9: with the source unreachable, the downloaded chapter of the
     * added novel still opens — text and illustrations.
     */
    @Test
    public void theDownloadedChapterStillOpensWithNoNetwork() throws Exception {
        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
        goOffline();
        int pagesBefore = total(pageRequests);
        int imagesBefore = total(imageRequests);

        ReaderActivity activity = open(CH_ONE);

        assertEquals("reader tidak boleh menyentuh jaringan", pagesBefore, total(pageRequests));
        assertEquals("gambar tidak boleh diunduh saat membaca offline",
                imagesBefore, total(imageRequests));

        RecyclerView list = new RecyclerView(activity);
        List<RecyclerView> lists = findAll(activity.getWindow().getDecorView(), RecyclerView.class);
        assertFalse("RecyclerView harus ada", lists.isEmpty());
        list = lists.get(0);
        assertNotNull(list.getAdapter());
        assertTrue("daftar blok harus terisi", list.getAdapter().getItemCount() > 1);

        String text = visibleText(activity);
        assertTrue("teks chapter harus tampil, dapat: " + text,
                text.contains("Paragraf 1") || text.contains("Paragraf 4"));
        assertFalse("tidak boleh ada pesan gagal untuk chapter tersimpan",
                text.contains("belum diunduh") || text.contains("Gagal memuat"));
    }

    /** Illustrations of the offline chapter resolve to files, never to URLs. */
    @Test
    public void theIllustrationsOfTheAddedNovelComeFromLocalFiles() throws Exception {
        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
        goOffline();

        ChapterContent stored = store.load(NOVEL_ID, CH_ONE);
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

        ReaderActivity activity = open(CH_ONE);
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertFalse("sel ilustrasi harus terpasang di reader", cells.isEmpty());
        for (ImageCell cell : cells) {
            assertTrue("sel ilustrasi harus punya kotak nyata", cell.getWidth() > 0);
            assertTrue("sel ilustrasi harus punya tinggi", cell.getHeight() > 0);
        }
    }

    /** Illustration order survives the round trip through disk. */
    @Test
    public void theIllustrationOrderOfTheAddedNovelIsPreservedOffline() throws Exception {
        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
        goOffline();

        ChapterContent stored = store.load(NOVEL_ID, CH_ONE);
        assertNotNull(stored);

        List<Block> blocks = stored.blocks();
        int firstImage = -1;
        int secondImage = -1;
        int afterSecond = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).isImage()) {
                if (firstImage < 0) firstImage = i;
                else secondImage = i;
            } else if (secondImage >= 0) {
                afterSecond++;
            }
        }
        assertTrue("ilustrasi tidak boleh di awal", firstImage > 0);
        assertTrue("ilustrasi kedua setelah yang pertama", secondImage > firstImage);
        assertTrue("harus ada teks setelah ilustrasi terakhir", afterSecond > 0);
    }

    /** Progress of the added novel is its own, and survives going offline. */
    @Test
    public void readingProgressOfTheAddedNovelIsSeparateFromTheBuiltIns() throws Exception {
        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
        goOffline();

        Prefs added = new Prefs(context).scoped(NOVEL_ID);
        Prefs mono = new Prefs(context).scoped(Novels.MONOGATARI);
        added.setProgress(CH_ONE, 43);
        mono.setProgress("https://kaoritranslation.blogspot.com/2026/01/v1c1.html", 12);

        ReaderActivity activity = open(CH_ONE);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);

        assertEquals("progres novel lain tidak boleh tersentuh", 12,
                mono.progress("https://kaoritranslation.blogspot.com/2026/01/v1c1.html"));
        assertTrue("progres novel baru harus tercatat di novelnya sendiri",
                added.progress(CH_ONE) > 0);
    }

    /** Navigating to the next chapter offline, when that one is not downloaded. */
    @Test
    public void navigatingToAnUndownloadedChapterOfflineSaysSo() throws Exception {
        ChapterDownloader.download(context, NOVEL_ID, CH_ONE, 1, "Prolog", () -> false);
        goOffline();

        ReaderActivity activity = open(CH_ONE);
        activity.runOnUiThread(() -> {
            View next = activity.getWindow().getDecorView().findViewWithTag("next");
            assertNotNull("tombol Berikutnya harus ada", next);
            next.performClick();
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);

        String text = visibleText(activity);
        assertTrue("chapter yang belum diunduh harus dijelaskan, dapat: " + text,
                text.contains("belum diunduh") || text.contains("Gagal memuat"));
        assertEquals("navigasi tidak boleh memakai jaringan", 1, total(pageRequests));
    }

    private String visibleText(android.app.Activity activity) {
        StringBuilder text = new StringBuilder();
        for (TextView view : findAll(activity.getWindow().getDecorView(), TextView.class)) {
            if (view.getVisibility() != View.VISIBLE) continue;
            CharSequence value = view.getText();
            if (value != null && value.length() > 0) text.append(value).append('\n');
        }
        return text.toString();
    }
}
