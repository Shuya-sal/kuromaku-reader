package id.shuyasal.kuromaku;

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

    @Test
    public void aLabelAddressSaysTheLinksCameFromTheFeed() {
        String line = foundLine(LABEL_URL);
        assertTrue("label dibaca lewat feed, bukan artikel: " + line,
                line.contains("di feed label"));
        assertTrue("jumlah tautan tetap disebut: " + line, line.contains("2"));
    }

    @Test
    public void anArticleAddressSaysTheLinksCameFromTheArticle() {
        String line = foundLine(ARTICLE_URL);
        assertTrue("artikel dibaca dari halaman itu sendiri: " + line,
                line.contains("di artikel"));
        assertTrue("jumlah tautan tetap disebut: " + line, line.contains("2"));
    }
}
