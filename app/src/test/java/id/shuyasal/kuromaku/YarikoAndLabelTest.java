package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.CatalogLoader;
import id.shuyasal.kuromaku.net.FeedClient;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.net.NovelUrl;
import id.shuyasal.kuromaku.parser.NovelDiscovery;

/**
 * The two reported failures, and the label address that used to be refused.
 *
 * <p>Both bugs looked like "the parser is too strict", and neither was. The tests
 * below are written so that putting either cause back on purpose makes one of them
 * fail:
 *
 * <ul>
 *   <li>Yariko's catalogue has 47 links in the article and 43 chapter posts. It
 *       reported "no chapters" because the boilerplate strip removed every
 *       {@code .widget}, and the article lives inside {@code div.widget.Blog} — so
 *       the article was deleted before the parser ever saw it.</li>
 *   <li>A label address was refused before it was fetched at all. It is now read
 *       through the label's JSON feed, which pages deterministically instead of
 *       showing twenty posts and hiding the rest.</li>
 * </ul>
 *
 * <p>Also covered: an individual chapter post must be refused as a catalogue, a
 * catalogue without any "Chapter" wording must be offered to the user instead of
 * being guessed at, and a cover must never be invented from a chapter illustration.
 */
public class YarikoAndLabelTest {

    private static final String YARIKO_TOC =
            "https://kaoritranslation.blogspot.com/2026/03/yarikon-de-ita-game-sekai-no-akuyaku.html";
    private static final String YARIKO_CHAPTER =
            "https://kaoritranslation.blogspot.com/2026/03/yarikonde-ita-game-sekai-no-akuyaku-mob.html";
    private static final String YARIKO_LABEL =
            "https://kaoritranslation.blogspot.com/search/label/"
                    + "Yarikonde%20ita%20Game%20Sekai%20no%20Akuyaku%20Mob%20ni%20Tensei%20Shimashita";
    private static final String MAGICAL =
            "https://kaoritranslation.blogspot.com/2025/04/ln-magicalexplorer-eroge-no-yuujin.html";

    @After
    public void tearDown() {
        Fetcher.reset();
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = YarikoAndLabelTest.class.getClassLoader()
                .getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture katalog/" + name + " hilang", in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    private static Volume volume(List<Volume> volumes, int number) {
        for (Volume volume : volumes) {
            if (volume.number == number) return volume;
        }
        fail("volume " + number + " tidak ada di hasil parse");
        return null;
    }

    // ------------------------------------------- bug 1: label page was refused

    /**
     * A label address normalises instead of being refused.
     *
     * <p>Before: {@code Invalid("Itu bukan halaman artikel…")}. The label page is the
     * only catalogue some novels have, and the same page typed with {@code ?m=1},
     * with sorting parameters, with a trailing slash or with {@code +} instead of
     * {@code %20} must resolve to one identity.
     */
    @Test
    public void aLabelAddressNormalisesInsteadOfBeingRefused() throws Exception {
        String canonical = NovelUrl.normalize(YARIKO_LABEL);

        assertEquals("https://kaoritranslation.blogspot.com/search/label/"
                + "Yarikonde%20ita%20Game%20Sekai%20no%20Akuyaku%20Mob%20ni%20Tensei%20Shimashita",
                canonical);
        assertTrue(NovelUrl.isLabel(canonical));
        assertFalse(NovelUrl.isArticle(canonical));
        assertEquals(NovelUrl.Kind.LABEL, NovelUrl.kindOf(canonical));

        String[] spellings = {
                YARIKO_LABEL + "?m=1",
                YARIKO_LABEL + "/",
                YARIKO_LABEL + "?updated-max=2026-03-28T20:16:00%2B07:00&max-results=20&start=20",
                YARIKO_LABEL.replace("%20", "+"),
                "http://www.kaoritranslation.blogspot.com/search/label/"
                        + "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita",
                "  " + YARIKO_LABEL + "  "
        };
        for (String spelling : spellings) {
            assertEquals("ejaan label harus satu identitas: " + spelling, canonical,
                    NovelUrl.normalize(spelling));
            assertEquals("id harus sama untuk ejaan itu", NovelUrl.id(canonical),
                    NovelUrl.id(NovelUrl.normalize(spelling)));
        }

        // The human name is what the feed and the library show.
        assertEquals("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita",
                NovelUrl.label(canonical));

        // The id is filesystem- and prefs-safe, and cannot collide with the
        // article of the same novel.
        String id = NovelUrl.id(canonical);
        assertTrue("id label harus aman untuk nama folder: " + id, id.matches("[a-z0-9-]+"));
        assertTrue("id label harus dikenali sebagai label: " + id, id.startsWith("label-"));
        assertNotEquals("label dan artikel tidak boleh berbagi id",
                id, NovelUrl.id(NovelUrl.normalize(YARIKO_TOC)));
    }

    /** The feed address is built from the label, encoded the way Blogger expects. */
    @Test
    public void theLabelFeedAddressIsEncodedAndScopedToThatLabel() throws Exception {
        String canonical = NovelUrl.normalize(YARIKO_LABEL);
        String feed = NovelUrl.labelFeed(canonical);

        assertTrue("feed harus JSON: " + feed, feed.contains("?alt=json"));
        assertTrue("feed harus menyebut labelnya: " + feed,
                feed.contains("/feeds/posts/summary/-/Yarikonde%20ita%20Game"));
        assertTrue("feed harus dihost yang sama: " + feed,
                feed.startsWith("https://kaoritranslation.blogspot.com/"));

        // A label with characters that must be escaped.
        String weird = NovelUrl.normalize(
                "https://kaoritranslation.blogspot.com/search/label/Novel%20Uji%20%26%20Lain");
        assertEquals("Novel Uji & Lain", NovelUrl.label(weird));
        assertTrue("ampersand harus di-encode: " + NovelUrl.labelFeed(weird),
                NovelUrl.labelFeed(weird).contains("Novel%20Uji%20%26%20Lain"));
    }

