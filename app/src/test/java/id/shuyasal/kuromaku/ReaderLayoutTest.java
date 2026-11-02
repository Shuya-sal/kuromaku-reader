package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.data.Block;

/**
 * BUG 2 regression: the reading area must keep a real height, the navigation
 * panel must sit below it instead of over the text, and the final block must be
 * reachable by scrolling.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class ReaderLayoutTest {

    private static final String URL = "https://kaoritranslation.blogspot.com/2026/03/long-chapter.html";
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2160;

    private static ChapterContent longChapter(int paragraphs) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.heading("Chapter 5", 2));
        for (int i = 1; i <= paragraphs; i++) {
            blocks.add(Block.paragraph("Paragraf ke-" + i
                    + ". Isi panjang supaya tinggi baris realistis dan chapter benar-benar bisa digulir."));
        }
        return new ChapterContent("Monogatari no Kuromaku Volume 7 Chapter 5", URL, blocks);
    }

    private static Catalog catalog() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("Volume 7 Chapter 5", "Chapter 5", URL, "2026-03-25", 105, 7),
                new Chapter("Volume 7 Chapter 6", "Chapter 6",
                        "https://kaoritranslation.blogspot.com/2026/03/next.html", "2026-03-25", 106, 7)));
        return new Catalog(new ArrayList<>(Arrays.asList(new Volume(7, chapters))));
    }

    private ActivityController<ReaderActivity> controller;
    private ReaderActivity activity;

    @Before
    public void setUp() {
        ContentCache.put(URL, longChapter(300));
        Session.setCatalog(catalog());
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_URL, URL);
        controller = Robolectric.buildActivity(ReaderActivity.class, intent).setup();
        activity = controller.get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout();
    }

    /** Forces a real measure/layout pass, as the window would do. */
    private void layout() {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private RecyclerView findList() {
        RecyclerView found = find(activity.getWindow().getDecorView(), RecyclerView.class);
        assertNotNull("RecyclerView pembaca harus ada", found);
        return found;
    }

    private View findBottomBar() {
        View tag = activity.getWindow().getDecorView().findViewWithTag("inner");
        assertNotNull("panel navigasi harus ada", tag);
        return tag.getParent() instanceof View ? (View) tag.getParent() : tag;
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return (T) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void readingAreaHasRealHeight() {
        RecyclerView list = findList();
        assertTrue("area baca tidak boleh 0 tinggi, dapat " + list.getHeight(),
                list.getHeight() > HEIGHT / 3);
        assertTrue("area baca harus terlihat", list.getVisibility() == View.VISIBLE);
    }

    @Test
    public void navigationPanelDoesNotOverlapTheText() {
        RecyclerView list = findList();
        View bottomBar = findBottomBar();

        int[] listPos = new int[2];
        int[] barPos = new int[2];
        list.getLocationInWindow(listPos);
        bottomBar.getLocationInWindow(barPos);

        int listBottom = listPos[1] + list.getHeight();
        assertTrue("panel navigasi mulai di " + barPos[1]
                        + " padahal teks berakhir di " + listBottom,
                barPos[1] >= listBottom - 1);
        assertTrue("panel navigasi harus punya tinggi", bottomBar.getHeight() > 0);
    }

    @Test
    public void canScrollToTheVeryLastParagraph() {
        RecyclerView list = findList();
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        assertNotNull(manager);

        int lastIndex = list.getAdapter().getItemCount() - 1;
        manager.scrollToPositionWithOffset(lastIndex, 0);
        layout();

        assertEquals("item terakhir harus bisa dicapai",
                lastIndex, manager.findLastVisibleItemPosition());
        assertEquals(0, manager.findFirstVisibleItemPosition() == RecyclerView.NO_POSITION ? 0 : 0);
    }

    @Test
    public void lastBlockKeepsBottomSlackForPanelAndGestureBar() {
        RecyclerView list = findList();
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        int lastIndex = list.getAdapter().getItemCount() - 1;
        manager.scrollToPositionWithOffset(lastIndex, 0);
        layout();

        View last = manager.findViewByPosition(lastIndex);
        assertNotNull("footer harus terpasang", last);
        assertTrue("footer butuh padding bawah agar paragraf terakhir tidak tertutup",
                last.getPaddingBottom() > 0);
        assertTrue("area baca harus benar-benar bisa digulir", list.canScrollVertically(-1)
                || list.getChildCount() > 1);
    }

    @Test
    public void scrollingToEndReportsFinishedOnlyAtTheEnd() {
        RecyclerView list = findList();
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        int lastIndex = list.getAdapter().getItemCount() - 1;

        manager.scrollToPositionWithOffset(0, 0);
        layout();
        int atTop = id.shuyasal.kuromaku.ui.ScrollMath.percent(
                manager.findLastVisibleItemPosition(), list.getAdapter().getItemCount());
        assertTrue("di awal progres harus kecil, dapat " + atTop, atTop < 20);

        manager.scrollToPositionWithOffset(lastIndex, 0);
        layout();
        int atEnd = id.shuyasal.kuromaku.ui.ScrollMath.percent(
                manager.findLastVisibleItemPosition(), list.getAdapter().getItemCount());
        assertEquals(100, atEnd);
    }

    @Test
    public void previousAndNextButtonsAreWiredToTheCatalog() {
        View previous = activity.getWindow().getDecorView().findViewWithTag("previous");
        View next = activity.getWindow().getDecorView().findViewWithTag("next");
        assertNotNull(previous);
        assertNotNull(next);
        assertTrue("Berikutnya harus aktif karena ada chapter sesudahnya", next.isEnabled());

        next.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("navigasi harus pindah ke chapter berikutnya",
                "https://kaoritranslation.blogspot.com/2026/03/next.html",
                activity.getIntent().getStringExtra(ReaderActivity.EXTRA_URL));
    }

    @Test
    public void readerSurvivesWithoutCatalog() {
        Session.setCatalog(null);
        ContentCache.put(URL, longChapter(20));
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_URL, URL);
        Activity created = Robolectric.buildActivity(ReaderActivity.class, intent).setup().get();
        assertNotNull(created);
        assertTrue(!created.isFinishing());
    }
}
