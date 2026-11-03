package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.CatalogueMerge;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.FeedEntry;
import id.shuyasal.kuromaku.data.Library;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.NovelMapper;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.db.NovelDatabase;
import id.shuyasal.kuromaku.db.NovelRecord;
import id.shuyasal.kuromaku.net.CatalogLoader;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.net.FeedClient;
import id.shuyasal.kuromaku.parser.LandingCatalogParser;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Monogatari's catalogue now comes from the source's own list article, with the
 * label feed kept alongside it.
 *
 * <p>The label feed alone was the whole catalogue until the feed broke and every
 * chapter disappeared behind "Respons feed halaman ke-1 tidak dapat dibaca". The
 * article lists all seven volumes and their chapters, so it is the authority; the
 * feed is kept for the three jobs only it can do — repair a link the article
 * printed twice, add a chapter the article has not listed yet, and stand in when
 * the article itself cannot be read.
 *
 * <p>Every number asserted here was measured on the saved source page and the
 * saved feed, not estimated: 7 volumes, 115 chapter links in the article, one URL
 * printed twice, 117 posts in the feed, 116 chapters after the merge.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class MonogatariCatalogueTest {

    private static final String ARTICLE_URL =
            "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari-no-kuromaku-ni-tensei.html";
    private static final String FEED_MARKER = "/feeds/posts/summary/-/";

    private Context context;
    private NovelDatabase database;
    private Library library;
    private Prefs prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        NovelDatabase.reset();
        database = NovelDatabase.inMemory(context);
        library = Library.forDatabase(database);
        Library.useForTests(library);
        prefs = new Prefs(context, Novels.MONOGATARI);
        Fetcher.reset();
    }

    @After
    public void tearDown() {
        Fetcher.reset();
        Library.useForTests(null);
        database.close();
        NovelDatabase.reset();
    }

    // ---------------------------------------------------------------- helpers

    private static String fixture(String name) throws Exception {
        try (InputStream in = MonogatariCatalogueTest.class.getClassLoader()
                .getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture katalog/" + name + " hilang", in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    /** The saved feed, re-served page by page exactly as Blogger paged it. */
    private static String feedPage(String url) throws Exception {
        JSONArray all = new JSONObject(fixture("monogatari_feed.json")).getJSONArray("entries");
        int start = url.matches(".*start-index=(\\d+).*")
                ? Integer.parseInt(url.replaceAll(".*start-index=(\\d+).*", "$1")) : 1;
        int maxResults = url.matches(".*max-results=(\\d+).*")
                ? Integer.parseInt(url.replaceAll(".*max-results=(\\d+).*", "$1")) : 25;

        JSONObject feed = new JSONObject();
        JSONObject total = new JSONObject();
        total.put("$t", String.valueOf(all.length()));
        feed.put("openSearch$totalResults", total);
        JSONArray page = new JSONArray();
        for (int i = start - 1; i < Math.min(all.length(), start - 1 + maxResults); i++) {
            JSONObject source = all.getJSONObject(i);
            JSONObject item = new JSONObject();
            JSONObject title = new JSONObject();
            title.put("$t", source.getString("title"));
            item.put("title", title);
            JSONObject published = new JSONObject();
            published.put("$t", "2025-09-01T08:00:00.000+07:00");
            item.put("published", published);
            JSONArray links = new JSONArray();
            JSONObject alternate = new JSONObject();
            alternate.put("rel", "alternate");
            alternate.put("href", source.getString("url"));
            links.put(alternate);
            item.put("link", links);
            page.put(item);
        }
        feed.put("entry", page);
        JSONObject root = new JSONObject();
        root.put("feed", feed);
        return root.toString();
    }

    private void serveRealSource() throws Exception {
        final String article = fixture("monogatari_article.html");
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                if (url.contains(FEED_MARKER)) return feedPage(url);
                return article;
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });
    }

    private static Volume volume(List<Volume> volumes, int number) {
        for (Volume volume : volumes) {
            if (volume.number == number) return volume;
        }
        return null;
    }

    private static Chapter chapter(Volume volume, String label) {
        for (Chapter chapter : volume.chapters) {
            if (chapter.label.equals(label)) return chapter;
        }
        return null;
    }

    private static int total(List<Volume> volumes) {
        int sum = 0;
        for (Volume volume : volumes) sum += volume.chapterCount();
        return sum;
    }

    // ------------------------------------------------- 1. the article is real

    /**
     * The article really does list all seven volumes and their chapters. Measured on
     * the saved page: 7 headings, 116 anchors of which 115 distinct URLs.
     */
    @Test
    public void theListArticleHoldsAllSevenVolumes() throws Exception {
        List<Volume> volumes = LandingCatalogParser.parse(
                fixture("monogatari_article.html"), "monogatari", Novels.MONOGATARI);

        assertEquals("tujuh volume", 7, volumes.size());
        assertEquals("semua volume berisi chapter", 7, filled(volumes));
        // 116 entries, one URL printed twice, so 116 labels over 115 posts.
        assertEquals("116 entri chapter di artikel", 116, total(volumes));
        assertEquals("14 chapter di Volume 1", 14, volume(volumes, 1).chapterCount());
        assertEquals("18 chapter di Volume 2", 18, volume(volumes, 2).chapterCount());
        assertEquals("16 chapter di Volume 7", 16, volume(volumes, 7).chapterCount());

        // Volume 2 is the broken one: "Prolog" and "Chapter 1" both point at _49.
        Chapter prolog = chapter(volume(volumes, 2), "Prolog");
        Chapter chapterOne = chapter(volume(volumes, 2), "Chapter 1");
        assertNotNull("Prolog Volume 2 harus terbaca", prolog);
        assertNotNull("Chapter 1 Volume 2 harus terbaca", chapterOne);
        assertEquals("dua label menunjuk satu postingan sebelum diperbaiki",
                prolog.url, chapterOne.url);
    }

    private static int filled(List<Volume> volumes) {
        int count = 0;
        for (Volume volume : volumes) if (volume.chapterCount() > 0) count++;
        return count;
    }

    // ------------------------------------------------- 2. the feed repairs it

    /**
     * The merge hands the prologue back its own post and leaves chapter 1 where the
     * article put it, so the volume reads 18 chapters with no URL used twice.
     */
    @Test
    public void theFeedGivesThePrologueBackItsOwnPost() throws Exception {
        List<Volume> page = LandingCatalogParser.parse(
                fixture("monogatari_article.html"), "monogatari", Novels.MONOGATARI);
        CatalogueMerge.Result merged =
                CatalogueMerge.merge(Novels.MONOGATARI, page, savedFeed());

        assertEquals("satu tautan diperbaiki", 1, merged.repaired);
        assertEquals("tidak ada chapter baru yang perlu ditambah", 0, merged.added);
        assertEquals("116 chapter setelah digabung", 116, total(merged.volumes));

        Volume two = volume(merged.volumes, 2);
        assertEquals("18 entri di Volume 2", 18, two.chapterCount());
        assertEquals("Prolog memakai postingan prolog yang asli",
                "https://kaoritranslation.blogspot.com/2025/10/ln-monogatari-no-kuromaku-volume-2_12.html",
                chapter(two, "Prolog").url);
        assertEquals("Chapter 1 tetap di postingan chapter 1",
                "https://kaoritranslation.blogspot.com/2025/10/ln-monogatari-no-kuromaku-volume-2_49.html",
                chapter(two, "Chapter 1").url);

        Set<String> urls = new HashSet<>();
        for (Volume volume : merged.volumes) {
            for (Chapter chapter : volume.chapters) {
                assertTrue("URL dipakai dua kali: " + chapter.url, urls.add(chapter.url));
            }
        }
        assertEquals("116 URL unik", 116, urls.size());

        // Reading order survives the swap: prologue first, chapter 1 next.
        assertTrue("Prolog harus di depan Chapter 1",
                two.chapters.indexOf(chapter(two, "Prolog"))
                        < two.chapters.indexOf(chapter(two, "Chapter 1")));
    }

    private static List<FeedEntry> savedFeed() throws Exception {
        Fetcher.Source source = new Fetcher.Source() {
            @Override
            public String page(String pageUrl) throws Exception {
                return feedPage(pageUrl);
            }

            @Override
            public byte[] image(String imageUrl) {
                return new byte[0];
            }
        };
        // FeedClient's seam only declares IOException, so the checked Exception that
        // a fixture source may throw is normalised exactly like the app does it.
        FeedClient.Result result = FeedClient.fetchLabel("Monogatari no Kuromaku", url -> {
            try {
                return source.page(url);
            } catch (java.io.IOException error) {
                throw error;
            } catch (Exception error) {
                throw new java.io.IOException(error.toString(), error);
            }
        });
        assertFalse("feed contoh harus berisi postingan", result.entries.isEmpty());
        return result.entries;
    }

    // ------------------------------------------------- 3. the loader end to end

    /**
     * Loading the built-in Monogatari from the real source gives 116 chapters in 7
     * volumes, and says so — including the repaired link — instead of silently
     * showing a volume with a prologue it invented.
     */
    @Test
    public void loadingTheBuiltInMonogatariUsesArticleAndFeed() throws Exception {
        serveRealSource();
        Novel novel = library.byId(Novels.MONOGATARI);
        assertEquals("sumber katalog baru", Novel.CATALOG_ARTICLE_FEED, novel.catalogMode);

        CatalogLoader.Loaded loaded = CatalogLoader.load(novel);
        Catalog catalog = loaded.catalog;

        assertEquals("116 chapter", 116, catalog.size());
        assertEquals("7 volume", 7, catalog.volumes.size());
        assertEquals("semua volume berisi", 7, filled(catalog.volumes));
        assertNotNull("panel harus menjelaskan sumbernya", loaded.warning);
        assertTrue("sumber disebut sebagai artikel katalog: " + loaded.warning,
                loaded.warning.contains("artikel katalog sumber"));
        assertTrue("perbaikan tautan dilaporkan beserta angkanya: " + loaded.warning,
                loaded.warning.contains("1 tautan yang salah di artikel diperbaiki"));

        // Covers still come from the verified per-volume table, not from the page.
        for (Volume volume : catalog.volumes) {
            assertTrue("volume " + volume.number + " harus punya cover", volume.hasCover());
        }
        assertNotNull("metadata ikut terbaca dari artikel yang sama", loaded.discovery);
        assertEquals("Yuuki Ryou", loaded.discovery.author);
    }

    // ------------------------------------------------- 4. new chapters, no APK

    /**
     * A chapter published after this APK shipped must appear on "Muat Ulang" without
     * a new build: it is in the feed, absent from the article, and the merge adds it
     * to the volume it belongs to.
     */
    @Test
    public void aChapterThatOnlyTheFeedKnowsYetIsAddedOnReload() throws Exception {
        List<Volume> page = LandingCatalogParser.parse(
                fixture("monogatari_article.html"), "monogatari", Novels.MONOGATARI);
        List<FeedEntry> feed = new ArrayList<>(savedFeed());
        feed.add(new FeedEntry("[LN] Monogatari no Kuromaku volume 7 Chapter 9",
                "https://kaoritranslation.blogspot.com/2026/11/ln-monogatari-no-kuromaku-volume-7_25.html",
                "2026-11-02"));

        CatalogueMerge.Result merged = CatalogueMerge.merge(Novels.MONOGATARI, page, feed);

        assertEquals("satu chapter baru ditambahkan", 1, merged.added);
        Volume seven = volume(merged.volumes, 7);
        assertEquals("Volume 7 bertambah satu", 17, seven.chapterCount());
        Chapter added = chapter(seven, "Chapter 9");
        assertNotNull("chapter baru harus muncul", added);
        // Volume 7 on the source runs Prolog, Chapter 1-8, then the bonus/side
        // stories and back matter. A newly published chapter 9 belongs right after
        // chapter 8, not at the end of the volume.
        assertTrue("chapter 9 dibaca setelah chapter 8",
                added.order > chapter(seven, "Chapter 8").order);
        assertTrue("chapter 9 dibaca sebelum Bonus Story",
                added.order < chapter(seven, "Bonus Story").order);
    }

    /**
     * The same chapter twice in the feed must not double the list, and a chapter the
     * article already lists must not be added again.
     */
    @Test
    public void aChapterAlreadyOnThePageIsNotAddedTwice() throws Exception {
        List<Volume> page = LandingCatalogParser.parse(
                fixture("monogatari_article.html"), "monogatari", Novels.MONOGATARI);
        List<FeedEntry> feed = new ArrayList<>(savedFeed());
        // Volume 1 Chapter 1 as the feed spells it, already in the article.
        feed.add(new FeedEntry("[LN] Monogatari no Kuromaku volume 1 Chapter 1",
                "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari-no-kuromaku-volume-1_83.html",
                "2025-09-05"));

        CatalogueMerge.Result merged = CatalogueMerge.merge(Novels.MONOGATARI, page, feed);

        assertEquals("tidak ada yang ditambahkan", 0, merged.added);
        assertEquals("jumlah chapter tidak berubah", 116, total(merged.volumes));
    }

    // ------------------------------------------------- 5. failure keeps the list

    /**
     * When the article cannot be fetched, the feed still builds a usable catalogue
     * and the panel says which source was used. The old behaviour — an exception and
     * an empty list — is what the user reported.
     */
    @Test
    public void whenTheArticleFailsTheFeedStillBuildsTheCatalogue() throws Exception {
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                if (url.contains(FEED_MARKER)) return feedPage(url);
                throw new java.io.IOException("HTTP 429 untuk " + url);
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        Novel novel = library.byId(Novels.MONOGATARI);
        CatalogLoader.Loaded loaded = CatalogLoader.load(novel);

        assertTrue("feed tetap memberi chapter", loaded.catalog.size() > 100);
        assertEquals("tujuh volume dari feed", 7, filled(loaded.catalog.volumes));
        assertNotNull("sumber pengganti harus dijelaskan", loaded.warning);
        assertTrue("alasan artikel gagal disebut: " + loaded.warning,
                loaded.warning.contains("Artikel katalog tidak terbaca")
                        && loaded.warning.contains("429"));
        assertNull("metadata tidak dikarang saat artikel gagal", loaded.discovery);
    }

    /**
     * When both sources fail the loader throws, so the caller keeps the catalogue it
     * already had. It must never return an empty catalogue as if it were an answer.
     */
    @Test
    public void whenBothSourcesFailTheLoaderRefusesInsteadOfReturningNothing() throws Exception {
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                throw new java.io.IOException("jaringan mati");
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        Novel novel = library.byId(Novels.MONOGATARI);
        try {
            CatalogLoader.Loaded loaded = CatalogLoader.load(novel);
            throw new AssertionError("harus gagal, bukan mengembalikan katalog kosong; "
                    + "hasil: " + loaded.catalog.size() + " chapter");
        } catch (java.io.IOException expected) {
            String message = expected.getMessage();
            assertNotNull("alasan harus dijelaskan", message);
            assertTrue("kedua sumber disebut: " + message,
                    message.contains("Halaman katalog") && message.contains("Feed label"));
        }
    }

    // ------------------------------------------------- 6. no duplicate, no loss

    /**
     * A row written by the previous version says "feed". Opening the shelf must point
     * it at the new source while keeping the id, URL, position and metadata — that is
     * what keeps progress, bookmarks and offline folders attached.
     */
    @Test
    public void anOlderStoredRowIsPointedAtTheNewSourceWithoutLosingAnything() {
        // The previous version stored both built-ins, with Monogatari still on the
        // label feed. Seeding only runs on an empty table, so an upgrade has to be
        // simulated by writing the old rows the same way the old app did.
        Novel monogatariSeed = Novels.byId(Novels.MONOGATARI);
        NovelRecord stale = NovelMapper.toRecord(monogatariSeed, 0);
        stale.catalogMode = Novel.CATALOG_FEED;
        stale.url = ARTICLE_URL;
        stale.title = "Judul yang sudah ada";
        stale.author = "Yuuki Ryou";
        stale.addedAt = 1_700_000_000_000L;
        database.novels().insert(stale);
        database.novels().insert(NovelMapper.toRecord(Novels.byId(Novels.MAGICAL), 1));

        // Progress and a bookmark stored outside the table, keyed by the URL.
        String chapterUrl =
                "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari-no-kuromaku-volume-1_83.html";
        prefs.setScroll(chapterUrl, 812, 33);
        prefs.setProgress(chapterUrl, 64);

        Library reopened = Library.forDatabase(database);
        Library.useForTests(reopened);
        List<Novel> all = reopened.all();

        assertEquals("tetap dua novel, tidak ada duplikat", 2, all.size());
        Novel monogatari = reopened.byId(Novels.MONOGATARI);
        assertNotNull(monogatari);
        assertEquals("id tidak berubah", Novels.MONOGATARI, monogatari.id);
        assertEquals("URL tidak berubah", ARTICLE_URL, monogatari.landingUrl);
        assertEquals("judul simpanan tidak ditimpa", "Judul yang sudah ada", monogatari.title);
        assertEquals("posisi rak tidak berubah", 0, database.novels().byId(Novels.MONOGATARI).position);
        assertEquals("sumber katalog diperbarui",
                Novel.CATALOG_ARTICLE_FEED, monogatari.catalogMode);
        assertEquals("label feed cadangan tersimpan",
                "Monogatari no Kuromaku", monogatari.feedLabel);

        // Nothing outside the table moved.
        assertEquals("progres membaca tetap", 64, prefs.progress(chapterUrl));
        assertEquals("posisi scroll tetap", 812, prefs.scrollPosition(chapterUrl));
        assertEquals("offset scroll tetap", 33, prefs.scrollOffset(chapterUrl));
    }

    /** Opening the shelf repeatedly must not insert a second Monogatari. */
    @Test
    public void theUpgradeRunsOnceAndNeverDuplicatesTheNovel() {
        Library first = Library.forDatabase(database);
        Library.useForTests(first);
        first.all();
        first.reload();

        Library second = Library.forDatabase(database);
        Library.useForTests(second);
        second.all();
        second.reload();

        int monogatari = 0;
        for (Novel novel : second.all()) {
            if (Novels.MONOGATARI.equals(novel.id)) monogatari++;
        }
        assertEquals("satu baris Monogatari", 1, monogatari);
        assertEquals("dua novel di rak", 2, second.count());
    }
}
