package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import id.shuyasal.kuromaku.parser.NovelDiscovery;
import id.shuyasal.kuromaku.ui.AddNovelPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * The manual picker: what it offers, what it records, and when it acts.
 *
 * <p>The user asked for a fallback when the parser is unsure — show the links, let
 * the user pick, and never treat every link as a chapter. That promise is only kept
 * if the picker obeys three rules, and until now none of them had a test:
 *
 * <ul>
 *   <li>it offers only links that <em>could</em> be chapters — never navigation, an
 *       advertisement, a label page, or another novel's post;</li>
 *   <li>a tick records that link and an untick removes it, so the selection is what
 *       is on screen;</li>
 *   <li>nothing reaches the activity before "Pakai pilihan ini" is pressed, and what
 *       reaches it is exactly the ticked set.</li>
 * </ul>
 *
 * <p>Driven through the real page on a 320 dp phone, so the checkboxes are the ones
 * the user would actually tap.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class AddNovelPickerTest {

    private static final String PAGE =
            "https://kaoritranslation.blogspot.com/2026/02/novel-x.html";

    /** Every selection the page handed to the activity, in order. */
    private final List<List<String>> applied = new ArrayList<>();

    private static String fixture(String name) throws Exception {
        try (InputStream in = AddNovelPickerTest.class.getClassLoader()
                .getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture katalog/" + name + " hilang", in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    /**
     * A catalogue with four links that share one slug stem and none of them saying
     * "chapter": the parser cannot name any of them, so it must ask.
     */
    private NovelDiscovery.Result uncertainCatalogue() throws Exception {
        NovelDiscovery.Result preview =
                NovelDiscovery.parse(fixture("unlabelled_catalogue.html"), PAGE);
        assertTrue("prasyarat: parser harus tidak yakin", preview.needsConfirmation);
        return preview;
    }

    private AddNovelPage page(NovelDiscovery.Result preview, boolean picking) {
        AddNovelPage.State state = new AddNovelPage.State();
        state.url = PAGE;
        state.preview = preview;
        state.picking = picking;
        return new AddNovelPage(RuntimeEnvironment.getApplication(),
                Palette.of(Prefs.THEME_DARK), state, new AddNovelPage.Listener() {
                    @Override
                    public void onFetch(String url) {
                    }

                    @Override
                    public void onRetry() {
                    }

                    @Override
                    public void onSave() {
                    }

                    @Override
                    public void onApplySelection(List<String> urls) {
                        applied.add(new ArrayList<>(urls));
                    }
                });
    }

    private static void collect(View root, String tag, List<View> out) {
        if (root == null) return;
        if (tag.equals(root.getTag())) out.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), tag, out);
            }
        }
    }

    private static List<CheckBox> checkBoxes(View root) {
        List<View> found = new ArrayList<>();
        collect(root, "add_novel_candidate", found);
        List<CheckBox> boxes = new ArrayList<>();
        for (View view : found) boxes.add((CheckBox) view);
        return boxes;
    }

    // ------------------------------------------------------------ what it offers

    /**
     * Only links that could be a chapter are offered.
     *
     * <p>The fixture holds five series links; one of them points at the page being
     * read, so it can never be a chapter of itself. The picker must show four — no
     * more, because a selectable navigation link would let a tap import an
     * advertisement as a chapter.
     */
    @Test
    public void thePickerOffersOnlyLinksThatCouldBeChapters() throws Exception {
        NovelDiscovery.Result preview = uncertainCatalogue();
        AddNovelPage page = page(preview, true);

        View root = page.build();
        List<CheckBox> boxes = checkBoxes(root);
        assertEquals("kotak centang harus sebanyak kandidat yang boleh dipilih",
                preview.selectableCandidates().size(), boxes.size());
        assertTrue("harus ada yang bisa dipilih: " + boxes.size(), boxes.size() >= 3);

        // The heading states the proportion, so a truncated list is not mistaken for
        // the whole page.
        List<View> heading = new ArrayList<>();
        collect(root, "add_novel_picker", heading);
        assertTrue("picker harus muncul", !heading.isEmpty());
        String text = ((TextView) ((ViewGroup) heading.get(0)).getChildAt(0))
                .getText().toString();
        assertTrue("judul harus menyebut jumlah yang bisa dipakai: " + text,
                text.contains(String.valueOf(preview.selectableCandidates().size())));
        assertTrue("judul harus menyebut jumlah seluruh tautan: " + text,
                text.contains(String.valueOf(preview.candidates.size())));

        // Nothing is applied just because the list is on screen.
        assertTrue("belum ada pilihan yang boleh dikirim tanpa tombol", applied.isEmpty());
    }

    // ---------------------------------------------------------- what it records

    /**
     * Ticking and unticking is what the selection is; the button sends exactly that.
     *
     * <p>Two links are ticked and one of them is ticked again — an untick — so the
     * result cannot be explained by "collect every box the user touched".
     */
    @Test
    public void applySendsExactlyTheLinksLeftTicked() throws Exception {
        NovelDiscovery.Result preview = uncertainCatalogue();
        AddNovelPage page = page(preview, true);

        View root = page.build();
        List<CheckBox> boxes = checkBoxes(root);
        List<NovelDiscovery.Candidate> selectable = preview.selectableCandidates();
        assertTrue("butuh minimal tiga kotak untuk tes ini", boxes.size() >= 3);

        boxes.get(0).performClick();
        boxes.get(1).performClick();
        boxes.get(2).performClick();
        boxes.get(2).performClick();   // untick again

        assertTrue("masih belum ada yang dikirim sebelum tombol ditekan", applied.isEmpty());

        List<View> apply = new ArrayList<>();
        collect(root, "add_novel_apply_selection", apply);
        assertEquals("harus ada satu tombol pakai pilihan", 1, apply.size());
        ((Button) apply.get(0)).performClick();

        assertEquals("tombol harus mengirim tepat satu pilihan", 1, applied.size());
        List<String> sent = applied.get(0);
        assertEquals("dua tautan yang masih tercentang", 2, sent.size());
        assertTrue("tautan pertama yang dicentang harus ikut",
                sent.contains(selectable.get(0).url));
        assertTrue("tautan kedua yang dicentang harus ikut",
                sent.contains(selectable.get(1).url));
        assertTrue("tautan yang dibatalkan centangnya tidak boleh ikut: " + sent,
                !sent.contains(selectable.get(2).url));
    }

    /**
     * An accepted catalogue is not offered the picker unless the user asks for it.
     *
     * <p>A confident parse already has its chapters; showing eighty checkboxes over
     * it would suggest the list is a guess.
     */
    @Test
    public void aConfidentCatalogueHidesThePicker() throws Exception {
        NovelDiscovery.Result confident = NovelDiscovery.parse(
                fixture("yariko_toc.html"),
                "https://kaoritranslation.blogspot.com/2026/03/yarikon-de-ita-game-sekai-no-akuyaku.html");
        assertTrue("prasyarat: katalog ini harus terbaca dengan yakin",
                confident.chapterCount() > 0);
        assertTrue("prasyarat: tidak perlu konfirmasi", !confident.needsConfirmation);

        View root = page(confident, false).build();
        List<View> picker = new ArrayList<>();
        collect(root, "add_novel_picker", picker);
        assertTrue("picker tidak boleh muncul tanpa diminta", picker.isEmpty());
        assertTrue("tidak ada kotak centang yang menyesatkan",
                checkBoxes(root).isEmpty());
        assertTrue("dan tidak ada yang dikirim", applied.isEmpty());
    }
}
