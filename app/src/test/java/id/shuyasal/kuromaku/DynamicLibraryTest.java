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
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Library;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.NovelMapper;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.db.NovelDatabase;
import id.shuyasal.kuromaku.db.NovelRecord;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.net.NovelUrl;
import id.shuyasal.kuromaku.offline.ChapterDownloader;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The shelf itself: what survives a restart, what a duplicate does, and what a
 * deletion is allowed to touch.
 *
 * <p>Every test here runs against the real Room database, not a stand-in — the
 * in-memory instance is the same generated implementation the device uses, so the
 * primary-key rule that refuses a duplicate and the metadata update statement are
 * exercised as they will actually run. What the in-memory instance cannot show is
 * a process restart, so that scenario is simulated the only honest way available:
 * the library object is thrown away and a new one is built over a database file
 * that was closed and reopened.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class DynamicLibraryTest {

    private static final String ADDED_URL =
            "https://kaoritranslation.blogspot.com/2026/02/novel-uji.html";
    private static final String CHAPTER_1 =
            "https://kaoritranslation.blogspot.com/2026/02/novel-uji-chapter-1.html";
    private static final String CHAPTER_2 =
            "https://kaoritranslation.blogspot.com/2026/02/novel-uji-chapter-2.html";
    private static final String MONO_CHAPTER =
            "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
    private static final String IMG = "https://blogger.googleusercontent.com/img/a/plate_a.jpg";

    private Context context;
    private NovelDatabase database;
    private Library library;
    private File offlineRoot;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        NovelDatabase.reset();
        database = NovelDatabase.inMemory(context);
        library = Library.forDatabase(database);
        Library.useForTests(library);

        offlineRoot = new File(context.getCacheDir(), "dynamic-library-" + System.nanoTime());
        OfflineStore.useRoot(offlineRoot);
    }

    @After
    public void tearDown() {
        Fetcher.reset();
        OfflineStore.reset();
        Library.useForTests(null);
        database.close();
        NovelDatabase.reset();
        deleteTree(offlineRoot);
    }

    // ---------------------------------------------------------------- helpers

    /** A novel as the add page would build it from a parsed preview. */
    private Novel added() {
        return new Novel(
                NovelUrl.id(ADDED_URL),
                "Novel Uji Format Berbeda",
                "小説テスト",
                "Novel Uji Format Berbeda",
                "Penulis Uji",
                "Ilustrator Uji",
                "Fantasy, Adventure",
                "Ongoing",
                "Kaori Translation",
                ADDED_URL,
                ADDED_URL,
                "https://blogger.googleusercontent.com/img/a/cover_uji.jpg",
                0.70f,
                new String[]{"Paragraf satu.", "Paragraf dua."},
                2,
                Novel.CATALOG_DISCOVERED,
                "",
                "",
                false);
    }

    private static List<Volume> volumesOf(List<Chapter> chapters) {
        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, chapters));
        return volumes;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String article() {
        return "<html><body><div class='post-body entry-content'>"
                + "<h2>Volume 1 Chapter 1</h2>"
                + "<p>Paragraf pertama chapter contoh yang cukup panjang untuk dibaca.</p>"
                + "<p>Paragraf kedua chapter contoh ini juga cukup panjang.</p>"
                + "<img src=\"" + IMG + "\" data-original-width=\"320\" data-original-height=\"454\"/>"
                + "<p>Paragraf penutup chapter contoh.</p>"
                + "</div></body></html>";
    }

    private static byte[] plate() throws Exception {
        try (InputStream in = DynamicLibraryTest.class.getClassLoader()
                .getResourceAsStream("offline/plate_a.jpg")) {
            assertNotNull("fixture gambar hilang", in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private void servePages() {
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                return article();
            }

            @Override
            public byte[] image(String url) throws Exception {
                return plate();
            }
        });
    }

    // ------------------------------------------------- skenario 1 dan 12: seed

    /**
     * Skenario 12 (first half): upgrading from the version that shipped two fixed
     * novels must not lose them. An empty table is seeded with both, and the
     * metadata is the verified one rather than a placeholder.
     */
    @Test
    public void anEmptyShelfIsSeededWithBothBuiltInNovels() {
        List<Novel> all = library.all();

        assertEquals("dua novel bawaan harus ada", 2, all.size());
        Novel monogatari = library.byId(Novels.MONOGATARI);
        Novel magical = library.byId(Novels.MAGICAL);
        assertNotNull(monogatari);
        assertNotNull(magical);
        assertTrue("novel bawaan harus ditandai", monogatari.builtIn && magical.builtIn);
        assertEquals("Yuuki Ryou", monogatari.author);
        assertEquals("Irisu", magical.author);
        // Monogatari's catalogue comes from the source's own list article, with the
        // label feed kept alongside it to repair links and pick up new chapters.
        assertEquals("katalog Monogatari lewat artikel + feed",
                Novel.CATALOG_ARTICLE_FEED, monogatari.catalogMode);
        assertEquals("label feed cadangan Monogatari",
                "Monogatari no Kuromaku", monogatari.feedLabel);
        assertEquals("katalog Magical lewat landing", Novel.CATALOG_LANDING, magical.catalogMode);
    }

    /** Seeding happens once: opening the shelf twice cannot double the rows. */
    @Test
    public void openingTheShelfTwiceDoesNotDuplicateTheBuiltIns() {
        library.all();
        library.reload();
        library.all();
        library.reload();
        assertEquals(2, library.count());
    }

    // ---------------------------------------- skenario 1: add a novel by URL

    /** Skenario 1: a valid catalogue is stored with its own id and metadata. */
    @Test
    public void aValidNovelIsStoredWithItsOwnIdentity() {
        Library.Result result = library.add(added());

        assertEquals(Library.Added.ADDED, result.status);
        assertNotNull(result.novel);
        assertEquals("novel-uji", result.novel.id);
        assertEquals(3, library.count());

        Novel stored = library.byId("novel-uji");
        assertNotNull(stored);
        assertEquals("Novel Uji Format Berbeda", stored.title);
        assertEquals("Penulis Uji", stored.author);
        assertEquals(ADDED_URL, stored.landingUrl);
        assertFalse("novel baru bukan novel bawaan", stored.builtIn);
        assertEquals(Novel.CATALOG_DISCOVERED, stored.catalogMode);
    }

    /** The new novel is appended after the built-ins, so their order is kept. */
    @Test
    public void aNewNovelIsAppendedAfterTheBuiltIns() {
        library.add(added());
        List<Novel> all = library.all();

        assertEquals(Novels.MONOGATARI, all.get(0).id);
        assertEquals(Novels.MAGICAL, all.get(1).id);
        assertEquals("novel-uji", all.get(2).id);
    }

    // ------------------------------------------- skenario 2: the same URL twice

    /**
     * Skenario 2: adding the same page again is refused and changes nothing.
     *
     * <p>The second attempt carries a different title, because that is what a
     * second fetch of a slightly different page could produce. The row count must
     * not change and the stored metadata must not be overwritten.
     */
    @Test
    public void addingTheSameUrlTwiceCreatesNoSecondNovel() {
        library.add(added());
        int before = library.count();

        Novel again = new Novel(
                NovelUrl.id(ADDED_URL),
                "Judul Berbeda Dari Halaman Yang Sama",
                "", "", "Penulis Lain", "", "", "",
                "Kaori Translation", ADDED_URL, ADDED_URL, "", 0.70f,
                new String[0], 2, Novel.CATALOG_DISCOVERED, "", "", false);

        Library.Result second = library.add(again);

        assertEquals(Library.Added.DUPLICATE, second.status);
        assertEquals("tidak boleh ada baris baru", before, library.count());
        assertEquals("judul tersimpan tidak boleh tertimpa",
                "Novel Uji Format Berbeda", library.byId("novel-uji").title);
        assertNotNull("pesan duplikat harus bisa dibaca user", second.message);
        assertTrue(second.message.length() > 10);
    }

    /** The duplicate check works on the normalised URL, not on the id alone. */
    @Test
    public void theDuplicateCheckUsesTheNormalisedUrl() throws Exception {
        library.add(added());
        String messy = ADDED_URL + "?m=1";
        assertEquals("URL dengan ?m=1 harus menunjuk novel yang sama",
                "novel-uji", library.byUrl(NovelUrl.normalize(messy)).id);
    }

    // ----------------------------- skenario 6: survived closing the application

    /**
     * Skenario 6: the shelf survives a restart.
     *
     * <p>A process restart is simulated the way it actually behaves: the database
     * is closed, a brand-new instance is opened over the same file, and a fresh
     * library is built over it. Nothing is carried in memory, so what appears here
     * came off disk.
     */
    @Test
    public void theShelfIsStillThereAfterTheAppIsClosedAndReopened() {
        File file = new File(context.getCacheDir(), "restart-" + System.nanoTime() + ".db");
        NovelDatabase first = androidx.room.Room.databaseBuilder(
                context, NovelDatabase.class, file.getAbsolutePath())
                .allowMainThreadQueries().build();
        Library before = Library.forDatabase(first);
        before.add(added());
        assertEquals(3, before.count());
        first.close();

        NovelDatabase second = androidx.room.Room.databaseBuilder(
                context, NovelDatabase.class, file.getAbsolutePath())
                .allowMainThreadQueries().build();
        try {
            Library after = Library.forDatabase(second);
            assertEquals("tiga novel harus tetap ada setelah dibuka ulang", 3, after.count());
            Novel stored = after.byId("novel-uji");
            assertNotNull("novel tambahan harus tetap ada", stored);
            assertEquals("Novel Uji Format Berbeda", stored.title);
            assertEquals("Penulis Uji", stored.author);
            assertEquals("cover harus tetap tersimpan",
                    "https://blogger.googleusercontent.com/img/a/cover_uji.jpg", stored.coverUrl);
            assertEquals("sinopsis harus tetap tersimpan", 2, stored.synopsis.length);
            assertEquals("dua novel bawaan harus tetap ada",
                    Novels.MONOGATARI, after.all().get(0).id);
            assertEquals(Novels.MAGICAL, after.all().get(1).id);
        } finally {
            second.close();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    // ------------------------------ skenario 7: refresh keeps identity, progress

    /**
     * Skenario 7: a refresh updates the metadata a page actually printed, keeps
     * every field the page did not, and never changes the id or the library order.
     */
    @Test
    public void refreshingAStoredNovelUpdatesMetadataWithoutTouchingIdentity() {
        library.add(added());
        Prefs prefs = new Prefs(context).scoped("novel-uji");
        prefs.setProgress(CHAPTER_1, 37);
        prefs.setScroll(CHAPTER_1, 12, 40);
        prefs.setLastUrl(CHAPTER_1);

        Novel refreshed = new Novel(
                "novel-uji",
                "Novel Uji (Judul Diperbarui)",
                "",
                "Novel Uji (Judul Diperbarui)",
                "Penulis Uji",
                "",
                "Fantasy",
                "Completed",
                "Kaori Translation",
                ADDED_URL, ADDED_URL,
                "",
                0f,
                new String[0],
                2, Novel.CATALOG_DISCOVERED, "", "", false);

        Novel updated = library.updateMetadata(refreshed);

        assertNotNull(updated);
        assertEquals("judul baru harus tersimpan",
                "Novel Uji (Judul Diperbarui)", updated.title);
        assertEquals("genre baru harus tersimpan", "Fantasy", updated.genres);
        assertEquals("status baru harus tersimpan", "Completed", updated.status);
        assertEquals("ilustrator lama tidak boleh terhapus", "Ilustrator Uji", updated.illustrator);
        assertEquals("cover lama tidak boleh terhapus",
                "https://blogger.googleusercontent.com/img/a/cover_uji.jpg", updated.coverUrl);
        assertEquals("sinopsis lama tidak boleh terhapus", 2, updated.synopsis.length);
        assertEquals("id tidak boleh berubah", "novel-uji", updated.id);
        assertEquals("posisi di perpustakaan tidak boleh berubah",
                "novel-uji", library.all().get(2).id);
        assertEquals("progres membaca tidak boleh hilang", 37, prefs.progress(CHAPTER_1));
        assertEquals("posisi scroll tidak boleh hilang", 12, prefs.scrollPosition(CHAPTER_1));
    }

    /** A refresh of one novel must not disturb another novel's progress. */
    @Test
    public void refreshingOneNovelLeavesTheOtherNovelsAlone() {
        library.add(added());
        Prefs magical = new Prefs(context).scoped(Novels.MAGICAL);
        Prefs addedPrefs = new Prefs(context).scoped("novel-uji");
        magical.setProgress(MONO_CHAPTER, 61);
        addedPrefs.setProgress(CHAPTER_2, 18);

        library.updateMetadata(new Novel(
                "novel-uji", "Judul Baru", "", "Judul Baru", "", "", "", "", "Kaori Translation",
                ADDED_URL, ADDED_URL, "", 0f, new String[0], 2,
                Novel.CATALOG_DISCOVERED, "", "", false));

        assertEquals("progres novel lain harus utuh", 61, magical.progress(MONO_CHAPTER));
        assertEquals("progres novel yang diperbarui harus utuh", 18,
                addedPrefs.progress(CHAPTER_2));
        assertEquals("jumlah novel tetap tiga", 3, library.count());
    }

    // ------------------------------- skenario 10: delete keeps progress

    /**
     * Skenario 10: removing the shelf entry keeps the downloads and the progress.
     *
     * <p>This is the first of the two documented choices. The row disappears, but
     * the chapter stays on disk and the bookmark is still readable, so re-adding
     * the same novel continues where the reader stopped.
     */
    @Test
    public void deletingFromTheShelfKeepsDownloadsAndProgress() throws Exception {
        library.add(added());
        Prefs prefs = new Prefs(context).scoped("novel-uji");
        prefs.setProgress(CHAPTER_1, 64);
        prefs.setLastUrl(CHAPTER_1);

        servePages();
        ChapterDownloader.download(context, "novel-uji", CHAPTER_1, 1, "Chapter 1", () -> false);
        OfflineStore store = OfflineStore.get(context);
        assertTrue("prasyarat: chapter harus tersimpan", store.isSaved("novel-uji", CHAPTER_1));

        // The documented first choice: shelf only.
        assertTrue(library.remove("novel-uji"));

        assertEquals("novel harus hilang dari perpustakaan", 2, library.count());
        assertNull(library.byId("novel-uji"));
        assertTrue("unduhan harus tetap ada", store.isSaved("novel-uji", CHAPTER_1));
        assertEquals("progres membaca harus tetap ada", 64, prefs.progress(CHAPTER_1));
        assertEquals("chapter terakhir harus tetap tercatat", CHAPTER_1, prefs.lastUrl());
    }

    // ------------------- skenario 11: delete takes the offline data with it

    /**
     * Skenario 11: removing the novel together with its downloads deletes that
     * novel's files and nothing else. The progress is removed only when the second
     * checkbox was ticked, which is what the caller decides.
     */
    @Test
    public void deletingWithDownloadsRemovesOnlyThatNovelsFiles() throws Exception {
        library.add(added());
        servePages();
        OfflineStore store = OfflineStore.get(context);
        ChapterDownloader.download(context, "novel-uji", CHAPTER_1, 1, "Chapter 1", () -> false);
        ChapterDownloader.download(context, Novels.MONOGATARI, CHAPTER_2, 1, "Chapter 2",
                () -> false);

        Prefs addedPrefs = new Prefs(context).scoped("novel-uji");
        Prefs monoPrefs = new Prefs(context).scoped(Novels.MONOGATARI);
        addedPrefs.setProgress(CHAPTER_1, 55);
        monoPrefs.setProgress(CHAPTER_2, 22);

        // What the dialog does: offline files go, the shelf row goes, the progress
        // goes only because the second checkbox was ticked.
        store.deleteNovel("novel-uji");
        addedPrefs.clearNovelData();
        library.remove("novel-uji");

        assertFalse("unduhan novel yang dihapus harus hilang",
                store.isSaved("novel-uji", CHAPTER_1));
        assertTrue("unduhan novel lain harus utuh",
                store.isSaved(Novels.MONOGATARI, CHAPTER_2));
        assertEquals("progres novel lain harus utuh", 22, monoPrefs.progress(CHAPTER_2));
        assertFalse("progres novel yang dihapus harus hilang", addedPrefs.hasNovelData());
        assertEquals("novel lain tetap ada", 2, library.count());
    }

    /** Deleting the offline copy alone leaves both the shelf and the progress. */
    @Test
    public void deletingOnlyTheDownloadsKeepsTheNovelAndItsProgress() throws Exception {
        library.add(added());
        servePages();
        OfflineStore store = OfflineStore.get(context);
        ChapterDownloader.download(context, "novel-uji", CHAPTER_1, 1, "Chapter 1", () -> false);
        Prefs prefs = new Prefs(context).scoped("novel-uji");
        prefs.setProgress(CHAPTER_1, 71);

        store.deleteNovel("novel-uji");

        assertFalse(store.isSaved("novel-uji", CHAPTER_1));
        assertNotNull("novel harus tetap di perpustakaan", library.byId("novel-uji"));
        assertEquals("progres harus tetap ada", 71, prefs.progress(CHAPTER_1));
    }

    // ------------------------------------ skenario 12: the built-ins still work

    /**
     * Skenario 12: after adding and deleting another novel, both built-in novels
     * still carry their own catalogues and their own reading progress.
     */
    @Test
    public void theTwoBuiltInNovelsKeepWorkingAfterTheShelfChanges() {
        library.add(added());

        List<Chapter> monoChapters = new ArrayList<>();
        monoChapters.add(new Chapter("Chapter 1", "Chapter 1", MONO_CHAPTER,
                "2026-01-01", 11, 1));
        Session.setCatalog(new Catalog(Novels.MONOGATARI, volumesOf(monoChapters)));

        List<Chapter> addedChapters = new ArrayList<>();
        addedChapters.add(new Chapter("Chapter 1", "Chapter 1", CHAPTER_1,
                "2026-02-01", 101, 1));
        Session.setCatalog(new Catalog("novel-uji", volumesOf(addedChapters)));

        Prefs monoPrefs = new Prefs(context).scoped(Novels.MONOGATARI);
        monoPrefs.setProgress(MONO_CHAPTER, 48);

        library.remove("novel-uji");
        Session.forget("novel-uji");

        assertEquals("dua novel bawaan harus tetap ada", 2, library.count());
        assertNotNull("katalog Monogatari tidak boleh ikut hilang",
                Session.catalog(Novels.MONOGATARI));
        assertEquals("chapter Monogatari harus tetap terbaca", 1,
                Session.catalog(Novels.MONOGATARI).size());
        assertNull("katalog novel yang dihapus harus dilupakan",
                Session.catalog("novel-uji"));
        assertEquals("progres novel bawaan harus utuh", 48, monoPrefs.progress(MONO_CHAPTER));
        assertEquals("novel bawaan tidak boleh berubah mode",
                Novel.CATALOG_LANDING, library.byId(Novels.MAGICAL).catalogMode);
    }

    // --------------------------------------------------------- data integrity

    /** Every stored column comes back unchanged; nothing is dropped in the mapper. */
    @Test
    public void everyStoredFieldSurvivesTheRoundTrip() {
        Novel source = added();
        library.add(source);

        NovelRecord record = database.novels().byId("novel-uji");
        assertNotNull(record);
        Novel restored = NovelMapper.toNovel(record);

        assertNotNull(restored);
        assertEquals(source.title, restored.title);
        assertEquals(source.titleJp, restored.titleJp);
        assertEquals(source.author, restored.author);
        assertEquals(source.illustrator, restored.illustrator);
        assertEquals(source.genres, restored.genres);
        assertEquals(source.status, restored.status);
        assertEquals(source.landingUrl, restored.landingUrl);
        assertEquals(source.coverUrl, restored.coverUrl);
        assertEquals(source.catalogMode, restored.catalogMode);
        assertEquals("sinopsis harus utuh sebagai paragraf", 2, restored.synopsis.length);
        assertEquals("Paragraf satu.", restored.synopsis[0]);
        assertEquals("Paragraf dua.", restored.synopsis[1]);
        assertTrue("rasio cover harus tersimpan", Math.abs(0.70f - restored.coverRatio) < 0.01f);
    }

    /** An empty novel is refused rather than stored as an unreadable row. */
    @Test
    public void anEmptyNovelIsRefused() {
        Library.Result result = library.add(new Novel(
                "", "", "", "", "", "", "", "", "", "", "", "", 0f,
                new String[0], 1, Novel.CATALOG_DISCOVERED, "", "", false));

        assertEquals(Library.Added.REFUSED, result.status);
        assertEquals(2, library.count());
    }
}