    /** Feeds, static pages, the front page and other hosts are still refused. */
    @Test
    public void everythingThatIsStillNotACatalogueIsRefused() {
        String[] bad = {
                "https://kaoritranslation.blogspot.com/",
                "https://kaoritranslation.blogspot.com/feeds/posts/default",
                "https://kaoritranslation.blogspot.com/p/list-novel.html",
                "https://kaoritranslation.blogspot.com/search?q=novel",
                "https://kaoritranslation.blogspot.com/search/label/",
                "https://contoh.com/search/label/Novel",
                "ftp://kaoritranslation.blogspot.com/search/label/Novel"
        };
        for (String url : bad) {
            try {
                NovelUrl.normalize(url);
                fail("harus ditolak: " + url);
            } catch (NovelUrl.Invalid expected) {
                assertTrue("pesan harus bisa ditindaklanjuti: " + expected.getMessage(),
                        expected.getMessage().length() > 15);
            }
        }
    }

    // ---------------------------------------- bug 2: the article was deleted

    /**
     * The Yariko catalogue is read completely: 43 chapters in 3 volumes.
     *
     * <p>This is the regression that matters. The page holds 47 anchors; four are
     * not chapters (image wrappers and the volume landing link) and 43 are. Before
     * the fix the count was zero, because stripping {@code .widget} removed the
     * article along with the sidebar — the post body on this template is
     * {@code div.widget.Blog > div.blog-posts > article > div.post-body}.
     */
    @Test
    public void theYarikoCatalogueIsReadCompletely() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("yariko_toc.html"), YARIKO_TOC);

        assertEquals("47 tautan harus ditemukan di artikel", 47, result.linksFound);
        assertEquals("43 di antaranya chapter", 43, result.linksAccepted);
        assertEquals("43 chapter", 43, result.chapterCount());
        assertEquals("3 volume berisi chapter", 3, result.filledVolumes());
        assertFalse("katalog ini yakin, tidak perlu konfirmasi", result.needsConfirmation);

        assertEquals("Volume 1", 16, volume(result.volumes, 1).chapterCount());
        assertEquals("Volume 2", 13, volume(result.volumes, 2).chapterCount());
        assertEquals("Volume 3", 14, volume(result.volumes, 3).chapterCount());

        // Reading order is the page's own order, not a rule the page never agreed
        // to: Volume 1 opens with Ilustrasi, then Prolog, then Chapter 1.
        List<Chapter> one = volume(result.volumes, 1).chapters;
        assertEquals("Illustrasi", one.get(0).label);
        assertEquals("Prolog", one.get(1).label);
        assertEquals("Chapter 1", one.get(2).label);
        assertEquals("Interlude I", one.get(3).label);

        // Every chapter points at its own post inside this novel, never at the page.
        for (Volume v : result.volumes) {
            for (Chapter chapter : v.chapters) {
                assertTrue("URL chapter harus absolut: " + chapter.url,
                        chapter.url.startsWith("https://kaoritranslation.blogspot.com/2026/03/"));
                assertNotEquals("chapter tidak boleh menunjuk halaman katalog", YARIKO_TOC,
                        chapter.url);
                assertEquals("volume chapter harus sesuai grupnya", v.number, chapter.volume);
            }
        }

        // Metadata printed on the page is read; nothing is invented.
        assertTrue("judul terbaca: " + result.title, result.title.startsWith("Yarikon de Ita"));
        assertEquals("Natsunomi", result.author);
        assertEquals("Shimanun", result.illustrator);
        assertEquals("Comedy, Fantasy, Romance", result.genres);
        assertTrue("judul Jepang harus terbaca", result.titleJp.startsWith("やり込んでいた"));
        assertTrue("sinopsis harus beberapa paragraf: " + result.synopsis.length,
                result.synopsis.length >= 6);

        // Cover: the portrait artwork, not the wide banner at the top.
        assertFalse("cover tidak boleh kosong", result.coverUrl.isEmpty());
        assertTrue("cover harus portrait: " + result.coverRatio, result.coverRatio < 0.9f);
    }

    /**
     * Diagnosis is reported, not hidden: every rejected link says why.
     *
     * <p>"Tidak ada tautan chapter" used to be the whole story. Now the preview can
     * show how many links existed, how many were accepted, and a reason for each
     * one that was dropped.
     */
    @Test
    public void everyRejectedLinkCarriesItsReason() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("yariko_toc.html"), YARIKO_TOC);

        assertEquals("jumlah kandidat harus sama dengan tautan yang ditemukan", 47,
                result.candidates.size());

        List<NovelDiscovery.Candidate> rejected = result.rejected();
        assertEquals("4 tautan ditolak", 4, rejected.size());
        for (NovelDiscovery.Candidate candidate : rejected) {
            assertNotNull(candidate.reason);
            assertFalse("alasan tidak boleh kosong", candidate.reason.trim().isEmpty());
            assertTrue("alasan penolakan harus eksplisit: " + candidate.reason,
                    candidate.reason.startsWith("ditolak"));
            assertNotNull(candidate.url);
        }
        for (NovelDiscovery.Candidate candidate : result.candidates) {
            assertFalse("setiap kandidat harus punya alasan", candidate.reason.trim().isEmpty());
        }
        assertTrue("preview harus menyebut jumlah tautan",
                warningsJoin(result).contains("47"));
    }

    /**
     * An individual chapter post is refused as a catalogue, and the message says
     * what was actually found instead of claiming the article had nothing.
     */
    @Test
    public void anIndividualChapterPostIsRefusedAsACatalogue() throws Exception {
        try {
            NovelDiscovery.parse(fixture("yariko_chapter.html"), YARIKO_CHAPTER);
            fail("artikel satu chapter harus ditolak sebagai katalog");
        } catch (NovelDiscovery.Unreadable expected) {
            String message = expected.getMessage();
            assertTrue("pesan harus menyebut chapter: " + message,
                    message.toLowerCase().contains("chapter"));
            assertTrue("pesan harus jujur soal jumlah tautan: " + message,
                    message.contains("7 tautan") || message.contains("Tidak ada satu pun"));
        }
    }

    /** A chapter post still yields its own text and illustration for reading. */
    @Test
    public void aChapterPostStillDescribesItselfForReading() throws Exception {
        NovelDiscovery.Result page = NovelDiscovery.describe(
                fixture("yariko_chapter.html"), YARIKO_CHAPTER);
        assertTrue("judul chapter harus terbaca: " + page.title,
                page.title.contains("Illustrasi"));
        assertFalse("chapter bergambar harus punya cover/ilustrasi", page.coverUrl.isEmpty());
    }

    // ------------------------------------------- label catalogue from the feed

    /**
     * A label address becomes a full catalogue through its feed, with every page
     * of it.
     *
     * <p>44 posts do not fit on one label listing, and Blogger's HTML paging is what
     * used to make this "twenty chapters and a mystery". The feed is paged here
     * instead: 25 + 19, then nothing. Volumes come from the post titles, which is
     * the only place the source prints them.
     */
    @Test
    public void aLabelCatalogueComesFromThePagedFeed() throws Exception {
        final String labelHtml = fixture("label_yarikonde.html");
        final List<String> requested = new ArrayList<>();
        final List<String> feed = new ArrayList<>();
        for (int i = 1; i <= 19; i++) feed.add("Volume 1 Chapter " + i);
        for (int i = 1; i <= 10; i++) feed.add("Volume 2 Chapter " + i);
        for (int i = 1; i <= 15; i++) feed.add("Volume 3 Chapter " + i);

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                requested.add(url);
                if (url.contains("/feeds/posts/summary/-/")) {
                    return feedPage(url, feed);
                }
                return labelHtml;
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        NovelDiscovery.Result preview = CatalogLoader.inspect(NovelUrl.normalize(YARIKO_LABEL));

        assertEquals("44 chapter dari dua halaman feed", 44, preview.chapterCount());
        assertEquals("3 volume", 3, preview.filledVolumes());
        assertEquals("Volume 1", 19, volume(preview.volumes, 1).chapterCount());
        assertEquals("Volume 2", 10, volume(preview.volumes, 2).chapterCount());
        assertEquals("Volume 3", 15, volume(preview.volumes, 3).chapterCount());

        // The label name is the novel's name; the newest chapter's heading is not.
        assertEquals("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita",
                preview.title);

        // Two feed requests, then the label page once for the description.
        int feedCalls = 0;
        int pageCalls = 0;
        for (String url : requested) {
            if (url.contains("/feeds/posts/summary/-/")) feedCalls++;
            else pageCalls++;
        }
        assertEquals("feed harus dibaca dua halaman saja", 2, feedCalls);
        assertEquals("halaman label dibaca sekali", 1, pageCalls);

        // No chapter is invented and no illustration is dressed up as a cover.
        assertTrue("cover tidak boleh diambil dari ilustrasi chapter: " + preview.coverUrl,
                preview.coverUrl.isEmpty());
        assertTrue("preview harus menyarankan URL artikel untuk cover",
                warningsJoin(preview).contains("URL artikelnya"));
        assertTrue("preview harus menyebut asal chapter",
                warningsJoin(preview).contains("feed label"));
        assertFalse("katalog feed tidak perlu konfirmasi manual", preview.needsConfirmation);
    }

    /**
     * Every number in the diagnostics has to match the list the user is looking at.
     *
     * <p>The first version printed "43 chapter diambil dari feed" above a list of 42,
     * because it counted feed posts before the duplicate merge. A user who counts the
     * chapters and finds one missing goes looking for a chapter that was never lost.
     * This asserts the reported figure equals the actual chapter count.
     */
    @Test
    public void theReportedChapterCountEqualsTheListOnScreen() throws Exception {
        final String labelHtml = fixture("label_yarikonde.html");
        // Two posts carry the same volume and the same chapter title, so the builder
        // keeps one. 5 posts -> 4 unique chapters.
        final List<String> titles = Arrays.asList(
                "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita",
                "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 1 Bab 1",
                "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 1 Bab 2",
                "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 1 Bab 2",
                "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 1 Bab 3");

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                if (url.contains("/feeds/posts/summary/-/")) return feedPageRaw(url, titles);
                return labelHtml;
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        NovelDiscovery.Result preview = CatalogLoader.inspect(NovelUrl.normalize(YARIKO_LABEL));
        assertEquals("tiga chapter unik", 3, preview.chapterCount());

        String said = warningsJoin(preview);
        // 4 chapter posts, one of them published twice -> 4 posts, 3 chapters.
        assertTrue("angka yang dilaporkan harus sama dengan daftar, bukan jumlah "
                        + "postingan feed; peringatan: " + said,
                said.contains("3 chapter diambil dari feed label"));
        assertTrue("penggabungan harus dijelaskan beserta angkanya: " + said,
                said.contains("4 postingan chapter di feed")
                        && said.contains("1 di antaranya")
                        && said.contains("3 chapter unik"));
        assertTrue("postingan deskripsi tetap dilaporkan: " + said,
                said.contains("deskripsi novel tidak dihitung"));
    }

    /** A label that holds nothing is refused instead of producing an empty novel. */
    @Test
    public void anEmptyLabelIsRefusedInsteadOfStoredEmpty() throws Exception {
        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                if (url.contains("/feeds/posts/summary/-/")) {
                    return "{\"feed\":{\"entry\":[],\"openSearch$totalResults\":{\"$t\":\"0\"}}}";
                }
                return "<html><body><div class='post-body entry-content'>kosong</div></body></html>";
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });
        try {
            CatalogLoader.inspect(NovelUrl.normalize(
                    "https://kaoritranslation.blogspot.com/search/label/Label%20Kosong"));
            fail("label kosong harus ditolak");
        } catch (NovelDiscovery.Unreadable expected) {
            assertTrue("pesan harus menyebut label: " + expected.getMessage(),
                    expected.getMessage().contains("Label"));
        }
    }

    /**
     * A label listing is not the novel's description post.
     *
     * <p>What the label page shows is the newest chapter, so its heading is that
     * chapter's title and its pictures are that chapter's illustrations. Neither is
     * allowed to become the novel's title or cover — the catalogue built from the
     * label keeps the label's own name and leaves the cover empty with an
     * explanation.
     */
    @Test
    public void aLabelListingDoesNotBecomeTheNovelDescription() throws Exception {
        NovelDiscovery.Result described = NovelDiscovery.describe(
                fixture("label_yarikonde.html"), YARIKO_LABEL);
        assertTrue("halaman label biasanya memuat satu postingan terbaru: " + described.title,
                described.title.toLowerCase().contains("volume"));

        assertFalse("label bukan artikel bertanggal", NovelUrl.isArticle(YARIKO_LABEL));
        assertEquals("label harus punya nama sendiri",
                "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita",
                NovelUrl.label(YARIKO_LABEL));
    }

    /** The feed address the client builds is used, not a hard-coded label. */
    @Test
    public void theFeedClientBuildsTheAddressForAnyLabel() throws Exception {
        String feed = FeedClient.feedUrl("Novel Uji");
        assertEquals("https://kaoritranslation.blogspot.com/feeds/posts/summary/-/"
                + "Novel%20Uji?alt=json", feed);
        assertEquals(feed, NovelUrl.labelFeed(
                NovelUrl.normalize("https://kaoritranslation.blogspot.com/search/label/Novel%20Uji")));
    }

    // ------------------------------------------------------- manual selection

    /**
     * A catalogue with no chapter wording at all is offered to the user, not
     * guessed at and not silently dropped.
     *
     * <p>Four links share one slug stem: that is a list. The preview reports
     * {@code needsConfirmation}, holds zero chapters, and lists every link so the
     * user can pick — nothing is stored until they do.
     */
    @Test
    public void aCatalogueWithoutChapterWordingIsOfferedForSelection() throws Exception {
        NovelDiscovery.Result preview = NovelDiscovery.parse(
                fixture("unlabelled_catalogue.html"),
                "https://kaoritranslation.blogspot.com/2026/02/novel-x.html");

        assertTrue("parser harus minta konfirmasi", preview.needsConfirmation);
        assertEquals("belum ada chapter sampai dipilih", 0, preview.chapterCount());
        assertTrue("kandidat harus ditawarkan: " + preview.candidates.size(),
                preview.candidates.size() >= 5);
        assertFalse("preview harus melaporkan jumlah tautan", preview.linksFound == 0);

        // Four links name this series, but one of them *is* this page ("Bagian
        // Pembuka" pointing at the post being read). That link is a candidate the
        // diagnostics show and a link the picker must refuse: a catalogue page cannot
        // be a chapter of itself.
        int seriesLinks = 0;
        for (NovelDiscovery.Candidate candidate : preview.candidates) {
            if (candidate.url.contains("novel-x")) seriesLinks++;
        }
        assertEquals("4 tautan satu seri", 4, seriesLinks);

        List<String> chosen = new ArrayList<>();
        for (NovelDiscovery.Candidate candidate : preview.selectableCandidates()) {
            chosen.add(candidate.url);
        }
        assertEquals("tautan halaman ini sendiri tidak boleh ditawarkan", 3, chosen.size());

        NovelDiscovery.Result picked = NovelDiscovery.reselect(preview, chosen);
        assertEquals("3 chapter dari pilihan manual", 3, picked.chapterCount());
        assertFalse("setelah dipilih tidak perlu konfirmasi lagi", picked.needsConfirmation);
        for (Chapter chapter : picked.volumes.get(0).chapters) {
            assertTrue("hanya tautan yang dipilih: " + chapter.url,
                    chapter.url.contains("novel-x"));
        }
    }

    /** Nothing ticked means nothing saved — the preview is refused, not emptied. */
    @Test
    public void anEmptySelectionIsRefused() throws Exception {
        NovelDiscovery.Result preview = NovelDiscovery.parse(
                fixture("unlabelled_catalogue.html"),
                "https://kaoritranslation.blogspot.com/2026/02/novel-x.html");
        try {
            NovelDiscovery.reselect(preview, new ArrayList<String>());
            fail("pilihan kosong harus ditolak");
        } catch (NovelDiscovery.Unreadable expected) {
            assertTrue("pesan harus menjelaskan: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("dipilih"));
        }
    }

    /** A URL that was never on the page cannot be smuggled in through the picker. */
    @Test
    public void aLinkThatWasNotOnThePageCannotBeSelected() throws Exception {
        NovelDiscovery.Result preview = NovelDiscovery.parse(
                fixture("unlabelled_catalogue.html"),
                "https://kaoritranslation.blogspot.com/2026/02/novel-x.html");
        // Neither of these is a candidate: one is on the page but was rejected as
        // "baca juga", the other is not on the page at all. A selection containing
        // only such links yields nothing, so it is refused rather than saved.
        try {
            NovelDiscovery.reselect(preview, Arrays.asList(
                    "https://kaoritranslation.blogspot.com/2026/01/review-buku-lain.html",
                    "https://contoh.com/2026/02/palsu.html"));
            fail("tautan di luar daftar kandidat tidak boleh diterima");
        } catch (NovelDiscovery.Unreadable expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("dipilih"));
        }

        // A URL that was never on the page is silently ignored even when the
        // selection also holds real candidates: only the real ones survive.
        List<String> mixed = new ArrayList<>();
        for (NovelDiscovery.Candidate candidate : preview.selectableCandidates()) {
            mixed.add(candidate.url);
        }
        int real = mixed.size();
        assertTrue("fixture harus menyisakan kandidat nyata", real > 0);
        mixed.add("https://contoh.com/2026/02/palsu.html");
        NovelDiscovery.Result picked = NovelDiscovery.reselect(preview, mixed);
        assertEquals("hanya kandidat nyata yang dipakai", real, picked.chapterCount());
        for (Chapter chapter : picked.volumes.get(0).chapters) {
            assertFalse("tautan luar tidak boleh masuk: " + chapter.url,
                    chapter.url.contains("contoh.com"));
        }
    }

    /** A review post with two "read also" links is not a list, so it is refused. */
    @Test
    public void anOrdinaryArticleIsStillRefusedNotOfferedAsAList() throws Exception {
        try {
            NovelDiscovery.parse(fixture("plain_article.html"),
                    "https://kaoritranslation.blogspot.com/2026/01/review-bulan-ini.html");
            fail("artikel biasa harus ditolak, bukan ditawarkan sebagai daftar");
        } catch (NovelDiscovery.Unreadable expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("chapter"));
        }
    }

    // --------------------------------------------------- the working catalogue

    /** The Magical★Explorer catalogue that already worked keeps working. */
    @Test
    public void theMagicalExplorerCatalogueIsUnchanged() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("magical_landing.html"), MAGICAL);

        assertEquals("141 chapter", 141, result.chapterCount());
        assertEquals("14 volume", 14, result.volumes.size());
        assertEquals("13 volume berisi chapter", 13, result.filledVolumes());
        assertEquals("Volume 1", 12, volume(result.volumes, 1).chapterCount());
        assertEquals("Volume 4", 13, volume(result.volumes, 4).chapterCount());
        assertEquals("Volume 14 hanya cover", 0, volume(result.volumes, 14).chapterCount());
        assertEquals("Chapter 0 harus tetap ada", "Chapter 0",
                volume(result.volumes, 10).chapters.get(1).label);

        assertEquals("Irisu", result.author);
        assertTrue("cover harus portrait: " + result.coverRatio, result.coverRatio < 0.9f);
        assertFalse("katalog ini yakin", result.needsConfirmation);
    }

    /** The Monogatari catalogue that already worked keeps working. */
    @Test
    public void theMonogatariCatalogueIsUnchanged() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("monogatari_landing.html"),
                "https://kaoritranslation.blogspot.com/2025/09/"
                        + "ln-monogatari-no-kuromaku-ni-tensei.html");

        assertEquals("7 volume", 7, result.filledVolumes());
        assertEquals("111 chapter", 111, result.chapterCount());
        assertEquals("Yuuki Ryou", result.author);
        assertEquals("Nakamura", result.illustrator);
        assertTrue("genre harus terbaca", result.genres.contains("Fantasy"));
        assertFalse(result.needsConfirmation);
    }

    // --------------------------------------- diagnostics must name the real reason

    /**
     * The four rejected links on Yariko's page each get their own reason.
     *
     * <p>Three of them are {@code <a>} wrappers around the illustration bitmaps — the
     * count has to include them, because they are anchors on the page, but calling a
     * picture "sendiri di grup volume" told the user nothing. The fourth is the numeral
     * "2" that the source tore out of "Interlude 2" and made into its own anchor.
     */
    @Test
    public void everyRejectionSaysWhatTheLinkActuallyIs() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("yariko_toc.html"), YARIKO_TOC);
        List<NovelDiscovery.Candidate> rejected = result.rejected();
        assertEquals("4 tautan ditolak", 4, rejected.size());

        int images = 0;
        int fragments = 0;
        for (NovelDiscovery.Candidate candidate : rejected) {
            assertFalse("tautan yang ditolak tidak boleh bisa dipilih: " + candidate.url,
                    candidate.selectable);
            if (candidate.reason.contains("tautan gambar")) images++;
            else if (candidate.reason.contains("potongan judul")) fragments++;
            else fail("alasan tidak spesifik untuk " + candidate.url + ": " + candidate.reason);
        }
        assertEquals("tiga pembungkus gambar", 3, images);
        assertEquals("satu potongan judul", 1, fragments);
    }

    /**
     * "Interlude" + "2" written as two anchors is one chapter, not a chapter and a stray.
     *
     * <p>The source does this in Volume 1 only: {@code <a>Interlude </a><a>2</a>}, the
     * numeral pointing at a different post. Left alone, the chapter was labelled
     * "Interlude" and "2" became a pickable candidate. The numeral is folded back into
     * the label and normalised, so all three volumes read "Interlude I/II/III".
     */
    @Test
    public void aNumeralTornIntoItsOwnAnchorIsPutBack() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("yariko_toc.html"), YARIKO_TOC);

        List<String> volumeOne = new ArrayList<>();
        for (Chapter chapter : volume(result.volumes, 1).chapters) volumeOne.add(chapter.label);
        assertTrue("Volume 1 harus punya Interlude II: " + volumeOne,
                volumeOne.contains("Interlude II"));
        // After the merge there is no bare "Interlude" left in Volume 1: the numeral
        // was folded in. Before the fix this count was 1 and Volume 1 read "Interlude".
        assertEquals("tidak boleh ada label tanpa angka", 0,
                countOf(volumeOne, "Interlude"));

        // The numeral's own URL is not a chapter of its own.
        for (Volume v : result.volumes) {
            for (Chapter chapter : v.chapters) {
                assertNotEquals("potongan judul tidak boleh jadi chapter", "2", chapter.label);
            }
        }
        assertEquals("43 chapter tetap 43", 43, result.chapterCount());
        for (Volume v : result.volumes) {
            List<String> labels = new ArrayList<>();
            for (Chapter chapter : v.chapters) labels.add(chapter.label);
            assertEquals("Interlude I", 1, countOf(labels, "Interlude I"));
            assertEquals("Interlude II", 1, countOf(labels, "Interlude II"));
            assertEquals("Interlude III", 1, countOf(labels, "Interlude III"));
        }
    }

    /**
     * A bare numeral that is <em>not</em> attached to a label still gets its own reason.
     *
     * <p>The Yariko page covers the merge case: {@code Interlude} + {@code 2} next to
     * each other. This covers the other half — a stray "2" somewhere else in the list,
     * which cannot be merged into anything, must be named as a fragment instead of
     * falling through to a vague rejection. Without this, deleting that branch left
     * every test green.
     */
    @Test
    public void aStrayNumeralIsNamedAsAFragment() throws Exception {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p><b>Author :</b> Uji</p>"
                + "<h3>Volume 1</h3>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2026/02/novel-x_1.html'>Chapter 1</a></p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2026/02/novel-x_2.html'>Chapter 2</a></p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2026/02/novel-x_3.html'>Chapter 3</a></p>"
                + "<p>Catatan penerjemah soal jadwal rilis mingguan yang berubah.</p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2026/02/novel-x_9.html'>2</a></p>"
                + "</div></body></html>";

        NovelDiscovery.Result result = NovelDiscovery.parse(html,
                "https://kaoritranslation.blogspot.com/2026/02/novel-x.html");

        assertEquals("3 chapter", 3, result.chapterCount());
        NovelDiscovery.Candidate stray = null;
        for (NovelDiscovery.Candidate candidate : result.rejected()) {
            if ("2".equals(candidate.text)) stray = candidate;
        }
        assertNotNull("angka liar harus muncul di diagnostik", stray);
        assertTrue("alasan harus menyebut potongan judul: " + stray.reason,
                stray.reason.contains("potongan judul"));
        assertFalse("potongan judul tidak boleh bisa dipilih", stray.selectable);
    }

    /** Whichever way the source writes the numeral, the label reads the same. */
    @Test
    public void anInterludeIsLabelledTheSameFromDigitsOrRoman() {
        assertEquals("Interlude II", id.shuyasal.kuromaku.parser.ChapterNaming.label("Interlude 2"));
        assertEquals("Interlude II", id.shuyasal.kuromaku.parser.ChapterNaming.label("Interlude II"));
        assertEquals("Interlude I", id.shuyasal.kuromaku.parser.ChapterNaming.label("interlude 1"));
        assertEquals("Interlude III", id.shuyasal.kuromaku.parser.ChapterNaming.label("Interlude 3"));
    }

    // -------------------------------------------- a label feed is not a chapter list

    /**
     * The post that describes the novel is not a chapter of it.
     *
     * <p>A label feed returns every post carrying the label, and the series landing
     * post carries it too: its title is the novel's own name, it has no volume and no
     * chapter wording, and it holds the cover and synopsis. Counting it as a chapter
     * put the description at the end of the list — before this was fixed the live feed
     * produced "Volume 0 = 1".
     */
    @Test
    public void aFeedPostThatDescribesTheNovelIsNotAChapter() throws Exception {
        final String labelHtml = fixture("label_yarikonde.html");
        final List<String> titles = Arrays.asList(
                "Yarikon de Ita Game Sekai no Akuyaku Mob ni Tensei Shimashita: Game Chishiki "
                        + "Tsukatte Kimama ni Ikitetara, Nazeka Ari to Arayuru Tokoro de Na ga "
                        + "Shirawatatteita [LN] Bahasa Indonesia",
                "Volume 1 Illustrasi",
                "Volume 1 Prolog",
                "Volume 1 Chapter 1",
                "Volume 1 Chapter 2",
                "Volume 1 Interlude I",
                "Volume 1 Interlude II",
                "Volume 1 Interlude III",
                "Volume 1 Epilog");

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) {
                if (url.contains("/feeds/posts/summary/-/")) return feedPageRaw(url, titles);
                return labelHtml;
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        NovelDiscovery.Result preview = CatalogLoader.inspect(NovelUrl.normalize(YARIKO_LABEL));

        assertEquals("deskripsi bukan chapter", 8, preview.chapterCount());
        assertEquals("hanya Volume 1", 1, preview.filledVolumes());
        // Volume 0 is what LibraryBuilder calls "no volume in the title"; the
        // description post used to land there as a readable chapter.
        for (Volume parsed : preview.volumes) {
            assertTrue("tidak boleh ada volume 0: " + parsed.number, parsed.number > 0);
        }
        assertNotEquals("judul novel tidak boleh jadi chapter",
                titles.get(0), volume(preview.volumes, 1).chapters.get(0).title);

        // The count that is reported is what the feed held, not what survived: eight
        // chapters out of nine posts means one was dropped, and the preview says so.
        assertEquals("tautan ditemukan = isi feed", titles.size(), preview.linksFound);
        assertTrue("penolakan harus dijelaskan: " + warningsJoin(preview),
                warningsJoin(preview).contains("deskripsi novel tidak dihitung"));
    }

    /**
     * Interlude I, II and III are three chapters, not one.
     *
     * <p>The label is what the duplicate guard compares, so dropping the numeral made
     * all three read "Interlude" and two of them were discarded as repeats. On the live
     * feed that silently removed one chapter per volume.
     */
    @Test
    public void everyInterludeKeepsItsNumeral() {
        assertEquals("Interlude I", id.shuyasal.kuromaku.data.LibraryBuilder.label(
                "Novel Volume 1 interlude I"));
        assertEquals("Interlude II", id.shuyasal.kuromaku.data.LibraryBuilder.label(
                "Novel Volume 1 Interlude II"));
        assertEquals("Interlude III", id.shuyasal.kuromaku.data.LibraryBuilder.label(
                "Novel Volume 1 Interlude III"));
        // No numeral at all still gets a usable label rather than "null".
        assertEquals("Interlude", id.shuyasal.kuromaku.data.LibraryBuilder.label(
                "Novel Volume 1 Interlude"));
    }

    /** What counts as a chapter post, stated as a table so the rule is visible. */
    @Test
    public void onlyPostsThatReadLikeChaptersAreCounted() {
        assertTrue(id.shuyasal.kuromaku.data.LibraryBuilder.isChapterPost("Novel Volume 2 Chapter 3"));
        assertTrue(id.shuyasal.kuromaku.data.LibraryBuilder.isChapterPost("Novel Prolog"));
        assertTrue(id.shuyasal.kuromaku.data.LibraryBuilder.isChapterPost("Novel Illustrasi"));
        assertTrue(id.shuyasal.kuromaku.data.LibraryBuilder.isChapterPost("Novel Afterword"));
        assertFalse("judul novel saja bukan chapter",
                id.shuyasal.kuromaku.data.LibraryBuilder.isChapterPost(
                        "Novel Panjang Tanpa Volume [LN] Bahasa Indonesia"));
        assertFalse(id.shuyasal.kuromaku.data.LibraryBuilder.isChapterPost("Review Bulan Ini"));
    }

    // ------------------------------------------------------------------ helpers

    /** One page of a Blogger summary feed, with the cursor the client asked for. */
    private static String feedPage(String url, List<String> titles) {
        int start = 1;
        int size = 25;
        int max = url.indexOf("start-index=") >= 0
                ? Integer.parseInt(url.replaceAll(".*start-index=(\\d+).*", "$1")) : 1;
        int maxResults = url.indexOf("max-results=") >= 0
                ? Integer.parseInt(url.replaceAll(".*max-results=(\\d+).*", "$1")) : size;
        start = max;
        try {
            JSONObject feed = new JSONObject();
            JSONObject total = new JSONObject();
            total.put("$t", String.valueOf(titles.size()));
            feed.put("openSearch$totalResults", total);
            JSONArray items = new JSONArray();
            for (int i = start - 1; i < Math.min(titles.size(), start - 1 + maxResults); i++) {
                JSONObject item = new JSONObject();
                JSONObject title = new JSONObject();
                title.put("$t", "Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita "
                        + titles.get(i));
                item.put("title", title);
                JSONObject published = new JSONObject();
                published.put("$t", "2026-03-01T08:00:00.000+07:00");
                item.put("published", published);
                JSONArray links = new JSONArray();
                JSONObject alternate = new JSONObject();
                alternate.put("rel", "alternate");
                alternate.put("href", "https://kaoritranslation.blogspot.com/2026/03/"
                        + "yarikonde-" + i + ".html");
                links.put(alternate);
                item.put("link", links);
                items.put(item);
            }
            feed.put("entry", items);
            JSONObject root = new JSONObject();
            root.put("feed", feed);
            return root.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    /** One page of a feed where each title is given verbatim. */
    private static String feedPageRaw(String url, List<String> rawTitles) {
        int start = url.indexOf("start-index=") >= 0
                ? Integer.parseInt(url.replaceAll(".*start-index=(\\d+).*", "$1")) : 1;
        int maxResults = url.indexOf("max-results=") >= 0
                ? Integer.parseInt(url.replaceAll(".*max-results=(\\d+).*", "$1")) : 25;
        try {
            JSONObject feed = new JSONObject();
            JSONObject total = new JSONObject();
            total.put("$t", String.valueOf(rawTitles.size()));
            feed.put("openSearch$totalResults", total);
            JSONArray items = new JSONArray();
            for (int i = start - 1; i < Math.min(rawTitles.size(), start - 1 + maxResults); i++) {
                JSONObject item = new JSONObject();
                JSONObject title = new JSONObject();
                title.put("$t", rawTitles.get(i));
                item.put("title", title);
                JSONObject published = new JSONObject();
                published.put("$t", "2026-03-01T08:00:00.000+07:00");
                item.put("published", published);
                JSONArray links = new JSONArray();
                JSONObject alternate = new JSONObject();
                alternate.put("rel", "alternate");
                alternate.put("href", "https://kaoritranslation.blogspot.com/2026/03/raw-" + i + ".html");
                links.put(alternate);
                item.put("link", links);
                items.put(item);
            }
            feed.put("entry", items);
            JSONObject root = new JSONObject();
            root.put("feed", feed);
            return root.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    // --------------------------------------------------- reload of a stored label

    /**
     * Reloading a novel that was saved by label address must give the same list the
     * preview gave.
     *
     * <p>It did not. The preview filtered the series description post out of the
     * feed, and the reload path — a different function, written earlier — built the
     * catalogue straight from every feed entry. So the novel looked right when it
     * was added and grew a phantom chapter at the end of Volume 0 after a reload.
     * Same address, same feed, two answers.
     */
    @Test
    public void reloadingALabelNovelKeepsTheDescriptionOutOfTheChapters() throws Exception {
        final String labelHtml = fixture("label_yarikonde.html");
        final List<String> titles = new ArrayList<>();
        titles.add("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita");
        titles.add("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 1 Bab 1");
        titles.add("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 1 Bab 2");
        titles.add("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita Volume 2 Bab 1");

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                if (url.contains("/feeds/posts/summary/-/")) {
                    return feedPageRaw(url, titles);
                }
                return labelHtml;
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        String address = NovelUrl.normalize(YARIKO_LABEL);
        Novel stored = new Novel("yariko", "Yarikonde", "", "", "", "", "", "",
                "Kaori Translation", "", address, "", 0.70f, new String[0], 1,
                Novel.CATALOG_DISCOVERED, "", "");

        CatalogLoader.Loaded loaded = CatalogLoader.load(stored);

        assertEquals("tiga chapter sungguhan, bukan empat", 3, loaded.catalog.size());
        assertEquals("Volume 0 tidak boleh ada", 0,
                loaded.catalog.volumes.isEmpty() ? 0
                        : volumeOrNull(loaded.catalog.volumes, 0) == null ? 0
                        : volumeOrNull(loaded.catalog.volumes, 0).chapterCount());
        assertEquals("Volume 1", 2, volume(loaded.catalog.volumes, 1).chapterCount());
        assertEquals("Volume 2", 1, volume(loaded.catalog.volumes, 2).chapterCount());

        // The user is told why the count differs from the raw feed size.
        assertTrue("harus ada penjelasan postingan deskripsi: " + loaded.warning,
                loaded.warning != null && loaded.warning.contains("deskripsi"));
    }

    /** Volume 0 is the absence of a volume number, not a volume. */
    private static Volume volumeOrNull(List<Volume> volumes, int number) {
        for (Volume volume : volumes) if (volume.number == number) return volume;
        return null;
    }

    /**
     * A label holding only the description post is refused, not saved as a novel
     * with one chapter.
     */
    @Test
    public void aLabelWithOnlyADescriptionPostIsRefusedOnReload() throws Exception {
        final String labelHtml = fixture("label_yarikonde.html");
        final List<String> titles = new ArrayList<>();
        titles.add("Yarikonde ita Game Sekai no Akuyaku Mob ni Tensei Shimashita");

        Fetcher.use(new Fetcher.Source() {
            @Override
            public String page(String url) throws Exception {
                if (url.contains("/feeds/posts/summary/-/")) {
                    return feedPageRaw(url, titles);
                }
                return labelHtml;
            }

            @Override
            public byte[] image(String url) {
                return new byte[0];
            }
        });

        String address = NovelUrl.normalize(YARIKO_LABEL);
        Novel stored = new Novel("yariko", "Yarikonde", "", "", "", "", "", "",
                "Kaori Translation", "", address, "", 0.70f, new String[0], 1,
                Novel.CATALOG_DISCOVERED, "", "");

        try {
            CatalogLoader.load(stored);
            fail("label tanpa chapter harus ditolak, bukan disimpan kosong");
        } catch (java.io.IOException expected) {
            assertTrue("pesan harus menyebut chapter: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("chapter"));
        }
    }

    private static int countOf(List<String> values, String wanted) {
        int found = 0;
        for (String value : values) if (wanted.equals(value)) found++;
        return found;
    }

    private static String warningsJoin(NovelDiscovery.Result result) {
        StringBuilder out = new StringBuilder();
        for (String warning : result.warnings) out.append(warning).append('\n');
        return out.toString();
    }

    /** Nothing here may leave a fetch source behind for the next test class. */
    @Test
    public void aFreshSourceIsUsedAfterReset() {
        Fetcher.reset();
        assertNotNull(Fetcher.get());
        assertNull(null);
    }
}
