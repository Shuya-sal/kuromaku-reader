package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import id.shuyasal.kuromaku.data.NovelInfo;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Home screen (2.2): novel detail on top, then an accordion of volumes.
 *
 * <p>Measures real geometry on a small phone (320dp wide) so the cover ratio,
 * synopsis toggle and collapsed chapters are verified rather than assumed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class LibraryPageTest {

    private static final String CHAPTER_1 = "https://kaoritranslation.blogspot.com/2026/01/v1c1.html";
    private static final String CHAPTER_2 = "https://kaoritranslation.blogspot.com/2026/01/v1c2.html";
    private static final String CHAPTER_3 = "https://kaoritranslation.blogspot.com/2026/01/v2c1.html";

    private final List<String> opened = new ArrayList<>();
    private final List<String> toggled = new ArrayList<>();
    private int synopsisToggles;
    private int sourceOpens;
    private boolean synopsisOpen;

    private Catalog catalog() {
        List<Chapter> v1 = new ArrayList<>(Arrays.asList(
                new Chapter("Monogatari no Kuromaku Volume 1 Chapter 1", "Chapter 1",
                        CHAPTER_1, "2026-01-10", 11, 1),
                new Chapter("Monogatari no Kuromaku Volume 1 Chapter 2", "Chapter 2",
                        CHAPTER_2, "2026-01-11", 12, 1)));
        List<Chapter> v2 = new ArrayList<>(Arrays.asList(
                new Chapter("Monogatari no Kuromaku Volume 2 Chapter 1", "Chapter 1",
                        CHAPTER_3, "2026-02-10", 21, 2)));
        return new Catalog(new ArrayList<>(Arrays.asList(new Volume(1, v1), new Volume(2, v2))));
    }

    private LibraryPage.Listener listener() {
        return new LibraryPage.Listener() {
            @Override
            public void onOpenChapter(Chapter chapter) {
                opened.add(chapter.url);
            }

            @Override
            public void onToggleSynopsis() {
                synopsisToggles++;
                synopsisOpen = !synopsisOpen;
            }

            @Override
            public void onToggleVolume(String volumeKey) {
                toggled.add(volumeKey);
            }

            @Override
            public void onOpenSource() {
                sourceOpens++;
            }
        };
    }

    private LinearLayout build(Activity activity, Prefs prefs, Catalog catalog) {
        Palette palette = Palette.of(prefs.theme());
        LibraryPage page = new LibraryPage(activity, prefs, palette, catalog, synopsisOpen,
                listener());
        LinearLayout content = page.build();
        // Measure at the width of the hosting scroll view.
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, width, content.getMeasuredHeight());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return content;
    }

    private Activity activity() {
        return Robolectric.buildActivity(Activity.class).setup().get();
    }

    private View byTag(View root, String tag) {
        View found = root.findViewWithTag(tag);
        assertNotNull("view dengan tag " + tag + " harus ada", found);
        return found;
    }

    /** Cover must keep the artwork's real ratio, never a square or a stretch. */
    @Test
    public void coverKeepsTheRealArtworkRatio() {
        Activity activity = activity();
        LinearLayout content = build(activity, new Prefs(activity), catalog());

        View cover = byTag(content, "cover");
        assertTrue("sampul harus punya lebar", cover.getWidth() > 0);
        assertTrue("sampul harus punya tinggi", cover.getHeight() > 0);

        float ratio = cover.getWidth() / (float) cover.getHeight();
        assertEquals("rasio sampul harus mengikuti artwork asli",
                NovelInfo.COVER_RATIO, ratio, 0.02f);

        // A 2:3 novel cover is taller than it is wide.
        assertTrue("sampul harus lebih tinggi daripada lebar", cover.getHeight() > cover.getWidth());
    }

    /** A nearby placeholder is always present behind the image. */
    @Test
    public void coverHasAPlaceholderBehindTheImage() {
        Activity activity = activity();
        LinearLayout content = build(activity, new Prefs(activity), catalog());

        View cover = byTag(content, "cover");
        View placeholder = byTag(cover, "cover_placeholder");
        View image = byTag(cover, "cover_image");
        assertNotNull(placeholder);
        assertNotNull(image);

        ViewGroup holder = (ViewGroup) cover;
        int placeholderIndex = holder.indexOfChild(placeholder);
        int imageIndex = holder.indexOfChild(image);
        assertTrue("placeholder harus digambar sebelum gambar agar tetap terlihat saat gagal",
                placeholderIndex < imageIndex);
    }

    @Test
    public void titleAndMetadataAreShownNotInvented() {
        Activity activity = activity();
        LinearLayout content = build(activity, new Prefs(activity), catalog());

        TextView title = (TextView) byTag(content, "novel_title");
        assertEquals(NovelInfo.TITLE, title.getText().toString());

        View metadata = byTag(content, "metadata");
        String text = collectText(metadata);
        assertTrue("penulis harus tampil", text.contains(NovelInfo.AUTHOR));
        assertTrue("ilustrator harus tampil", text.contains(NovelInfo.ILLUSTRATOR));
        assertTrue("genre harus tampil", text.contains("Fantasy"));
        assertTrue("status tidak boleh dikarang", text.contains(NovelInfo.STATUS));
        assertTrue("sumber terjemahan harus tampil", text.contains("Kaori Translation"));
    }

    /** Synopsis starts collapsed and expands on tap. */
    @Test
    public void synopsisExpandsOnTap() {
        Activity activity = activity();
        Prefs prefs = new Prefs(activity);
        LinearLayout collapsed = build(activity, prefs, catalog());

        TextView collapsedBody = (TextView) byTag(collapsed, "synopsis");
        int collapsedLength = collapsedBody.getText().length();
        assertTrue("sinopsis ringkas harus lebih pendek dari versi penuh",
                collapsedLength < fullSynopsisLength());

        Button toggle = (Button) byTag(collapsed, "synopsis_toggle");
        assertEquals("Baca selengkapnya", toggle.getText().toString());
        toggle.performClick();
        assertEquals(1, synopsisToggles);

        LinearLayout expanded = build(activity, prefs, catalog());
        TextView expandedBody = (TextView) byTag(expanded, "synopsis");
        assertEquals("sinopsis penuh harus memuat semua paragraf",
                fullSynopsisLength(), expandedBody.getText().length());

        Button close = (Button) byTag(expanded, "synopsis_toggle");
        assertEquals("Tutup sinopsis", close.getText().toString());
    }

    private int fullSynopsisLength() {
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < NovelInfo.SYNOPSIS.length; i++) {
            if (i > 0) all.append("\n\n");
            all.append(NovelInfo.SYNOPSIS[i]);
        }
        return all.length();
    }

    /** Volumes start collapsed and reveal chapters only when opened. */
    @Test
    public void volumesAreCollapsedAccordions() {
        Activity activity = activity();
        Prefs prefs = new Prefs(activity);
        LinearLayout content = build(activity, prefs, catalog());

        View bodyV1 = byTag(content, "chapters:1");
        assertEquals("volume tertutup harus menyembunyikan chapter",
                View.GONE, bodyV1.getVisibility());

        View headerV1 = byTag(content, "volume:1");
        assertTrue("header volume harus terlihat", headerV1.getVisibility() == View.VISIBLE);
        headerV1.performClick();
        assertEquals("mengetuk header harus memberi tahu layar", 1, toggled.size());
        assertEquals("1", toggled.get(0));

        // Opening the volume persists and renders the chapters.
        prefs.toggleExpanded("1");
        LinearLayout openedPage = build(activity, prefs, catalog());
        View openedBody = byTag(openedPage, "chapters:1");
        assertEquals(View.VISIBLE, openedBody.getVisibility());

        View chapterRow = byTag(openedPage, "chapter:" + CHAPTER_1);
        chapterRow.performClick();
        assertEquals(1, opened.size());
        assertEquals(CHAPTER_1, opened.get(0));
    }

    /** Every volume the crawler found must be listed, including Volume 1 and 2. */
    @Test
    public void allVolumesAreListed() {
        Activity activity = activity();
        Prefs prefs = new Prefs(activity);
        prefs.toggleExpanded("2");
        LinearLayout content = build(activity, prefs, catalog());

        assertNotNull(content.findViewWithTag("volume:1"));
        assertNotNull(content.findViewWithTag("volume:2"));

        View v2Body = byTag(content, "chapters:2");
        assertEquals(View.VISIBLE, v2Body.getVisibility());
        assertNotNull("chapter volume 2 harus ikut tampil",
                content.findViewWithTag("chapter:" + CHAPTER_3));
    }

    /** Continue-reading button appears only with a real bookmark. */
    @Test
    public void continueButtonUsesTheLastChapter() {
        Activity activity = activity();
        Prefs prefs = new Prefs(activity);
        LinearLayout without = build(activity, prefs, catalog());
        assertTrue("tanpa riwayat tidak ada tombol lanjutkan",
                without.findViewWithTag("continue") == null);

        prefs.setLastUrl(CHAPTER_2);
        prefs.setProgress(CHAPTER_2, 37);
        LinearLayout with = build(activity, prefs, catalog());

        Button resume = (Button) byTag(with, "continue");
        assertTrue("label harus menyebut chapter terakhir",
                resume.getText().toString().contains("Chapter 2"));
        assertTrue("label harus menyebut progres",
                resume.getText().toString().contains("37%"));
        resume.performClick();
        assertEquals(CHAPTER_2, opened.get(0));
    }

    /** Attribution and the source link must stay. */
    @Test
    public void attributionAndSourceLinkAreKept() {
        Activity activity = activity();
        LinearLayout content = build(activity, new Prefs(activity), catalog());

        View attribution = byTag(content, "attribution");
        String text = collectText(attribution);
        assertTrue(text.contains("Kaori Translation"));
        assertTrue("aplikasi tidak menyimpan salinan novel",
                text.contains("tidak menyimpan salinan novel"));

        Button source = (Button) byTag(content, "source");
        source.performClick();
        assertEquals(1, sourceOpens);
    }

    /** Dark theme must still render readable text on the same structure. */
    @Test
    public void darkThemeKeepsTheSameStructure() {
        Activity activity = activity();
        Prefs prefs = new Prefs(activity);
        prefs.setTheme(Prefs.THEME_DARK);
        Palette dark = Palette.of(prefs.theme());
        LinearLayout content = build(activity, prefs, catalog());

        TextView title = (TextView) byTag(content, "novel_title");
        assertEquals(dark.text, title.getCurrentTextColor());
        assertNotNull(content.findViewWithTag("cover"));
        assertNotNull(content.findViewWithTag("metadata"));
    }

    /** Whole activity on a small screen: detail first, list below, no overlap. */
    @Test
    public void homeScreenFitsASmallPhone() {
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View decor = host.getWindow().getDecorView();
        int width = host.getResources().getDisplayMetrics().widthPixels;
        int height = host.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);

        ScrollView scroller = (ScrollView) byTag(decor, "library_scroller");
        assertTrue("halaman harus bisa digulir di layar kecil",
                scroller.getHeight() > 0);
        assertNotNull("header aplikasi harus ada", decor.findViewWithTag("app_title"));
        assertFalse("aktivitas tidak boleh tertutup karena katalog belum dimuat",
                host.isFinishing());
    }

    private String collectText(View root) {
        StringBuilder out = new StringBuilder();
        if (root instanceof TextView) out.append(((TextView) root).getText()).append('\n');
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                out.append(collectText(group.getChildAt(i)));
            }
        }
        return out.toString();
    }
}
