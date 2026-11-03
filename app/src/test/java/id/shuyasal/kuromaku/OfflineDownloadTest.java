package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.ImageValidator;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.offline.VolumeJob;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The offline layer, exercised end to end without a network.
 *
 * <p>Every scenario the user listed for device testing is reproduced here as far
 * as it can be without hardware: a chapter is downloaded, the source is then made
 * unreachable ("airplane mode"), the chapter is read back from disk with all its
 * illustrations, a volume is downloaded and resumed after a cancellation, the app
 * is restarted, and deleting a download is checked to leave reading progress
 * untouched.
 *
 * <p>What this class cannot prove is rendering on a real screen — Glide decoding a
 * file:// path and the actual airplane-mode toggle need a device. Those are
 * reported as device checks, not as tested here.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class OfflineDownloadTest {

    private static final String NOVEL_A = Novels.MONOGATARI;
    private static final String NOVEL_B = Novels.MAGICAL;

    private static final String IMG_1 =
            "https://blogger.googleusercontent.com/img/a/plate_a.jpg";
    private static final String IMG_2 =
            "https://blogger.googleusercontent.com/img/a/plate_b.jpg";

    private Context context;
    private File root;
    private OfflineStore store;

    /** Counts fetches so "never downloaded twice" can be asserted, not assumed. */
    private final Map<String, Integer> pageFetches = new LinkedHashMap<>();
    private final Map<String, Integer> imageFetches = new LinkedHashMap<>();
    private boolean online = true;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        root = new File(context.getCacheDir(), "offline-test-" + System.nanoTime());
        OfflineStore.useRoot(root);
        store = OfflineStore.get(context);
        DownloadStatusStore.reset();
        pageFetches.clear();
        imageFetches.clear();
        online = true;

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                if (!online) throw new java.io.IOException("offline (mode pesawat)");
                pageFetches.merge(url, 1, Integer::sum);
                return article(url);
            }

            @Override
            public byte[] image(String url) throws Exception {
                if (!online) throw new java.io.IOException("offline (mode pesawat)");
                imageFetches.merge(url, 1, Integer::sum);
                // An error page instead of a picture: the download must fail.
                if (url.contains("broken")) {
                    return "<!DOCTYPE html><html><body>404</body></html>"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                return plate(url.contains("plate_b") ? "offline/plate_b.jpg" : "offline/plate_a.jpg");
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

    /** A real Blogger-shaped article: text with two plates interleaved. */
    private static String article(String url) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><div class='post-body entry-content'>");
        html.append("<h2>Volume 1 Chapter 1</h2>");
        for (int i = 1; i <= 4; i++) {
            html.append("<p>Paragraf pembuka nomor ").append(i)
                    .append(" dari chapter ini, cukup panjang untuk dianggap teks nyata.</p>");
        }
        html.append("<img src=\"").append(IMG_1)
                .append("\" data-original-width=\"320\" data-original-height=\"454\" alt=\"ilustrasi 1\"/>");
        for (int i = 5; i <= 8; i++) {
            html.append("<p>Paragraf lanjutan nomor ").append(i)
                    .append(" setelah ilustrasi pertama, tetap dalam urutan aslinya.</p>");
        }
        html.append("<img src=\"").append(IMG_2)
                .append("\" data-original-width=\"454\" data-original-height=\"320\" alt=\"ilustrasi 2\"/>");
        html.append("<p>Paragraf penutup chapter contoh ini.</p>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private static byte[] plate(String resource) throws Exception {
        try (InputStream in = OfflineDownloadTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull("fixture gambar hilang: " + resource, in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private static Chapter chapter(String url, String label, int volume) {
        return new Chapter(label, label, url, "2026-01-01", 0, volume);
    }

    /** Total page requests, counting repeats of the same URL. */
    private int pageRequests() {
        int total = 0;
        for (Integer count : pageFetches.values()) total += count;
        return total;
    }

    /** Total image requests, counting repeats of the same URL. */
    private int imageRequests() {
        int total = 0;
        for (Integer count : imageFetches.values()) total += count;
        return total;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** Downloads one chapter synchronously, the way the worker would. */
    private void download(String novelId, int volume, String url, String label) throws Exception {
        ChapterDownloader.download(context, novelId, url, volume, label, () -> false);
    }

    // ---------------------------------------------------------------- scenario 1

    /** Skenario 1: unduh satu chapter — teks dan ilustrasi benar-benar tersimpan. */
    @Test
    public void downloadingOneChapterStoresTextAndIllustrations() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");

        assertTrue("chapter harus tercatat tersimpan", store.isSaved(NOVEL_A, url));
        OfflineStore.Entry entry = store.entry(NOVEL_A, url);
        assertNotNull(entry);
        assertEquals("dua ilustrasi harus tercatat", 2, entry.imageCount);

        File dir = store.chapterDir(NOVEL_A, url);
        assertTrue("content.json harus ada", new File(dir, "content.json").isFile());

        // Illustrations are files on disk, never bare URLs in the chapter file.
        File[] images = store.imagesDir(NOVEL_A).listFiles();
        assertNotNull(images);
        assertEquals("dua berkas gambar unik", 2, images.length);
        for (File image : images) {
            assertTrue("berkas gambar harus berisi", image.length() > 1000);
            assertTrue("harus gambar nyata, bukan HTML",
                    ImageValidator.extensionOf(readAll(image)) != null);
        }
    }

    /** Skenario 2 + 3: mode pesawat, lalu chapter dibaca sampai akhir. */
    @Test
    public void storedChapterIsReadableWithNoNetworkAtAll() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");

        online = false; // airplane mode
        int fetchedBefore = pageFetches.size() + imageFetches.size();

        ChapterContent stored = store.load(NOVEL_A, url);
        assertNotNull("chapter tersimpan harus terbaca tanpa jaringan", stored);
        assertFalse("tidak boleh ada request saat offline", stored.blocks().isEmpty());
        assertEquals("tidak ada request jaringan saat membaca offline",
                fetchedBefore, pageFetches.size() + imageFetches.size());

        // Order preserved: text, image, text, image, closing text.
        List<Block> blocks = stored.blocks();
        int firstImage = -1;
        int secondImage = -1;
        int paragraphsAfterSecond = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block.isImage()) {
                if (firstImage < 0) firstImage = i; else secondImage = i;
            } else if (secondImage >= 0) {
                paragraphsAfterSecond++;
            }
        }
        assertTrue("ilustrasi pertama harus berada di antara paragraf", firstImage > 0);
        assertTrue("ilustrasi kedua harus setelah yang pertama", secondImage > firstImage);
        assertTrue("harus ada paragraf setelah ilustrasi terakhir", paragraphsAfterSecond > 0);
    }

    /** Skenario 4: semua ilustrasi tampil — berkas lokal ada dan ukurannya dipertahankan. */
    @Test
    public void everyIllustrationResolvesToALocalFile() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");
        online = false;

        ChapterContent stored = store.load(NOVEL_A, url);
        assertNotNull(stored);
        int images = 0;
        for (Block block : stored.blocks()) {
            if (!block.isImage()) continue;
            images++;
            assertTrue("setiap ilustrasi harus punya berkas lokal", block.hasLocalImage());
            File file = new File(block.imagePath);
            assertTrue("berkas ilustrasi harus ada: " + block.imagePath, file.isFile());
            assertTrue("berkas ilustrasi harus berisi", file.length() > 1000);
            // imageSource() is what the reader loads: it must be the file, not the URL.
            assertEquals(block.imagePath, block.imageSource());
            assertFalse("sumber gambar tidak boleh berupa URL",
                    block.imageSource().startsWith("http"));
            // Declared size survives the round trip, so the reader can reserve the box.
            assertTrue("ukuran asli harus tersimpan", block.hasImageSize());
        }
        assertEquals("dua ilustrasi", 2, images);
    }

    // ---------------------------------------------------------------- scenario 5

    /** Skenario 5: unduh satu volume secara berurutan, dengan progres. */
    @Test
    public void downloadingAVolumeStoresEveryChapterInOrder() throws Exception {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c1.html", "Chapter 1", 1),
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c2.html", "Chapter 2", 1),
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c3.html", "Chapter 3", 1)));

        List<int[]> progress = new ArrayList<>();
        VolumeJob.Result result = VolumeJob.run(context, NOVEL_A, 1, chapters,
                () -> false, (finished, total) -> progress.add(new int[]{finished, total}));

        assertTrue("volume harus selesai", result.allDone());
        assertEquals(3, result.finished);
        assertEquals(3, result.total);
        assertEquals("tiga chapter tersimpan", 3, store.savedCountInVolume(NOVEL_A, 1));

        assertFalse("progres harus dilaporkan", progress.isEmpty());
        assertEquals("setiap laporan memakai total yang benar", 3, progress.get(0)[1]);
        assertEquals("progres terakhir harus penuh", 3,
                progress.get(progress.size() - 1)[0]);

        // Progress is reported incrementally, never jumping straight to the end.
        assertEquals("progres pertama harus 1 chapter", 1, progress.get(0)[0]);
    }

    /** Skenario 6: tutup dan buka kembali aplikasi — unduhan tetap ada. */
    @Test
    public void downloadsSurviveAnAppRestart() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");

        // Simulate a fresh process: singleton dropped, index read back from disk.
        OfflineStore.reset();
        // A fresh process resolves the same directory from filesDir; the test root
        // stands in for it so the reopened store reads exactly the same disk.
        OfflineStore.useRoot(root);
        OfflineStore reopened = OfflineStore.get(context);

        assertTrue("unduhan harus tetap ada setelah aplikasi dibuka ulang",
                reopened.isSaved(NOVEL_A, url));
        ChapterContent stored = reopened.load(NOVEL_A, url);
        assertNotNull("isi harus terbaca setelah restart", stored);
        assertFalse(stored.blocks().isEmpty());
        assertTrue("byte terpakai harus > 0", reopened.bytesOf(NOVEL_A) > 0);
        assertEquals("entri index harus pulih", 1, reopened.savedCount(NOVEL_A));
    }

    /** Skenario 8 + 9: batalkan di tengah, lalu ulangi tanpa duplikasi. */
    @Test
    public void cancellingMidwayKeepsFinishedChaptersAndResumeSkipsThem() throws Exception {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c1.html", "Chapter 1", 1),
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c2.html", "Chapter 2", 1),
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c3.html", "Chapter 3", 1)));

        // Cancel after the first chapter has been written.
        // Cancel as soon as the first chapter has been committed. Driving this
        // from the progress callback keeps it deterministic: the downloader polls
        // the flag several times per chapter, so a plain call counter would stop
        // it mid-chapter instead of between chapters.
        final boolean[] stop = {false};
        VolumeJob.Result cancelled = VolumeJob.run(context, NOVEL_A, 1, chapters,
                () -> stop[0], (finished, total) -> {
                    if (finished >= 1) stop[0] = true;
                });

        assertTrue("job harus berhenti karena dibatalkan", cancelled.cancelled);
        assertTrue("chapter pertama tetap tersimpan", store.isSaved(NOVEL_A, chapters.get(0).url));
        assertFalse("chapter kedua belum tersimpan", store.isSaved(NOVEL_A, chapters.get(1).url));

        // Resume: only the missing chapters may be fetched.
        pageFetches.clear();
        imageFetches.clear();

        VolumeJob.Result resumed = VolumeJob.run(context, NOVEL_A, 1, chapters, () -> false, null);

        assertTrue("resume harus selesai", resumed.allDone());
        assertEquals("hanya dua chapter yang perlu diunduh ulang", 2, pageRequests());
        assertFalse("chapter pertama tidak boleh diunduh ulang",
                pageFetches.containsKey(chapters.get(0).url));
        assertEquals("total tetap tiga chapter", 3, store.savedCountInVolume(NOVEL_A, 1));

        // No duplicated illustration files after the resume.
        File[] images = store.imagesDir(NOVEL_A).listFiles();
        assertNotNull(images);
        assertEquals("gambar tidak boleh terduplikasi", 2, images.length);
    }

    /** Downloading the same chapter twice must not create a second copy. */
    @Test
    public void redownloadingAChapterIsANoOp() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");
        long sizeAfterFirst = store.bytesOf(NOVEL_A);
        int requestsAfterFirst = pageRequests() + imageRequests();

        download(NOVEL_A, 1, url, "Chapter 1");

        assertEquals("ukuran tidak boleh bertambah", sizeAfterFirst, store.bytesOf(NOVEL_A));
        // Counting repeats matters: distinct-URL counts stay flat even when the
        // same chapter is fetched again, which hides a missing "already saved" guard.
        assertEquals("tidak boleh ada request ulang", requestsAfterFirst,
                pageRequests() + imageRequests());
    }

    // ---------------------------------------------------------------- scenario 10

    /** Skenario 10: hapus unduhan, progres membaca tetap ada. */
    @Test
    public void deletingDownloadsKeepsReadingProgressAndBookmarks() throws Exception {
        Prefs prefs = new Prefs(context).scoped(NOVEL_A);
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c1.html", "Chapter 1", 1),
                chapter("https://kaoritranslation.blogspot.com/2026/01/v1c2.html", "Chapter 2", 1)));
        VolumeJob.run(context, NOVEL_A, 1, chapters, () -> false, null);

        prefs.setProgress(chapters.get(0).url, 64);
        prefs.setScroll(chapters.get(0).url, 12, 40);
        prefs.setLastUrl(chapters.get(1).url);

        int removed = store.deleteVolume(NOVEL_A, 1);

        assertEquals("dua chapter harus terhapus", 2, removed);
        assertEquals("tidak ada chapter tersisa", 0, store.savedCount(NOVEL_A));
        assertEquals("folder chapter harus bersih", 0,
                new File(store.novelDir(NOVEL_A), "chapters").listFiles() == null ? 0
                        : new File(store.novelDir(NOVEL_A), "chapters").listFiles().length);

        assertEquals("progres membaca harus tetap 64%", 64,
                prefs.progress(chapters.get(0).url));
        assertEquals("posisi scroll harus tetap", 12, prefs.scrollPosition(chapters.get(0).url));
        assertEquals("chapter terakhir harus tetap tercatat", chapters.get(1).url, prefs.lastUrl());
    }

    /** A single chapter can be deleted without touching its volume neighbours. */
    @Test
    public void deletingOneChapterLeavesTheRestOfTheVolume() throws Exception {
        String first = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        String second = "https://kaoritranslation.blogspot.com/2026/01/v1c2.html";
        download(NOVEL_A, 1, first, "Chapter 1");
        download(NOVEL_A, 1, second, "Chapter 2");

        store.deleteChapter(NOVEL_A, first);

        assertFalse(store.isSaved(NOVEL_A, first));
        assertTrue(store.isSaved(NOVEL_A, second));
        assertEquals(1, store.savedCountInVolume(NOVEL_A, 1));
    }

    // ------------------------------------------------------------------ integrity

    /** An HTML error page where a picture should be must fail the chapter. */
    @Test
    public void imageThatIsNotAPictureFailsTheChapterInsteadOfSavingIt() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/broken.html";
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String target) {
                return article(target).replace(IMG_1,
                        "https://blogger.googleusercontent.com/img/broken.html");
            }

            @Override
            public byte[] image(String target) throws Exception {
                return "<!DOCTYPE html><html><body>rate limited</body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        });

        try {
            download(NOVEL_A, 1, url, "Chapter 1");
            throw new AssertionError("download seharusnya gagal");
        } catch (java.io.IOException expected) {
            assertTrue("alasan harus menyebut HTML, dapat: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("html"));
        }
        assertFalse("chapter tidak boleh ditandai tersimpan", store.isSaved(NOVEL_A, url));
    }

    /** A page with no readable body must not be stored as an empty chapter. */
    @Test
    public void emptyArticleIsRejected() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/empty.html";
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String target) {
                return "<html><body><div class='post-body entry-content'></div></body></html>";
            }

            @Override
            public byte[] image(String target) {
                return new byte[0];
            }
        });

        try {
            download(NOVEL_A, 1, url, "Chapter 1");
            throw new AssertionError("chapter kosong seharusnya ditolak");
        } catch (Exception expected) {
            // The parser refuses an empty body; the download must not reach disk.
            assertNotNull("kegagalan harus punya alasan", expected.getMessage());
        }
        assertFalse(store.isSaved(NOVEL_A, url));
    }

    /** A leftover .tmp file (interrupted write) is never treated as a saved chapter. */
    @Test
    public void interruptedWriteIsNotConsideredSaved() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        File dir = store.chapterDir(NOVEL_A, url);
        assertTrue(dir.mkdirs());
        // Half a JSON document, exactly what a killed process would leave.
        OfflineStore.writeAtomically(new File(dir, "content.json.tmp"),
                "{\"title\":\"separuh".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertFalse("file .tmp tidak boleh dianggap tersimpan", store.isSaved(NOVEL_A, url));
        assertNull("tidak ada chapter yang bisa dibaca", store.load(NOVEL_A, url));
    }

    /** A truncated content.json is reported unreadable, not rendered as broken text. */
    @Test
    public void truncatedChapterFileIsReportedUnreadable() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");
        File content = new File(store.chapterDir(NOVEL_A, url), "content.json");
        OfflineStore.writeAtomically(content, "{\"blocks\":[".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));

        assertNull("isi rusak harus dibaca sebagai tidak tersedia", store.load(NOVEL_A, url));
    }

    // ------------------------------------------------------------------ two novels

    /** Both novels are stored side by side and can never mix. */
    @Test
    public void twoNovelsNeverShareOfflineContent() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");
        download(NOVEL_B, 1, url, "Chapter 1");

        assertTrue(store.isSaved(NOVEL_A, url));
        assertTrue(store.isSaved(NOVEL_B, url));
        assertFalse("kunci chapter harus berbeda antar novel",
                OfflineStore.keyFor(NOVEL_A, url).equals(OfflineStore.keyFor(NOVEL_B, url)));
        assertTrue("folder novel harus terpisah",
                !store.novelDir(NOVEL_A).equals(store.novelDir(NOVEL_B)));

        store.deleteChapter(NOVEL_A, url);

        assertFalse("chapter novel A harus terhapus", store.isSaved(NOVEL_A, url));
        assertTrue("menghapus di novel A tidak boleh menyentuh novel B",
                store.isSaved(NOVEL_B, url));
        assertEquals(0, store.savedCount(NOVEL_A));
        assertEquals(1, store.savedCount(NOVEL_B));
    }

    /** Storage usage is measured from disk, so it drops after a delete. */
    @Test
    public void storageUsageIsReportedAndFreed() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        download(NOVEL_A, 1, url, "Chapter 1");
        long used = store.totalBytes();
        assertTrue("penggunaan penyimpanan harus > 0", used > 0);
        assertTrue("format ukuran harus terbaca",
                OfflineStore.formatSize(used).matches("\\d+([.,]\\d+)? (B|KB|MB|GB)"));

        store.deleteVolume(NOVEL_A, 1);
        assertEquals("penyimpanan harus kosong setelah dihapus", 0, store.totalBytes());
    }

    /** Downloaded chapters are listed per volume for the "Unduhan" page. */
    @Test
    public void downloadsPageDataGroupsByNovelAndVolume() throws Exception {
        download(NOVEL_A, 1, "https://kaoritranslation.blogspot.com/2026/01/a1.html", "Chapter 1");
        download(NOVEL_A, 2, "https://kaoritranslation.blogspot.com/2026/01/a2.html", "Chapter 1");
        download(NOVEL_B, 1, "https://kaoritranslation.blogspot.com/2026/01/b1.html", "Chapter 1");

        assertEquals(2, store.savedCount(NOVEL_A));
        assertEquals(1, store.savedCount(NOVEL_B));
        assertEquals(1, store.savedCountInVolume(NOVEL_A, 1));
        assertEquals(1, store.savedCountInVolume(NOVEL_A, 2));
        assertEquals("dua volume terdeteksi", 2, store.byVolume(NOVEL_A).size());
        assertTrue(store.novelsWithContent().contains(NOVEL_A));
    }

    // ------------------------------------------------------------------ statuses

    /** All four states the list must show are reachable and distinct. */
    @Test
    public void statusStoreReportsEveryRequiredState() throws Exception {
        String url = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
        String key = DownloadStatusStore.key(NOVEL_A, url);

        assertEquals(DownloadStatusStore.NOT_DOWNLOADED,
                DownloadStatusStore.resolve(NOVEL_A, url, store));

        DownloadStatusStore.set(key, DownloadStatusStore.DOWNLOADING, null);
        assertEquals(DownloadStatusStore.DOWNLOADING,
                DownloadStatusStore.resolve(NOVEL_A, url, store));

        DownloadStatusStore.set(key, DownloadStatusStore.FAILED, "HTTP 500");
        assertEquals(DownloadStatusStore.FAILED,
                DownloadStatusStore.resolve(NOVEL_A, url, store));
        assertEquals("HTTP 500", DownloadStatusStore.message(key));

        DownloadStatusStore.clear(key);
        download(NOVEL_A, 1, url, "Chapter 1");
        // Disk wins once the file exists, even with no live state: that is what
        // makes the status correct after a restart.
        assertEquals(DownloadStatusStore.SAVED,
                DownloadStatusStore.resolve(NOVEL_A, url, store));
    }

    // ------------------------------------------------------------------- utils

    private static byte[] readAll(File file) throws Exception {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }
}
