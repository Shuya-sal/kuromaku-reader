package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.LandingCatalogParser;

/**
 * Catalogue parsing for Magical★Explorer, driven by the real landing page.
 *
 * <p>The page is the catalogue for this novel: it prints a "Volume N" heading
 * and then the chapter links for that volume. These tests pin the behaviour the
 * reader depends on — volume detection, the source's misspelled "Cahpter 2",
 * Chapter 0 ordering, extras, and the rule that only links become chapters.
 */
public class LandingCatalogParserTest {

    private static final String SLUG = "ln-magical-explorer-";

    private static String fixture(String name) throws Exception {
        try (InputStream in = LandingCatalogParserTest.class
                .getClassLoader().getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture tidak ditemukan: " + name, in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    private static List<Volume> magical() throws Exception {
        return LandingCatalogParser.parse(fixture("magical_landing.html"), SLUG, "magical-explorer");
    }

    private static Chapter find(Volume volume, String label) {
        for (Chapter chapter : volume.chapters) {
            if (chapter.label.equals(label)) return chapter;
        }
        return null;
    }

    /** All fourteen volumes the source actually lists must be detected. */
    @Test
    public void detectsEveryVolumeOnTheSourcePage() throws Exception {
        List<Volume> volumes = magical();
        List<Integer> numbers = new ArrayList<>();
        for (Volume volume : volumes) numbers.add(volume.number);

        assertEquals("jumlah volume", 14, volumes.size());
        assertEquals("Volume 1..14 berurutan",
                java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), numbers);
    }

    /** Volume 1 and 2 are the ones the user asked to be able to open. */
    @Test
    public void volumeOneAndTwoHaveTheirChapters() throws Exception {
        List<Volume> volumes = magical();
        Volume one = volumes.get(0);
        Volume two = volumes.get(1);

        assertEquals(1, one.number);
        assertEquals("Volume 1 punya 12 tautan", 12, one.chapters.size());
        assertEquals(2, two.number);
        assertEquals("Volume 2 punya 9 tautan", 9, two.chapters.size());

        assertEquals("ln-magical-explorer-volume-1-chapter-1",
                slug(find(one, "Chapter 1").url));
        assertEquals("ln-magical-explorer-volume-2-chapter-1",
                slug(find(two, "Chapter 1").url));
    }

    /** Every chapter must point at this novel, never at another series. */
    @Test
    public void everyChapterBelongsToThisNovel() throws Exception {
        for (Volume volume : magical()) {
            for (Chapter chapter : volume.chapters) {
                assertTrue("URL harus milik novel ini: " + chapter.url,
                        chapter.url.contains(SLUG));
                assertTrue("URL harus postingan Kaori Translation: " + chapter.url,
                        chapter.url.contains("kaoritranslation.blogspot.com"));
                assertFalse("URL tidak boleh kosong", chapter.url.isEmpty());
                assertEquals("volume chapter harus sama dengan volume induknya",
                        volume.number, chapter.volume);
            }
        }
    }

    /** The source spells volume 3 chapter 2 as "Cahpter 2"; order must survive. */
    @Test
    public void misspelledChapterStillSortsInPlace() throws Exception {
        Volume three = magical().get(2);
        assertEquals(3, three.number);

        List<String> labels = new ArrayList<>();
        for (Chapter chapter : three.chapters) labels.add(chapter.label);

        assertTrue("label harus dibakukan: " + labels, labels.contains("Chapter 2"));
        assertFalse("salah eja tidak boleh tampil: " + labels, labels.contains("Cahpter 2"));

        // Chapter 2 must sit between Chapter 1 and Chapter 3 in reading order.
        int one = labels.indexOf("Chapter 1");
        int two = labels.indexOf("Chapter 2");
        int threeLabel = labels.indexOf("Chapter 3");
        assertTrue(one >= 0 && two > one && threeLabel > two);
    }

    /** Chapter 0 must come before Chapter 1 in volume 10. */
    @Test
    public void chapterZeroSortsBeforeChapterOne() throws Exception {
        Volume ten = magical().get(9);
        assertEquals(10, ten.number);
        List<String> labels = new ArrayList<>();
        for (Chapter chapter : ten.chapters) labels.add(chapter.label);

        int zero = labels.indexOf("Chapter 0");
        int one = labels.indexOf("Chapter 1");
        assertTrue("Chapter 0 harus ada: " + labels, zero >= 0);
        assertTrue("Chapter 0 harus sebelum Chapter 1: " + labels, zero < one);
    }

