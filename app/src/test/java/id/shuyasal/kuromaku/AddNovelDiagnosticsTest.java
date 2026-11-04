package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.NovelDiscovery;
import id.shuyasal.kuromaku.ui.AddNovelPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The diagnostics panel has to describe the source it actually read.
 *
 * <p>The first line used to say "Tautan ditemukan di artikel" for every address,
 * including a label address whose links come from the JSON feed. Telling the user
 * about a page the app never read is the sort of small lie that makes the rest of
 * the numbers untrustworthy.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class AddNovelDiagnosticsTest {

    private static final String LABEL_URL =
            "https://kaoritranslation.blogspot.com/search/label/Novel%20Uji";
    private static final String ARTICLE_URL =
            "https://kaoritranslation.blogspot.com/2026/02/novel-uji.html";

    private static NovelDiscovery.Result preview(String sourceUrl) {
        List<Chapter> chapters = Arrays.asList(
                new Chapter("Volume 1 Bab 1", "Bab 1", sourceUrl + "#1", "2026-02-01", 1, 1),
                new Chapter("Volume 1 Bab 2", "Bab 2", sourceUrl + "#2", "2026-02-02", 2, 1));
        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, chapters));
        return NovelDiscovery.catalogueFromFeed(sourceUrl, "Novel Uji", "", "Penulis", "", "",
                "", "", 0.70f, new String[]{"Sinopsis."}, volumes,
                new ArrayList<String>(), volumes.get(0).chapterCount());
    }

    private static String foundLine(String sourceUrl) {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = sourceUrl;
        state.preview = preview(sourceUrl);
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        TextView found = page.build().findViewWithTag("add_novel_links_found");
        assertNotNull("panel diagnostik harus punya baris jumlah tautan", found);
        return found.getText().toString();
    }

    /**
     * A form the app accepts must be named on the page.
     *
     * <p>Label addresses were supported since 2.10, but the hint under the heading
     * still mentioned only catalogue articles. A user holding a label URL had no way
     * to know they could paste it — and that URL is the one shape that yields a
     * complete chapter list in a single step.
     */
    @Test
    public void theHintNamesBothAddressShapesItAccepts() {
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), new AddNovelPage.State(), null);
        TextView hint = page.build().findViewWithTag("add_novel_hint");
        assertNotNull("halaman harus punya petunjuk di bawah judul", hint);
        String text = hint.getText().toString();
        assertTrue("petunjuk harus menyebut halaman label: " + text,
                text.contains("/search/label/"));
        assertTrue("petunjuk harus menyebut artikel katalog: " + text,
                text.contains("artikel katalog"));
        assertTrue("petunjuk harus menyebut ?m=1 masih diterima: " + text,
                text.contains("?m=1"));
    }

    @Test
    public void aLabelAddressSaysTheLinksCameFromTheFeed() {
        String line = foundLine(LABEL_URL);
        assertTrue("label dibaca lewat feed, bukan artikel: " + line,
                line.contains("di feed label"));
        assertTrue("jumlah tautan tetap disebut: " + line, line.contains("2"));
    }

    /** Preview built with a status the source may or may not have printed. */
    private static NovelDiscovery.Result previewWithStatus(String status) {
        List<Chapter> chapters = Arrays.asList(
                new Chapter("Volume 1 Bab 1", "Bab 1", ARTICLE_URL + "#1", "2026-02-01", 1, 1));
        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, chapters));
        return NovelDiscovery.catalogueFromFeed(ARTICLE_URL, "Novel Uji", "", "Penulis", "", "",
                status, "", 0.70f, new String[]{"Sinopsis."}, volumes,
                new ArrayList<String>(), volumes.get(0).chapterCount());
    }

    private static String metaLine(String status) {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = ARTICLE_URL;
        state.preview = previewWithStatus(status);
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        TextView line = page.build().findViewWithTag("add_novel_meta_status");
        assertNotNull("halaman pratinjau harus punya baris status", line);
        return line.getText().toString();
    }

    private static NovelDiscovery.Result previewWithMeta(String author, String illustrator,
                                                         String genres, String status) {
        List<Chapter> chapters = Arrays.asList(
                new Chapter("Volume 1 Bab 1", "Bab 1", ARTICLE_URL + "#1", "2026-02-01", 1, 1));
        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, chapters));
        return NovelDiscovery.catalogueFromFeed(ARTICLE_URL, "Novel Uji", "", author, illustrator,
                genres, status, "", 0.70f, new String[]{"Sinopsis."}, volumes,
                new ArrayList<String>());
    }

    /** Reads one preview row by its tag, given four distinct values. */
    private static String rowWithTag(String tag) {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = ARTICLE_URL;
        state.preview = previewWithMeta("Penulis Uji", "Ilustrator Uji", "Fantasi, Petualangan",
                "Ongoing");
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        TextView line = page.build().findViewWithTag(tag);
        assertNotNull("halaman pratinjau harus punya baris " + tag, line);
        return line.getText().toString();
    }

    /**
     * Every preview row must carry its own field, not merely exist.
     *
     * <p>The preview builds four rows through one helper and only the status row had
     * a test. Measured: swapping the author and illustrator values, and printing the
     * status in the genre row, both survived the whole class. A preview whose rows
     * drift is worse than a blank one — it tells the reader the source said something
     * it never said.
     */
    @Test
    public void eachPreviewRowCarriesItsOwnValue() {
        assertEquals("Penulis: Penulis Uji", rowWithTag("add_novel_meta_penulis"));
        assertEquals("Ilustrator: Ilustrator Uji", rowWithTag("add_novel_meta_ilustrator"));
        assertEquals("Genre: Fantasi, Petualangan", rowWithTag("add_novel_meta_genre"));
        assertEquals("Status: Ongoing", rowWithTag("add_novel_meta_status"));
    }

    private static NovelDiscovery.Result previewWithSynopsis(String[] synopsis) {
        List<Chapter> chapters = Arrays.asList(
                new Chapter("Volume 1 Bab 1", "Bab 1", ARTICLE_URL + "#1", "2026-02-01", 1, 1));
        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, chapters));
        return NovelDiscovery.catalogueFromFeed(ARTICLE_URL, "Novel Uji", "", "Penulis", "", "",
                "", "", 0.70f, synopsis, volumes, new ArrayList<String>());
    }

    /** Every paragraph the synopsis box prints, in order. */
    private static List<String> synopsisLines(String[] synopsis) {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = ARTICLE_URL;
        state.preview = previewWithSynopsis(synopsis);
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        View box = page.build().findViewWithTag("add_novel_synopsis_box");
        assertNotNull("halaman pratinjau harus punya kotak sinopsis", box);

        List<String> out = new ArrayList<>();
        collectTagged(box, "add_novel_synopsis", out);
        return out;
    }

    private static void collectTagged(View root, String tag, List<String> out) {
        if (tag.equals(root.getTag()) && root instanceof TextView) {
            out.add(((TextView) root).getText().toString());
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTagged(group.getChildAt(i), tag, out);
            }
        }
    }

    /**
     * An empty synopsis says the source did not print one, in that exact wording.
     *
     * <p>The synopsis box has two branches — no paragraphs, and one view per
     * paragraph — and neither was read by any test: no test referenced the
     * "add_novel_synopsis" tag at all. Measured: printing an empty string instead of
     * the fallback, and printing the fallback instead of every real paragraph, both
     * survived the whole class. An empty box tells the reader the app forgot to read
     * the page.
     */
    @Test
    public void anEmptySynopsisSaysTheSourceDidNotPrintOne() {
        List<String> lines = synopsisLines(new String[0]);
        assertEquals("satu baris cadangan", 1, lines.size());
        assertEquals("Tidak tercantum di halaman sumber", lines.get(0));
    }

    /**
     * Every synopsis paragraph is shown, verbatim and in order.
     *
     * <p>Guards the other direction: a branch that always prints the fallback would
     * pass the test above while hiding the whole synopsis.
     */
    @Test
    public void everySynopsisParagraphIsShownInOrder() {
        List<String> lines = synopsisLines(
                new String[]{"Paragraf pertama.", "Paragraf kedua.", "Paragraf ketiga."});
        assertEquals(Arrays.asList("Paragraf pertama.", "Paragraf kedua.", "Paragraf ketiga."),
                lines);
    }

    /** Two volumes holding three chapters between them: the two counts differ. */
    private static String countsLine(String tag) {
        List<Volume> volumes = new ArrayList<>();
        volumes.add(new Volume(1, Arrays.asList(
                new Chapter("Volume 1 Bab 1", "Bab 1", ARTICLE_URL + "#1", "2026-02-01", 1, 1),
                new Chapter("Volume 1 Bab 2", "Bab 2", ARTICLE_URL + "#2", "2026-02-02", 2, 1))));
        volumes.add(new Volume(2, Arrays.asList(
                new Chapter("Volume 2 Bab 1", "Bab 1", ARTICLE_URL + "#3", "2026-02-03", 3, 2))));

        AddNovelPage.State state = new AddNovelPage.State();
        state.url = ARTICLE_URL;
        state.preview = NovelDiscovery.catalogueFromFeed(ARTICLE_URL, "Novel Uji", "",
                "Penulis", "", "", "", "", 0.70f, new String[]{"Sinopsis."}, volumes,
                new ArrayList<String>());
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        TextView line = page.build().findViewWithTag(tag);
        assertNotNull("halaman pratinjau harus punya baris " + tag, line);
        return line.getText().toString();
    }

    /**
     * The counts row prints volumes in one slot and chapters in the other.
     *
     * <p>Both numbers come from the parser and the parser's numbers are already
     * tested, which made this row look covered. It was not: no test named either
     * "add_novel_volume_count" or "add_novel_chapter_count". Measured — printing the
     * chapter count in the volume slot, and the volume count in the chapter slot,
     * both survived the whole class. The two values differ here on purpose (2 volumes,
     * 3 chapters); with equal values a swap would be invisible even to this test.
     */
    @Test
    public void theCountsRowKeepsVolumesAndChaptersInTheirOwnSlot() {
        assertEquals("2 volume", countsLine("add_novel_volume_count"));
        assertEquals(" · 3 chapter ditemukan", countsLine("add_novel_chapter_count"));
    }

    /**
     * A field the source never printed says so, in its exact wording.
     *
     * <p>The preview shows four metadata rows built by the same helper — penulis,
     * ilustrator, genre, status — and that helper substitutes one generic sentence
     * when the value is blank. Nothing asserted that sentence: it could be changed
     * to an empty string, to "Unknown", or to a guess, and every test still passed.
     * A blank row tells the user the app forgot to read the page; a guess tells them
     * something the page never said. Both are worse than saying it is not printed.
     */
    @Test
    public void aFieldTheSourceDidNotPrintSaysSo() {
        assertEquals("Status: Tidak tercantum di halaman sumber", metaLine(""));
        assertEquals("spasi saja juga dihitung kosong", "Status: Tidak tercantum di halaman sumber",
                metaLine("   "));
    }

    /**
     * A status the source did print is shown verbatim, never replaced by the fallback.
     *
     * <p>Guards the other direction: a helper that always prints the fallback would
     * pass the test above while hiding every real status the source states.
     */
    @Test
    public void aStatusTheSourceDidPrintKeepsItsWording() {
        assertEquals("Status: Ongoing", metaLine("Ongoing"));
        assertEquals("Status: Completed", metaLine("Completed"));
    }

    @Test
    public void anArticleAddressSaysTheLinksCameFromTheArticle() {
        String line = foundLine(ARTICLE_URL);
        assertTrue("artikel dibaca dari halaman itu sendiri: " + line,
                line.contains("di artikel"));
        assertTrue("jumlah tautan tetap disebut: " + line, line.contains("2"));
    }

    /**
     * When a novel is already in library, a warning notice informs the reader.
     */
    @Test
    public void duplicateNovelShowsWarningNotice() {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = ARTICLE_URL;
        state.preview = preview(ARTICLE_URL);
        state.duplicate = true;
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        TextView notice = page.build().findViewWithTag("add_novel_duplicate");
        assertNotNull("peringatan novel duplikat harus ada saat state.duplicate true", notice);
        assertEquals("Novel ini sudah ada di perpustakaan Tuan. Menyimpan lagi tidak akan membuat duplikat.",
                notice.getText().toString());
    }

    /**
     * When a novel is not in library, the duplicate warning notice is absent.
     */
    @Test
    public void nonDuplicateNovelHidesWarningNotice() {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = ARTICLE_URL;
        state.preview = preview(ARTICLE_URL);
        state.duplicate = false;
        AddNovelPage page = new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, null);
        TextView notice = page.build().findViewWithTag("add_novel_duplicate");
        org.junit.Assert.assertNull("peringatan novel duplikat tidak boleh ada saat state.duplicate false", notice);
    }
}
