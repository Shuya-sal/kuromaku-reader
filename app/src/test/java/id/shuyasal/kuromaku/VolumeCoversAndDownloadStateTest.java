package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Library;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.NovelMapper;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.db.NovelDatabase;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.parser.LandingCatalogParser;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RingProgress;

/**
 * Feature 1 and feature 3: per-volume covers found on a URL-added novel's own
 * page, stored so they survive a restart, and the download state drawn on each
 * volume row.
 *
 * <p>Everything here is checked against the real Yarikon article that produced
 * the report ("Vol 1 / Vol 2 / Vol 3" placeholders instead of artwork), on a
 * 320 dp wide screen, because that is where the buttons have to fit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class VolumeCoversAndDownloadStateTest {

    private static final String NOVEL = "yarikon";
    private static final String CH1 = "https://kaoritranslation.blogspot.com/2026/03/ch1.html";
    private static final String CH2 = "https://kaoritranslation.blogspot.com/2026/03/ch2.html";

    private Context context;
    private NovelDatabase database;
    private Library library;
    private File root;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DownloadStatusStore.reset();
        NovelDatabase.reset();
        database = NovelDatabase.inMemory(context);
        library = Library.forDatabase(database);
        Library.useForTests(library);

        root = new File(context.getCacheDir(), "covers-" + System.nanoTime());
        OfflineStore.useRoot(root);
    }

    @After
    public void tearDown() {
        OfflineStore.reset();
        DownloadStatusStore.reset();
        Library.useForTests(null);
        database.close();
        NovelDatabase.reset();
        deleteTree(root);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = VolumeCoversAndDownloadStateTest.class
                .getClassLoader().getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture tidak ditemukan: " + name, in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // ------------------------------------------------- feature 1: real covers

    /**
     * The Yarikon article gives every volume its own picture.
     *
     * <p>Before this change the parser produced volumes without covers, so the
     * detail page could only print "Vol 1", "Vol 2", "Vol 3". The three covers
     * must now be distinct: reusing one picture for all volumes is exactly the
     * failure the report showed.
     */
    @Test
    public void everyVolumeOfTheUrlAddedNovelCarriesItsOwnCover() throws Exception {
        List<Volume> volumes = LandingCatalogParser.parse(
                fixture("yariko_article_covers.html"), "yarikonde-ita-game-sekai-", NOVEL);

        assertEquals("tiga volume", 3, volumes.size());
        Map<String, Integer> byUrl = new HashMap<>();
        for (Volume volume : volumes) {
            assertTrue("Volume " + volume.number + " harus punya cover", volume.hasCover());
            assertFalse("URL cover tidak boleh kosong", volume.coverUrl.trim().isEmpty());
            assertTrue("rasio cover harus masuk akal: " + volume.coverRatio(),
                    volume.coverRatio() > 0.4f && volume.coverRatio() < 1.2f);
            byUrl.put(volume.coverUrl, volume.number);
        }
        assertEquals("tiga cover harus berbeda satu sama lain", 3, byUrl.size());
    }

    /** A cover must stay attached to its own volume when the list is re-grouped. */
    @Test
    public void reGroupingKeepsEachCoverOnItsOwnVolume() throws Exception {
        List<Volume> volumes = LandingCatalogParser.parse(
                fixture("yariko_article_covers.html"), "yarikonde-ita-game-sekai-", NOVEL);
        Map<Integer, String> before = new HashMap<>();
        for (Volume volume : volumes) before.put(volume.number, volume.coverUrl);

        Catalog catalog = new Catalog(NOVEL, volumes).withCovers(NOVEL);
        for (Volume volume : catalog.volumes) {
            assertEquals("cover Volume " + volume.number + " tidak boleh berpindah",
                    before.get(volume.number), volume.coverUrl);
        }
    }

    /** The hard-coded table must never overwrite a cover the page itself gave. */
    @Test
    public void builtInTableDoesNotReplaceAParsedCover() {
        Volume parsed = new Volume(1, new ArrayList<Chapter>())
                .withCover("https://blogger.googleusercontent.com/img/a/dari_halaman.jpg",
                        1240, 1781);
        Catalog catalog = new Catalog(Novels.MONOGATARI,
                new ArrayList<>(Arrays.asList(parsed))).withCovers(Novels.MONOGATARI);

        assertEquals("cover hasil parse harus menang",
                "https://blogger.googleusercontent.com/img/a/dari_halaman.jpg",
                catalog.volumes.get(0).coverUrl);
    }

    /** A volume the page said nothing about keeps no cover, and gets no guess. */
    @Test
    public void volumeWithoutArtworkStaysWithoutOne() {
        Volume blank = new Volume(9, new ArrayList<Chapter>());
        Catalog catalog = new Catalog("novel-tanpa-tabel",
                new ArrayList<>(Arrays.asList(blank))).withCovers("novel-tanpa-tabel");

        assertFalse("tidak boleh mengarang cover", catalog.volumes.get(0).hasCover());
    }

    /**
     * Covers are stored and come back after the application is closed.
     *
     * <p>The report asked for the URL covers to be saved so the page does not
     * have to parse the source every time it opens. This checks the round trip
     * through the database, including the pixel size.
     */
    @Test
    public void storedCoversSurviveReopeningTheDatabase() throws Exception {
        List<Volume> volumes = LandingCatalogParser.parse(
                fixture("yariko_article_covers.html"), "yarikonde-ita-game-sekai-", NOVEL);
        Catalog catalog = new Catalog(NOVEL, volumes);
        library.saveVolumeCovers(NOVEL, catalog);

        Map<Integer, String> expected = new HashMap<>();
        for (Volume volume : volumes) expected.put(volume.number, volume.coverUrl);

        File file = new File(context.getCacheDir(), "covers-db-" + System.nanoTime() + ".db");
        NovelDatabase first = androidx.room.Room.databaseBuilder(
                context, NovelDatabase.class, file.getAbsolutePath())
                .allowMainThreadQueries().build();
        Library before = Library.forDatabase(first);
        before.saveVolumeCovers(NOVEL, catalog);
        first.close();

        NovelDatabase second = androidx.room.Room.databaseBuilder(
                context, NovelDatabase.class, file.getAbsolutePath())
                .allowMainThreadQueries().build();
        try {
            Library after = Library.forDatabase(second);
            Map<Integer, id.shuyasal.kuromaku.data.VolumeCovers.Cover> stored =
                    after.storedVolumeCovers(NOVEL);
            assertEquals("tiga cover tersimpan", 3, stored.size());
            for (Map.Entry<Integer, String> item : expected.entrySet()) {
                assertNotNull("Volume " + item.getKey() + " harus punya cover tersimpan",
                        stored.get(item.getKey()));
                assertEquals(item.getValue(), stored.get(item.getKey()).url);
            }
        } finally {
            second.close();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** A stored cover fills a catalogue that arrived without one. */
    @Test
    public void storedCoverFillsACatalogueThatCameFromCache() throws Exception {
        List<Volume> volumes = LandingCatalogParser.parse(
                fixture("yariko_article_covers.html"), "yarikonde-ita-game-sekai-", NOVEL);
        library.saveVolumeCovers(NOVEL, new Catalog(NOVEL, volumes));

        List<Volume> bare = new ArrayList<>();
        for (Volume volume : volumes) {
            bare.add(new Volume(volume.number, volume.chapters));
        }
        Catalog filled = new Catalog(NOVEL, bare)
                .withStoredCovers(library.storedVolumeCovers(NOVEL));

        for (Volume volume : filled.volumes) {
            assertTrue("Volume " + volume.number + " harus terisi dari penyimpanan",
                    volume.hasCover());
        }
    }

    /** A volume that already has a cover is not overwritten by the stored one. */
    @Test
    public void storedCoverDoesNotOverwriteANewerOne() {
        Volume one = new Volume(1, new ArrayList<Chapter>())
                .withCover("https://blogger.googleusercontent.com/img/a/lama.jpg", 1240, 1781);
        library.saveVolumeCovers(NOVEL, new Catalog(NOVEL, new ArrayList<>(Arrays.asList(one))));

        Volume fresh = new Volume(1, new ArrayList<Chapter>())
                .withCover("https://blogger.googleusercontent.com/img/a/baru.jpg", 1240, 1781);
        Catalog catalog = new Catalog(NOVEL, new ArrayList<>(Arrays.asList(fresh)))
                .withStoredCovers(library.storedVolumeCovers(NOVEL));

        assertEquals("https://blogger.googleusercontent.com/img/a/baru.jpg",
                catalog.volumes.get(0).coverUrl);
    }

    // ------------------------------------- feature 3: download state per volume

    private Catalog twoChapters() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("Volume 1 Chapter 1", "Chapter 1", CH1, "2026-03-01", 1, 1),
                new Chapter("Volume 1 Chapter 2", "Chapter 2", CH2, "2026-03-02", 2, 1)));
        Volume volume = new Volume(1, chapters)
                .withCover("https://blogger.googleusercontent.com/img/a/v1.jpg", 1240, 1781);
        return new Catalog(NOVEL, new ArrayList<>(Arrays.asList(volume)));
    }

    private LinearLayout build(LibraryPage.DownloadState state) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Prefs prefs = new Prefs(activity).scoped(NOVEL);
        // Ensure the volume list is open. Toggling unconditionally would close it
        // on the second build inside one test, and then no chapter row exists.
        if (!prefs.isExpanded("1")) prefs.toggleExpanded("1");
        return new LibraryPage(activity, new Prefs(activity).scoped(NOVEL),
                Palette.of(Prefs.THEME_DARK), novel(), twoChapters(), false,
                new LibraryPage.Listener() {
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
                })
                .setDownload(new NoopDownloads(), state)
                .build();
    }

    private Novel novel() {
        return new Novel(NOVEL, "Yarikon", "", "Yarikon", "", "", "", "", "",
                "https://kaoritranslation.blogspot.com/2026/03/yarikon.html",
                "https://kaoritranslation.blogspot.com/2026/03/yarikon.html",
                "", 0.70f, new String[0], 0, Novel.CATALOG_DISCOVERED, "", "", false);
    }

    /**
     * Before a download the volume offers "Download Semua" and nothing else.
     */
    @Test
    public void volumeBeforeDownloadOffersDownloadAll() {
        LinearLayout content = build(new StateWith(0, 2, -1, false));

        View button = content.findViewWithTag("volume_download:1");
        assertNotNull(button);
        assertEquals("Download Semua", ((Button) button).getText().toString());
        assertNull("belum ada centang", content.findViewWithTag("volume_tick:1"));
        assertNull("belum ada cincin", content.findViewWithTag("volume_ring:1"));
        TextView summary = (TextView) content.findViewWithTag("volume_summary:1");
        assertEquals("0 dari 2 tersimpan", summary.getText().toString());
    }

    /**
     * While downloading, the row shows a ring, a measured percentage and how many
     * chapters are done, plus a way to cancel.
     */
    @Test
    public void volumeWhileDownloadingShowsRingPercentAndCount() {
        LinearLayout content = build(new StateWith(1, 2, 50, true));

        View ring = content.findViewWithTag("volume_ring:1");
        assertNotNull("cincin progres harus ada saat mengunduh", ring);
        assertTrue("cincin harus punya ukuran", ring.getLayoutParams().width > 0);

        TextView progress = (TextView) content.findViewWithTag("volume_progress:1");
        assertNotNull(progress);
        assertEquals("persentase nyata harus tampil bersama jumlah chapter",
                "50% · 1 dari 2 chapter", progress.getText().toString());

        View cancel = content.findViewWithTag("volume_cancel:1");
        assertNotNull("tombol Batalkan harus ada", cancel);
        assertTrue("area sentuh 48 dp",
                cancel.getMinimumHeight() >= dp(48));
    }

    /** A finished volume shows a tick and "Tersimpan semua", with no ring left. */
    @Test
    public void finishedVolumeShowsTickAndTersimpanSemua() {
        LinearLayout content = build(new StateWith(2, 2, 100, false));

        assertNotNull("centang harus ada", content.findViewWithTag("volume_tick:1"));
        assertNull("cincin harus hilang", content.findViewWithTag("volume_ring:1"));
        Button button = (Button) content.findViewWithTag("volume_download:1");
        assertEquals("Tersimpan semua", button.getText().toString());
        assertFalse("tombol tidak boleh menawarkan unduh lagi", button.isEnabled());
    }

    /** Partly stored: the count is kept and the button becomes "Lanjutkan Download". */
    @Test
    public void partlyStoredVolumeOffersToContinue() {
        LinearLayout content = build(new StateWith(1, 2, 50, false));

        Button button = (Button) content.findViewWithTag("volume_download:1");
        assertEquals("Lanjutkan Download", button.getText().toString());
        TextView summary = (TextView) content.findViewWithTag("volume_summary:1");
        assertEquals("1 dari 2 tersimpan", summary.getText().toString());
    }

    /** "Tersimpan semua" must never appear while a chapter is still missing. */
    @Test
    public void tersimpanSemuaIsNeverShownForAnIncompleteVolume() {
        LinearLayout content = build(new StateWith(1, 2, 50, false));
        Button button = (Button) content.findViewWithTag("volume_download:1");
        assertFalse("tidak boleh mengklaim lengkap: " + button.getText(),
                button.getText().toString().contains("Tersimpan semua"));
    }

    /**
     * A downloading chapter shows its own ring and the measured percentage as a
     * separate view, so the number and the status word can never touch.
     */
    @Test
    public void downloadingChapterShowsSeparatePercentAndStatus() {
        Map<String, Integer> states = new HashMap<>();
        states.put(CH1, DownloadStatusStore.DOWNLOADING);
        LinearLayout content = build(new MapState(states, 67));

        View ring = content.findViewWithTag("download_ring:" + CH1);
        assertNotNull("cincin chapter harus ada", ring);
        assertTrue(((RingProgress) ring).getWidth() >= 0);

        TextView percent = (TextView) content.findViewWithTag("download_percent:" + CH1);
        assertNotNull("persentase chapter harus ada", percent);
        assertEquals("67%", percent.getText().toString());

        TextView status = (TextView) content.findViewWithTag("download_status:" + CH1);
        assertNotNull(status);
        assertEquals("Mengunduh…", status.getText().toString());
        assertTrue("persentase dan status harus punya jarak: "
                        + percent.getPaddingRight() + " / " + percent.getPaddingLeft(),
                percent.getPaddingRight() > 0 || percent.getPaddingLeft() > 0);
    }

    /** When the size is unknown there is a ring but no number at all. */
    @Test
    public void unknownSizeShowsSpinnerWithoutAPercentage() {
        Map<String, Integer> states = new HashMap<>();
        states.put(CH1, DownloadStatusStore.DOWNLOADING);
        LinearLayout content = build(new MapState(states, -1));

        assertNotNull(content.findViewWithTag("download_ring:" + CH1));
        assertNull("tidak boleh ada angka yang tidak diukur",
                content.findViewWithTag("download_percent:" + CH1));
    }

    /** The awaiting-queue label and the downloading label must differ. */
    @Test
    public void queuedAndDownloadingUseDifferentLabels() {
        Map<String, Integer> queued = new HashMap<>();
        queued.put(CH1, DownloadStatusStore.QUEUED);
        LinearLayout first = build(new MapState(queued, -1));
        assertEquals("Menunggu",
                ((Button) first.findViewWithTag("download_busy:" + CH1)).getText().toString());

        Map<String, Integer> running = new HashMap<>();
        running.put(CH1, DownloadStatusStore.DOWNLOADING);
        LinearLayout second = build(new MapState(running, 40));
        assertEquals("Mengunduh",
                ((Button) second.findViewWithTag("download_busy:" + CH1)).getText().toString());
    }

    /** A stored chapter is marked with a tick and offers deletion, not download. */
    @Test
    public void storedChapterShowsTickAndDelete() {
        Map<String, Integer> states = new HashMap<>();
        states.put(CH1, DownloadStatusStore.SAVED);
        LinearLayout content = build(new MapState(states, -1));

        assertNotNull("centang chapter harus ada",
                content.findViewWithTag("download_tick:" + CH1));
        assertNotNull(content.findViewWithTag("download_delete:" + CH1));
        assertNull(content.findViewWithTag("download_chapter:" + CH1));
    }

    /** A partial chapter is offered "Lanjutkan" and keeps its delete action. */
    @Test
    public void partialChapterOffersContinue() {
        Map<String, Integer> states = new HashMap<>();
        states.put(CH1, DownloadStatusStore.PARTIAL);
        LinearLayout content = build(new MapState(states, -1));

        View resume = content.findViewWithTag("download_resume:" + CH1);
        assertNotNull("tombol Lanjutkan harus ada untuk chapter parsial", resume);
        assertEquals("Lanjutkan", ((Button) resume).getText().toString());
        assertNull("tidak boleh menawarkan hapus sebelum lengkap",
                content.findViewWithTag("download_delete:" + CH1));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------ test doubles

    private static final class NoopDownloads implements LibraryPage.DownloadListener {
        @Override
        public void onDownloadChapter(Chapter chapter) {
        }

        @Override
        public void onDeleteChapter(Chapter chapter) {
        }

        @Override
        public void onDownloadVolume(Volume volume) {
        }

        @Override
        public void onCancelVolume(Volume volume) {
        }
    }

    private static class StateWith implements LibraryPage.DownloadState {
        private final int finished;
        private final int total;
        private final int percent;
        private final boolean running;

        StateWith(int finished, int total, int percent, boolean running) {
            this.finished = finished;
            this.total = total;
            this.percent = percent;
            this.running = running;
        }

        @Override
        public int state(Chapter chapter) {
            return DownloadStatusStore.NOT_DOWNLOADED;
        }

        @Override
        public String statusLabel(Chapter chapter) {
            return "Belum diunduh";
        }

        @Override
        public int[] volumeProgress(Volume volume) {
            return new int[]{finished, total};
        }

        @Override
        public boolean isVolumeRunning(Volume volume) {
            return running;
        }
    }

    private static final class MapState implements LibraryPage.DownloadState {
        private final Map<String, Integer> states;
        private final int percent;

        MapState(Map<String, Integer> states, int percent) {
            this.states = states;
            this.percent = percent;
        }

        @Override
        public int state(Chapter chapter) {
            Integer value = states.get(chapter.url);
            return value == null ? DownloadStatusStore.NOT_DOWNLOADED : value;
        }

        @Override
        public String statusLabel(Chapter chapter) {
            switch (state(chapter)) {
                case DownloadStatusStore.QUEUED:
                    return "Menunggu";
                case DownloadStatusStore.DOWNLOADING:
                    return "Mengunduh…";
                case DownloadStatusStore.VERIFYING:
                    return "Memverifikasi…";
                case DownloadStatusStore.SAVED:
                    return "Tersimpan";
                case DownloadStatusStore.PARTIAL:
                    return "Tersimpan sebagian";
                case DownloadStatusStore.FAILED:
                    return "Gagal";
                default:
                    return "Belum diunduh";
            }
        }

        @Override
        public int[] volumeProgress(Volume volume) {
            return new int[]{0, 2};
        }

        @Override
        public boolean isVolumeRunning(Volume volume) {
            return false;
        }

        @Override
        public int bytePercent(Chapter chapter) {
            return percent;
        }
    }
}