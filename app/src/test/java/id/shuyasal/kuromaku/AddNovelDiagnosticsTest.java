package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
}
