package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.NovelUrl;
import id.shuyasal.kuromaku.parser.NovelDiscovery;

/**
 * Adding a novel by URL, decided before anything is written to the shelf.
 *
 * <p>These are the checks that run the moment the user presses "Ambil Katalog":
 * the text must be a Kaori Translation article page, the same page typed twice
 * must resolve to one identity, a plain blog post must be refused instead of
 * being stored as an empty novel, and a catalogue whose layout differs from the
 * two built-in novels must still be readable.
 *
 * <p>Everything here runs on the real parser against real page shapes; the only
 * thing simulated is the download itself.
 */
public class DynamicCatalogueTest {

    private static final String BASE =
            "https://kaoritranslation.blogspot.com/2026/02/novel-uji.html";

    private static String fixture(String name) throws Exception {
        try (InputStream in = DynamicCatalogueTest.class.getClassLoader()
                .getResourceAsStream("catalog/" + name)) {
            if (in == null) return "";
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // ---------------------------------------------------------- skenario 3: URL

    /** Skenario 3: a URL that is not a usable article page is refused, with a reason. */
    @Test
    public void invalidUrlsAreRefusedWithAReason() {
        String[] bad = {
                "",
                "   ",
                "bukan url sama sekali",
                "ftp://kaoritranslation.blogspot.com/2026/02/novel.html",
                "https://contoh.com/2026/02/novel.html",
                "https://kaoritranslation.blogspot.com/",
                "https://kaoritranslation.blogspot.com/search?q=novel",
                "https://kaoritranslation.blogspot.com/feeds/posts/default",
                "https://kaoritranslation.blogspot.com/p/tentang.html",
                "https://kaoritranslation.blogspot.com/2026/02",
        };
        for (String url : bad) {
            try {
                String result = NovelUrl.normalize(url);
                fail("URL harus ditolak: [" + url + "] tetapi diterima sebagai " + result);
            } catch (NovelUrl.Invalid expected) {
                assertTrue("alasan penolakan harus bisa dibaca user, dapat: "
                                + expected.getMessage(),
                        expected.getMessage() != null && expected.getMessage().length() > 8);
            }
        }
    }

    /** The same page written differently is one identity, never two novels. */
    @Test
    public void onePageHasOneIdentityWhateverTheSpelling() throws Exception {
        String canonical = "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari.html";
        String[] spellings = {
                canonical,
                canonical + "?m=1",
                canonical + "/",
                canonical + "?m=1&utm_source=discord",
                "https://www.kaoritranslation.blogspot.com/2025/09/ln-monogatari.html",
                "http://kaoritranslation.blogspot.com/2025/09/ln-monogatari.html",
                "https://KAORITranslation.blogspot.com/2025/09/ln-monogatari.html",
                "  " + canonical + "  ",
                "kaoritranslation.blogspot.com/2025/09/ln-monogatari.html",
        };
        for (String spelling : spellings) {
            assertEquals("bentuk URL ini harus menunjuk halaman yang sama: " + spelling,
                    canonical, NovelUrl.normalize(spelling));
            assertEquals("id novel harus sama untuk " + spelling,
                    "ln-monogatari", NovelUrl.id(NovelUrl.normalize(spelling)));
        }
    }

    /** Different novels keep different ids, so their data cannot merge. */
    @Test
    public void differentPagesGetDifferentIds() throws Exception {
        String first = NovelUrl.id(NovelUrl.normalize(
                "https://kaoritranslation.blogspot.com/2025/09/ln-satu.html"));
        String second = NovelUrl.id(NovelUrl.normalize(
                "https://kaoritranslation.blogspot.com/2025/10/ln-dua.html"));
        assertNotEquals(first, second);
        assertTrue("id harus aman untuk nama folder", first.matches("[a-z0-9-]+"));
    }

    // ------------------------------------------------- skenario 4: bukan katalog

    /**
     * Skenario 4: an ordinary article is refused.
     *
     * <p>The important half of this test is that nothing is invented: no chapter
     * is created from a "Baca juga" link, and the failure is an exception the
     * preview can print rather than an empty novel that gets saved.
     */
    @Test
    public void aPlainArticleIsRefusedInsteadOfStoredEmpty() throws Exception {
        String html = fixture("plain_article.html");
        assertFalse("fixture artikel biasa harus ada", html.isEmpty());
        try {
            NovelDiscovery.Result result = NovelDiscovery.parse(html, BASE);
            fail("artikel biasa harus ditolak, tetapi menghasilkan "
                    + result.chapterCount() + " chapter");
        } catch (NovelDiscovery.Unreadable expected) {
            assertTrue("pesan harus menjelaskan masalahnya: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("chapter"));
        }
    }

    /** A page with no article body at all is refused just as clearly. */
    @Test
    public void aPageWithoutAnArticleBodyIsRefused() {
        try {
            NovelDiscovery.parse("<html><body><nav><a href='/2026/01/a.html'>Chapter 1</a>"
                    + "</nav></body></html>", BASE);
            fail("halaman tanpa isi artikel harus ditolak");
        } catch (NovelDiscovery.Unreadable expected) {
            assertTrue(expected.getMessage().length() > 10);
        }
    }

    // ----------------------------------------------- skenario 5: format berbeda

    /**
     * Skenario 5: a catalogue that does not look like the two built-in novels.
     *
     * <p>This page uses {@code entry-content} instead of {@code post-body},
     * {@code h4} volume headings, and an ordered list for one volume, plus bold
     * labels with a space before the colon. The generic parser must still read
     * both volumes, order chapter 2 correctly even though the page spells it
     * "Cahpter 2", and pick the portrait cover rather than the wide banner above
     * it.
     */
    @Test
    public void aCatalogueInADifferentLayoutIsReadCompletely() throws Exception {
        NovelDiscovery.Result result = NovelDiscovery.parse(fixture("dynamic_catalogue.html"), BASE);

        assertEquals("enam tautan chapter harus terbaca", 6, result.chapterCount());
        assertEquals("dua volume", 2, result.filledVolumes());

        Volume first = volume(result.volumes, 1);
        Volume second = volume(result.volumes, 2);
        assertEquals("Volume 1 berisi 4 tautan", 4, first.chapterCount());
        assertEquals("Volume 2 berisi 2 tautan", 2, second.chapterCount());

        List<Chapter> one = first.chapters;
        assertEquals("Prolog", one.get(0).label);
        assertEquals("Chapter 1", one.get(1).label);
        assertEquals("Chapter 2", one.get(2).label);
        assertEquals("Illustrasi", one.get(3).label);

        // Each chapter points at its own post, not at the catalogue page.
        for (Chapter chapter : one) {
            assertTrue("URL chapter harus absolut: " + chapter.url,
                    chapter.url.startsWith("https://kaoritranslation.blogspot.com/2026/02/"));
            assertNotEquals("chapter tidak boleh menunjuk halaman katalog", BASE, chapter.url);
        }

        // Metadata that the page prints is read; nothing is invented.
        assertEquals("Novel Uji Format Berbeda", result.title);
        assertEquals("Penulis Uji", result.author);
        assertEquals("Ilustrator Uji", result.illustrator);
        assertEquals("Fantasy, Adventure, Slice of Life", result.genres);
        assertEquals("Ongoing", result.status);
        assertTrue("sinopsis harus terbaca", result.synopsis.length >= 2);

        // Cover: the portrait picture inside the link, not the wide banner.
        assertTrue("cover harus ilustrasi bersampul: " + result.coverUrl,
                result.coverUrl.contains("cover_uji"));
        assertFalse("banner lebar tidak boleh dipakai sebagai cover",
                result.coverUrl.contains("banner_uji"));
        assertTrue("rasio cover harus portrait", result.coverRatio < 0.9f);
    }

    /** Site chrome is never turned into a chapter. */
    @Test
    public void navigationAndFooterLinksAreNotChapters() throws Exception {
        NovelDiscovery.Result result =
                NovelDiscovery.parse(fixture("dynamic_catalogue.html"), BASE);
        for (Volume volume : result.volumes) {
            for (Chapter chapter : volume.chapters) {
                String url = chapter.url;
                assertFalse("tautan menu tidak boleh jadi chapter: " + url,
                        url.contains("/search") || url.endsWith("/") == false && url.contains("#"));
                assertTrue("hanya post bertanggal yang boleh jadi chapter: " + url,
                        url.contains("/2026/02/"));
            }
        }
    }

    // ------------------------------------------ the two real pages, unchanged

    /**
     * The same generic parser reads both novels that ship with the app, so a
     * user-added novel is not treated by a weaker code path than the built-ins.
     */
    @Test
    public void theGenericParserAlsoReadsTheTwoBuiltInPages() throws Exception {
        NovelDiscovery.Result monogatari = NovelDiscovery.parse(
                fixture("monogatari_landing.html"),
                "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari.html");
        assertEquals("Yuuki Ryou", monogatari.author);
        assertEquals("Nakamura", monogatari.illustrator);
        assertTrue("genre harus terpisah dari labelnya: " + monogatari.genres,
                monogatari.genres.startsWith("Action"));
        assertEquals("tujuh volume Monogatari", 7, monogatari.filledVolumes());

        NovelDiscovery.Result magical = NovelDiscovery.parse(
                fixture("magical_landing.html"),
                "https://kaoritranslation.blogspot.com/2025/04/ln-magicalexplorer.html");
        assertEquals("Irisu", magical.author);
        assertEquals("Kannatsuki Noboru", magical.illustrator);
        assertEquals("tiga belas volume berisi chapter", 13, magical.filledVolumes());
        assertTrue("volume 14 tanpa tautan tidak boleh diberi chapter palsu",
                volume(magical.volumes, 14) == null
                        || volume(magical.volumes, 14).chapterCount() == 0);
    }

    // ------------------------------- skenario 7: perbarui daftar chapter

    /**
     * Skenario 7: after the source publishes a new chapter, reading the page again
     * shows it.
     *
     * <p>This is the half of a refresh that metadata tests do not cover. The page is
     * parsed once, an extra volume is appended to the same markup, and the page is
     * parsed again — so what is asserted is that a re-read of the source produces a
     * larger catalogue, not that a stored list was mutated in place.
     */
    @Test
    public void rereadingThePagePicksUpANewlyPublishedChapter() throws Exception {
        String before = fixture("dynamic_catalogue.html");
        NovelDiscovery.Result first = NovelDiscovery.parse(before, BASE);
        assertEquals("katalog awal", 6, first.chapterCount());
        assertEquals(2, first.filledVolumes());

        // The same page after one more volume was published.
        String after = before.replace("</div>",
                "<h4>Volume&nbsp;3</h4>\n<ul>"
                        + "<li><a href=\"/2026/04/novel-uji-v3-chapter-1.html\">Chapter 1</a></li>"
                        + "<li><a href=\"/2026/04/novel-uji-v3-epilog.html\">Epilog</a></li>"
                        + "</ul>\n</div>");
        assertNotEquals("fixture harus benar-benar berubah", before, after);

        NovelDiscovery.Result second = NovelDiscovery.parse(after, BASE);

        assertEquals("chapter baru harus ikut terbaca", 8, second.chapterCount());
        assertEquals("volume ketiga harus muncul", 3, second.filledVolumes());
        Volume third = volume(second.volumes, 3);
        assertNotNull("Volume 3 harus ada", third);
        assertEquals(2, third.chapterCount());
        assertEquals("Chapter 1", third.chapters.get(0).label);
        assertEquals("Epilog", third.chapters.get(1).label);

        // The old chapters keep their identity: a refresh must not renumber them.
        assertEquals("chapter lama tidak boleh berubah URL",
                volume(first.volumes, 1).chapters.get(1).url,
                volume(second.volumes, 1).chapters.get(1).url);
    }

    private static Volume volume(List<Volume> volumes, int number) {
        for (Volume volume : volumes) {
            if (volume.number == number) return volume;
        }
        return null;
    }
}
