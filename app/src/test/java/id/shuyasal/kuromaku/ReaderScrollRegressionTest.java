package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ScrollMath;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * BUG 2 regression, second round.
 *
 * <p>The first fix only added bottom padding, which is why the bug survived: the
 * real cause was the full-size error {@link ScrollView} sitting on top of the
 * {@link RecyclerView} inside the reading FrameLayout and swallowing every drag.
 * These tests therefore assert the view stack, not just the padding.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class ReaderScrollRegressionTest {

    private static final String URL = "https://kaoritranslation.blogspot.com/2026/03/long.html";
    private static final String NEXT_URL = "https://kaoritranslation.blogspot.com/2026/03/next.html";
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2160;

    private ReaderActivity activity;
    private Prefs prefs;

    /** 300 paragraphs plus illustrations, the length the user reported failing. */
    private static ChapterContent longChapter() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.heading("Chapter 5", 2));
        for (int i = 1; i <= 300; i++) {
            blocks.add(Block.paragraph("Paragraf ke-" + i
                    + ". Isi yang cukup panjang supaya tinggi baris realistis dan chapter"
                    + " benar-benar harus digulir sampai bawah."));
            if (i % 100 == 0) {
                blocks.add(Block.image("https://blogger.googleusercontent.com/img/x" + i + ".jpg",
                        "ilustrasi " + i));
            }
        }
        blocks.add(Block.paragraph("PARAGRAF TERAKHIR."));
        return new ChapterContent("Volume 7 Chapter 5", URL, blocks);
    }

    private static Catalog catalog() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("Volume 7 Chapter 5", "Chapter 5", URL, "2026-03-25", 105, 7),
                new Chapter("Volume 7 Chapter 6", "Chapter 6", NEXT_URL, "2026-03-25", 106, 7)));
        return new Catalog(new ArrayList<>(Arrays.asList(new Volume(7, chapters))));
    }

    @Before
    public void setUp() {
        ContentCache.put(URL, longChapter());
        ContentCache.put(NEXT_URL, longChapter());
        Session.setCatalog(catalog());
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_URL, URL);
        activity = Robolectric.buildActivity(ReaderActivity.class, intent).setup().get();
        prefs = new Prefs(activity.getApplicationContext());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout();
    }

    private void layout() {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private RecyclerView list() {
        RecyclerView found = find(activity.getWindow().getDecorView(), RecyclerView.class);
        assertNotNull(found);
        return found;
    }

    private LinearLayoutManager manager() {
        return (LinearLayoutManager) list().getLayoutManager();
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

    /** The FrameLayout that holds the reading list and the error pane. */
    private ViewGroup readingHost() {
        RecyclerView list = list();
        return (ViewGroup) list.getParent();
    }

    /**
     * Root cause assertion: nothing may sit on top of the list. A visible
     * sibling with a higher z-order is exactly what blocked scrolling before.
     */
    @Test
    public void nothingOverlaysTheReadingList() {
        ViewGroup host = readingHost();
        int listIndex = host.indexOfChild(list());

        for (int i = listIndex + 1; i < host.getChildCount(); i++) {
            View sibling = host.getChildAt(i);
            assertFalse("view di atas RecyclerView tidak boleh terlihat: "
                            + sibling.getClass().getSimpleName(),
                    sibling.getVisibility() == View.VISIBLE);
        }
    }

    /** The error pane exists but must start hidden, otherwise it steals touches. */
    @Test
    public void errorScrollStartsHidden() {
        ScrollView errorScroll = find(activity.getWindow().getDecorView(), ScrollView.class);
        assertNotNull("panel error tetap harus ada", errorScroll);
        assertTrue("panel error harus GONE saat chapter berhasil dimuat",
                errorScroll.getVisibility() == View.GONE);
    }

    /**
     * The reading area must actually scroll, and the error pane must not be the
     * view that receives the gesture.
     *
     * <p>Robolectric does not run the frame loop RecyclerView needs to turn a
     * synthetic drag into a fling, so the scroll itself is driven directly; the
     * touch routing is verified through the view stack instead, which is exactly
     * where the bug lived (a full-size ScrollView on top of the list).
     */
    @Test
    public void readingAreaScrollsAndErrorPaneDoesNotBlockIt() {
        RecyclerView list = list();
        assertTrue("list harus punya isi untuk digulir", list.canScrollVertically(1));

        ViewGroup host = readingHost();
        int listIndex = host.indexOfChild(list);
        for (int i = listIndex + 1; i < host.getChildCount(); i++) {
            View sibling = host.getChildAt(i);
            if (sibling.getVisibility() != View.VISIBLE) continue;
            assertTrue("view di atas list harus meneruskan sentuhan ke list",
                    sibling instanceof ScrollView == false);
        }

        // The coordinates used for a drag fall inside the list, not the bars.
        int[] pos = new int[2];
        list.getLocationInWindow(pos);
        int dragY = pos[1] + list.getHeight() / 2;
        assertTrue("titik gulir harus berada di dalam area baca",
                dragY > pos[1] && dragY < pos[1] + list.getHeight());

        list.scrollBy(0, Ui.dp(activity, 600));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout();

        assertTrue("area baca harus benar-benar tergulir, posisi pertama masih "
                        + manager().findFirstVisibleItemPosition(),
                manager().findFirstVisibleItemPosition() > 0);
    }

    /** The 300th paragraph must be reachable, not clipped by the reader height. */
    @Test
    public void canReachTheThreeHundredthParagraph() {
        RecyclerView list = list();
        int lastTextIndex = list.getAdapter().getItemCount() - 2; // footer is last
        manager().scrollToPositionWithOffset(lastTextIndex, 0);
        layout();

        View lastText = manager().findViewByPosition(lastTextIndex);
        assertNotNull("paragraf terakhir harus terpasang", lastText);
        assertTrue("paragraf terakhir harus berada di dalam area baca",
                lastText.getTop() < list.getHeight());
    }

    /** The final paragraph must sit above the navigation panel, fully visible. */
    @Test
    public void lastParagraphClearsTheNavigationPanel() {
        RecyclerView list = list();
        int lastIndex = list.getAdapter().getItemCount() - 1;
        manager().scrollToPositionWithOffset(lastIndex, 0);
        layout();

        View footer = manager().findViewByPosition(lastIndex);
        assertNotNull(footer);
        assertTrue("footer butuh ruang agar paragraf terakhir tidak tertutup",
                footer.getPaddingBottom() > 0);

        View bottomBar = activity.getWindow().getDecorView().findViewWithTag("inner");
        assertNotNull(bottomBar);
        View barRoot = (View) bottomBar.getParent();

        int[] listPos = new int[2];
        int[] barPos = new int[2];
        list.getLocationInWindow(listPos);
        barRoot.getLocationInWindow(barPos);

        assertTrue("panel navigasi harus mulai di bawah area baca",
                barPos[1] >= listPos[1] + list.getHeight() - 1);
    }

    /**
     * Reading progress must survive the layout pass that follows a restore.
     *
     * <p>The exact block must be kept untouched, and the percentage must never be
     * clobbered to 0 by the adapter being filled. The recorded percentage may
     * settle on the true value for the restored position (that is not a bogus
     * write), but it must stay in the neighbourhood of the saved bookmark.
     */
    @Test
    public void restoringBookmarkIsNotOverwrittenByTheLayoutPass() {
        prefs.setScroll(URL, 120, 40);
        prefs.setProgress(URL, 44);

        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_URL, URL);
        ReaderActivity reopened = Robolectric.buildActivity(ReaderActivity.class, intent)
                .setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View decor = reopened.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("posisi blok tersimpan tidak boleh tertimpa layout pass",
                120, prefs.scrollPosition(URL));

        int progress = prefs.progress(URL);
        assertTrue("progres tidak boleh direset ke 0 oleh layout pass, dapat " + progress,
                progress > 0);
        assertTrue("progres tidak boleh meloncat jauh dari bookmark, dapat " + progress,
                Math.abs(progress - 44) <= 10);
    }

    /** Percent maths still reports 100 only at the very end. */
    @Test
    public void percentOnlyReachesOneHundredAtTheEnd() {
        int count = list().getAdapter().getItemCount();
        assertTrue(ScrollMath.percent(0, count) < 20);
        assertEquals(100, ScrollMath.percent(count - 1, count));
    }

    /** Previous / next must keep working after the layout change. */
    @Test
    public void navigationButtonsStillWork() {
        View next = activity.getWindow().getDecorView().findViewWithTag("next");
        assertNotNull(next);
        next.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(NEXT_URL, activity.getIntent().getStringExtra(ReaderActivity.EXTRA_URL));
    }
}
