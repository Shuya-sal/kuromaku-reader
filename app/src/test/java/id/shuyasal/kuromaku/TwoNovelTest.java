package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.NovelListPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Two novels in one app.
 *
 * <p>These tests cover the behaviour the reader must get right once a second
 * novel exists: both novels appear in the library, each has its own volumes, the
 * bookmark of one never shows up in the other, and chapter navigation cannot walk
 * from one book into the other.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-mdpi")
public class TwoNovelTest {

    private static final String MONO_1 = "https://kaoritranslation.blogspot.com/2025/09/"
            + "ln-monogatari-no-kuromaku-ni-tensei-volume-1-chapter-1.html";
    private static final String MONO_2 = "https://kaoritranslation.blogspot.com/2025/09/"
            + "ln-monogatari-no-kuromaku-ni-tensei-volume-1-chapter-2.html";
    private static final String MAGIC_1 = "https://kaoritranslation.blogspot.com/2025/04/"
            + "ln-magical-explorer-volume-1-chapter-1.html";
    private static final String MAGIC_2 = "https://kaoritranslation.blogspot.com/2025/04/"
            + "ln-magical-explorer-volume-1-chapter-2.html";

    private final List<String> openedNovels = new ArrayList<>();

    @Before
    public void setUp() {
        Session.clear();
        ContentCache.clear();
        Session.setCatalog(monogatariCatalog());
        Session.setCatalog(magicalCatalog());
    }

    @After
    public void tearDown() {
        Session.clear();
        ContentCache.clear();
    }