    /** Prolog, Epilog/Afterword and Illustrasi are kept, not dropped. */
    @Test
    public void prologEpilogAndIllustrationsAreKept() throws Exception {
        List<Volume> volumes = magical();
        List<String> all = new ArrayList<>();
        for (Volume volume : volumes) {
            for (Chapter chapter : volume.chapters) all.add(chapter.label);
        }
        assertTrue("Prolog volume 8 harus ada: " + all, all.contains("Prolog"));
        assertTrue("Illustrasi harus ada: " + all, all.contains("Illustrasi"));
        assertTrue("Afterword harus dikenali",
                anyStartsWith(all, "Chapter 8 + Afterword")
                        || anyStartsWith(all, "Chapter 10 + Afterword")
                        || all.contains("Afterword") || all.contains("Epilog"));
    }

    /** Illustrasi is ordered last inside its volume, as printed. */
    @Test
    public void illustrationSortsAfterTheChapters() throws Exception {
        Volume one = magical().get(0);
        List<String> labels = new ArrayList<>();
        for (Chapter chapter : one.chapters) labels.add(chapter.label);
        int illustration = labels.indexOf("Illustrasi");
        int lastChapter = labels.indexOf("Chapter 10");
        assertTrue(illustration >= 0);
        assertTrue("Illustrasi setelah chapter terakhir: " + labels,
                illustration > lastChapter);
    }

    /** Prolog precedes Chapter 1 in a volume that has one. */
    @Test
    public void prologSortsBeforeFirstChapter() throws Exception {
        Volume eight = magical().get(7);
        assertEquals(8, eight.number);
        List<String> labels = new ArrayList<>();
        for (Chapter chapter : eight.chapters) labels.add(chapter.label);
        int prolog = labels.indexOf("Prolog");
        int firstChapter = labels.indexOf("Chapter 1");
        assertTrue(prolog >= 0 && prolog < firstChapter);
    }

    /** Headings without a link must not become chapters. */
    @Test
    public void plainTextNeverBecomesAChapter() throws Exception {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>Volume 1</p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-magical-explorer-volume-1-chapter-1.html'>Chapter 1</a></p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-magical-explorer-volume-1-chapter-2.html'>Chapter 2</a></p>"
                + "<p>Chapter 3 (belum diterjemahkan)</p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-magical-explorer-volume-1-chapter-4.html'>Chapter 4</a></p>"
                + "</div></body></html>";

        List<Volume> volumes = LandingCatalogParser.parse(html, SLUG, "magical-explorer");
        assertEquals(1, volumes.size());
        List<String> labels = new ArrayList<>();
        for (Chapter chapter : volumes.get(0).chapters) labels.add(chapter.label);

        assertEquals("hanya tautan yang jadi chapter", 3, labels.size());
        assertFalse("teks tanpa tautan tidak boleh jadi chapter: " + labels,
                labels.contains("Chapter 3"));
    }

    /** A link to a different novel must be rejected outright. */
    @Test
    public void foreignNovelLinksAreRejected() {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>Volume 1</p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-magical-explorer-volume-1-chapter-1.html'>Chapter 1</a></p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/09/ln-monogatari-no-kuromaku-volume-1-chapter-1.html'>Chapter 1</a></p>"
                + "</div></body></html>";

        List<Volume> volumes = LandingCatalogParser.parse(html, SLUG, "magical-explorer");
        assertEquals(1, volumes.size());
        assertEquals(1, volumes.get(0).chapters.size());
        assertTrue(volumes.get(0).chapters.get(0).url.contains(SLUG));
    }

    /** Volume headings written with &nbsp; are still recognised. */
    @Test
    public void volumeHeadingWithNonBreakingSpaceIsRecognised() {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>Volume&nbsp;2</p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-magical-explorer-volume-2-chapter-1.html'>Chapter 1</a></p>"
                + "</div></body></html>";
        List<Volume> volumes = LandingCatalogParser.parse(html, SLUG, "magical-explorer");
        assertEquals(1, volumes.size());
        assertEquals(2, volumes.get(0).number);
    }

    /** Chapter numbering from the real page matches what the source prints. */
    @Test
    public void chapterCountsMatchTheSource() throws Exception {
        // Volume 14 is announced with a cover on the page but has no chapter link
        // yet, so it is kept as an empty volume instead of a made-up chapter.
        int[] expected = {12, 9, 11, 13, 11, 9, 11, 11, 11, 11, 10, 10, 12, 0};
        List<Volume> volumes = magical();
        assertEquals(expected.length, volumes.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("jumlah tautan volume " + (i + 1),
                    expected[i], volumes.get(i).chapters.size());
        }
    }

    /** Volume 14 must be listed (cover only) and must not invent a chapter. */
    @Test
    public void volumeWithoutChaptersIsKeptEmpty() throws Exception {
        List<Volume> volumes = magical();
        Volume last = volumes.get(volumes.size() - 1);
        assertEquals(14, last.number);
        assertTrue("volume 14 belum punya chapter di sumber", last.chapters.isEmpty());
    }

    private static boolean anyStartsWith(List<String> labels, String prefix) {
        for (String label : labels) {
            if (label.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String slug(String url) {
        int slash = url.lastIndexOf('/');
        return url.substring(slash + 1).replace(".html", "");
    }
}