    private static Catalog monogatariCatalog() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("Monogatari no Kuromaku Volume 1 Chapter 1", "Chapter 1",
                        MONO_1, "2025-09-10", 11, 1),
                new Chapter("Monogatari no Kuromaku Volume 1 Chapter 2", "Chapter 2",
                        MONO_2, "2025-09-11", 12, 1)));
        return new Catalog(Novels.MONOGATARI,
                new ArrayList<>(Arrays.asList(new Volume(1, chapters))))
                .withCovers(Novels.MONOGATARI);
    }

    private static Catalog magicalCatalog() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("MagicalExplorer Volume 1 Chapter 1", "Chapter 1",
                        MAGIC_1, "", 101, 1),
                new Chapter("MagicalExplorer Volume 1 Chapter 2", "Chapter 2",
                        MAGIC_2, "", 102, 1)));
        return new Catalog(Novels.MAGICAL,
                new ArrayList<>(Arrays.asList(new Volume(1, chapters))))
                .withCovers(Novels.MAGICAL);
    }

    private Activity activity() {
        return Robolectric.buildActivity(Activity.class).setup().get();
    }

    private LinearLayout buildList(Activity activity, Prefs prefs) {
        NovelListPage page = new NovelListPage(activity, prefs, Palette.of(prefs.theme()),
                Novels.all(), novel -> openedNovels.add(novel.id));
        LinearLayout content = page.build();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, width, content.getMeasuredHeight());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return content;
    }

    private View byTag(View root, String tag) {
        View found = root.findViewWithTag(tag);
        assertNotNull("view dengan tag " + tag + " harus ada", found);
        return found;
    }

    /** Both novels are registered, with the source's real titles. */
    @Test
    public void bothNovelsExistWithTheirOwnTitles() {
        assertEquals("perpustakaan harus memuat dua novel", 2, Novels.all().size());

        Novel monogatari = Novels.byId(Novels.MONOGATARI);
        Novel magical = Novels.byId(Novels.MAGICAL);
        assertEquals("Monogatari no Kuromaku ni Tensei Shite", monogatari.title);
        assertEquals("Magical★Explorer", magical.title);

        // The long title the user asked for must be present, not shortened away.
        assertTrue("judul lengkap Magical★Explorer harus utuh: " + magical.fullTitle,
                magical.fullTitle.contains("Eroge no Yuujin Kyara ni Tensei Shitakedo"));
        assertTrue(magical.fullTitle.contains("Geemu Chishiki Tsukatte Jiyuu ni Ikiru"));
    }

    /** The library screen shows one card per novel, each with a cover box. */
    @Test
    public void libraryShowsACardPerNovel() {
        Activity activity = activity();
        LinearLayout content = buildList(activity, new Prefs(activity));

        assertNotNull(byTag(content, "novel_card:" + Novels.MONOGATARI));
        assertNotNull(byTag(content, "novel_card:" + Novels.MAGICAL));

        View monoCover = byTag(content, "novel_card_cover:" + Novels.MONOGATARI);
        View magicCover = byTag(content, "novel_card_cover:" + Novels.MAGICAL);
        assertTrue("cover kartu harus punya lebar", monoCover.getWidth() > 0);
        assertTrue("cover kartu harus punya tinggi", monoCover.getHeight() > 0);
        assertTrue("cover kartu kedua harus punya ukuran", magicCover.getHeight() > 0);

        // Cards must be different novels, not one repeated.
        TextView monoTitle = (TextView) byTag(content, "novel_card_title:" + Novels.MONOGATARI);
        TextView magicTitle = (TextView) byTag(content, "novel_card_title:" + Novels.MAGICAL);
        assertFalse(monoTitle.getText().toString().equals(magicTitle.getText().toString()));
    }

    /** Tapping a card reports that novel, so the right detail page opens. */
    @Test
    public void tappingACardOpensThatNovel() {
        Activity activity = activity();
        LinearLayout content = buildList(activity, new Prefs(activity));

        byTag(content, "novel_card:" + Novels.MAGICAL).performClick();
        assertEquals(1, openedNovels.size());
        assertEquals(Novels.MAGICAL, openedNovels.get(0));

        byTag(content, "novel_card:" + Novels.MONOGATARI).performClick();
        assertEquals(Novels.MONOGATARI, openedNovels.get(1));
    }

    /** Each novel's chapter count is shown from its own catalogue. */
    @Test
    public void cardSummaryComesFromItsOwnCatalogue() {
        Activity activity = activity();
        LinearLayout content = buildList(activity, new Prefs(activity));

        String magical = ((TextView) byTag(content,
                "novel_card_meta:" + Novels.MAGICAL)).getText().toString();
        assertTrue("ringkasan harus menyebut volume dan chapter: " + magical,
                magical.contains("1 volume") && magical.contains("2 chapter"));
    }

    /**
     * Reading progress is stored per novel: the same chapter index in two novels
     * must not share a bookmark.
     */
    @Test
    public void progressOfOneNovelIsNotVisibleInTheOther() {
        Prefs prefs = new Prefs(activity());

        Prefs magical = prefs.scoped(Novels.MAGICAL);
        magical.setLastUrl(MAGIC_2);
        magical.setProgress(MAGIC_2, 63);
        magical.setScroll(MAGIC_2, 77, 12);

        Prefs monogatari = prefs.scoped(Novels.MONOGATARI);
        assertTrue("bookmark novel lain tidak boleh bocor",
                monogatari.lastUrl() == null || monogatari.lastUrl().isEmpty());
        assertEquals(0, monogatari.progress(MAGIC_2));
        assertFalse("posisi gulir novel lain tidak boleh terbaca",
                monogatari.hasScroll(MAGIC_2));

        // And the magical bookmark is intact.
        assertEquals(MAGIC_2, magical.lastUrl());
        assertEquals(63, magical.progress(MAGIC_2));
        assertEquals(77, magical.scrollPosition(MAGIC_2));
    }

    /** Volume accordion state is also per novel. */
    @Test
    public void expandedVolumesAreNotSharedBetweenNovels() {
        Prefs prefs = new Prefs(activity());
        prefs.scoped(Novels.MAGICAL).toggleExpanded("1");

        assertTrue(prefs.scoped(Novels.MAGICAL).isExpanded("1"));
        assertFalse("accordion Monogatari tidak boleh ikut terbuka",
                prefs.scoped(Novels.MONOGATARI).isExpanded("1"));
    }

    /** Theme and font stay app-wide: they are not per novel. */
    @Test
    public void themeAndFontRemainShared() {
        Prefs prefs = new Prefs(activity());
        prefs.scoped(Novels.MAGICAL).setFontSize(24);
        prefs.scoped(Novels.MAGICAL).setTheme(Prefs.THEME_DARK);

        assertEquals(24, prefs.scoped(Novels.MONOGATARI).fontSize());
        assertEquals(Prefs.THEME_DARK, prefs.scoped(Novels.MONOGATARI).theme());
    }

    /** Session keeps the two catalogues apart. */
    @Test
    public void sessionKeepsCataloguesSeparate() {
        Catalog monogatari = Session.catalog(Novels.MONOGATARI);
        Catalog magical = Session.catalog(Novels.MAGICAL);
        assertNotNull(monogatari);
        assertNotNull(magical);
        assertNull("novel yang tidak dikenal tidak punya katalog",
                Session.catalog("novel-lain"));

        assertEquals(Novels.MONOGATARI, monogatari.novelId);
        assertEquals(Novels.MAGICAL, magical.novelId);
        assertEquals(MONO_1, monogatari.chapterAt(0).url);
        assertEquals(MAGIC_1, magical.chapterAt(0).url);
    }

    /**
     * The reader for one novel must refuse to navigate with the other novel's
     * catalogue: the next button stays disabled at the end of the book.
     */
    @Test
    public void readerNavigationStaysInsideItsOwnNovel() {
        ContentCache.put(MAGIC_1, chapter("MagicalExplorer Volume 1 Chapter 1", MAGIC_1));
        ContentCache.put(MAGIC_2, chapter("MagicalExplorer Volume 1 Chapter 2", MAGIC_2));

        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, Novels.MAGICAL);
        intent.putExtra(ReaderActivity.EXTRA_URL, MAGIC_2);
        ReaderActivity reader = Robolectric.buildActivity(ReaderActivity.class, intent)
                .setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View next = reader.getWindow().getDecorView().findViewWithTag("next");
        assertNotNull("tombol berikutnya harus ada", next);
        assertFalse("chapter terakhir tidak boleh menyeberang ke novel lain",
                next.isEnabled());
    }

    /** The magic reader stores its bookmark under its own novel only. */
    @Test
    public void readerWritesBookmarkForItsOwnNovelOnly() {
        Activity host = activity();
        ContentCache.put(MAGIC_1, chapter("MagicalExplorer Volume 1 Chapter 1", MAGIC_1));

        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, Novels.MAGICAL);
        intent.putExtra(ReaderActivity.EXTRA_URL, MAGIC_1);
        ReaderActivity reader = Robolectric.buildActivity(ReaderActivity.class, intent)
                .setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Prefs shared = new Prefs(host.getApplicationContext());
        assertEquals("bookmark harus tercatat untuk novel ini",
                MAGIC_1, shared.scoped(Novels.MAGICAL).lastUrl());
        assertFalse("bookmark Monogatari tidak boleh ikut tertulis",
                MAGIC_1.equals(shared.scoped(Novels.MONOGATARI).lastUrl()));
    }

    private static ChapterContent chapter(String title, String url) {
        List<id.shuyasal.kuromaku.data.Block> blocks = new ArrayList<>();
        blocks.add(id.shuyasal.kuromaku.data.Block.heading(title, 2));
        for (int i = 1; i <= 40; i++) {
            blocks.add(id.shuyasal.kuromaku.data.Block.paragraph("Paragraf ke-" + i));
        }
        return new ChapterContent(title, url, blocks);
    }
}
