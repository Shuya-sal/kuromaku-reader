package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.ui.ImageCell;
import id.shuyasal.kuromaku.ui.ImageLoadLog;
import id.shuyasal.kuromaku.ui.ZoomableImageView;

/**
 * The image system, exercised through real views.
 *
 * <p>Root cause being guarded against: the old reader built a {@code wrap_content}
 * {@link ImageView} with no content and no reserved box, so the cell measured to
 * zero width and every illustration stayed invisible even when the download
 * succeeded. These tests assert the measured box, not just that a block exists.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class ReaderImageTest {

    private static final String URL = "https://kaoritranslation.blogspot.com/2025/01/ilu.html";
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2160;

    private static final String IMG_A =
            "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsA/s1600/m3.jpg";
    private static final String IMG_B =
            "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsB/s1600/m13.jpg";

    private ReaderActivity activity;

    private static ChapterContent illustrationPost() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.heading("Illustrasi", 2));
        // 1080x1534, the real ratio of the volume 1 plate.
        blocks.add(Block.image(IMG_A, "ilustrasi 1", 1080, 1534));
        blocks.add(Block.paragraph("Keterangan ilustrasi pertama."));
        // Landscape plate, 1523x1080.
        blocks.add(Block.image(IMG_B, "ilustrasi 2", 1523, 1080));
        blocks.add(Block.paragraph("Keterangan ilustrasi kedua."));
        return new ChapterContent("Volume 1 Illustrasi", URL, blocks);
    }

    @Before
    public void setUp() {
        ContentCache.clear();
        ContentCache.put(URL, illustrationPost());
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                new Chapter("Volume 1 Illustrasi", "Illustrasi", URL, "2025-01-05", 19, 1)));
        Session.setCatalog(new Catalog(id.shuyasal.kuromaku.data.Novels.MONOGATARI,
                new ArrayList<>(Arrays.asList(new Volume(1, chapters)))));

        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, id.shuyasal.kuromaku.data.Novels.MONOGATARI);
        intent.putExtra(ReaderActivity.EXTRA_URL, URL);
        activity = Robolectric.buildActivity(ReaderActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout();
        ImageLoadLog.clear();
    }

    private void layout() {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> List<T> findAll(View root, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(root)) out.add((T) root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                out.addAll(findAll(group.getChildAt(i), type));
            }
        }
        return out;
    }

    private RecyclerView list() {
        List<RecyclerView> found = findAll(activity.getWindow().getDecorView(), RecyclerView.class);
        assertFalse("RecyclerView harus ada", found.isEmpty());
        return found.get(0);
    }

    /** Root cause assertion: an image cell must occupy a real box. */
    @Test
    public void imageCellsOccupyARealBox() {
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertFalse("sel gambar harus terpasang", cells.isEmpty());

        for (ImageCell cell : cells) {
            assertTrue("sel gambar harus punya lebar nyata, dapat " + cell.getWidth(),
                    cell.getWidth() > 0);
            assertTrue("sel gambar harus punya tinggi nyata, dapat " + cell.getHeight(),
                    cell.getHeight() > 0);
        }
    }

    /** The reserved box keeps the picture's own ratio, so nothing is squashed. */
    @Test
    public void imageBoxKeepsSourceRatio() {
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertTrue("butuh dua sel gambar", cells.size() >= 2);

        float first = cells.get(0).getWidth() / (float) cells.get(0).getHeight();
        assertEquals("rasio potret 1080x1534 harus dipertahankan", 1080f / 1534f, first, 0.02f);

        float second = cells.get(1).getWidth() / (float) cells.get(1).getHeight();
        assertEquals("rasio lanskap 1523x1080 harus dipertahankan", 1523f / 1080f, second, 0.02f);
    }

    /** Width is responsive: the cell fills the reading column minus its margins. */
    @Test
    public void imageWidthIsResponsive() {
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        RecyclerView list = list();
        int available = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        assertTrue("daftar harus punya lebar", available > 0);

        for (ImageCell cell : cells) {
            int[] cellPos = new int[2];
            cell.getLocationInWindow(cellPos);
            assertTrue("gambar harus selebar kolom baca, dapat " + cell.getWidth()
                    + " dari " + available, cell.getWidth() > available / 2);
        }
    }

    /** An image without declared size still gets a usable, non-zero box. */
    @Test
    public void imageWithoutSizeStillGetsABox() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.image(IMG_A, "tanpa ukuran"));
        blocks.add(Block.paragraph("teks"));
        ContentCache.put(URL, new ChapterContent("Volume 1 Illustrasi", URL, blocks));

        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, id.shuyasal.kuromaku.data.Novels.MONOGATARI);
        intent.putExtra(ReaderActivity.EXTRA_URL, URL);
        ReaderActivity fresh = Robolectric.buildActivity(ReaderActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View decor = fresh.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        List<ImageCell> cells = findAll(decor, ImageCell.class);
        assertFalse("sel gambar tetap harus ada", cells.isEmpty());
        assertTrue("lebar tidak boleh nol", cells.get(0).getWidth() > 0);
        assertTrue("tinggi tidak boleh nol", cells.get(0).getHeight() > 0);
    }

    /** Tapping an illustration opens the zoomable full-screen viewer. */
    @Test
    public void tappingAnImageOpensTheZoomViewer() {
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertFalse(cells.isEmpty());
        cells.get(0).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("harus membuka penampil gambar", next);
        assertEquals(ImageViewerActivity.class.getName(), next.getComponent().getClassName());
        assertEquals(IMG_A, next.getStringExtra(ImageViewerActivity.EXTRA_URL));
    }

    /** The viewer shows the picture and supports pinch/double-tap zoom. */
    @Test
    public void viewerSupportsZoom() {
        Intent intent = new Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, IMG_A);
        intent.putExtra(ImageViewerActivity.EXTRA_CAPTION, "Volume 1 Illustrasi");
        ImageViewerActivity viewer = Robolectric.buildActivity(
                ImageViewerActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View decor = viewer.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);

        List<ZoomableImageView> views = findAll(decor, ZoomableImageView.class);
        assertFalse("penampil harus punya ZoomableImageView", views.isEmpty());
        ZoomableImageView image = views.get(0);
        assertTrue("lebar penampil harus nyata", image.getWidth() > 0);

        assertEquals("zoom awal 1x", 1f, image.zoom(), 0.001f);
        image.applyScale(2.5f, image.getWidth() / 2f, image.getHeight() / 2f);
        assertEquals("zoom setelah cubit", 2.5f, image.zoom(), 0.001f);
        image.applyScale(99f, 0, 0);
        assertEquals("zoom dibatasi 4x", 4f, image.zoom(), 0.001f);
        image.applyScale(0.1f, 0, 0);
        assertEquals("zoom tidak bisa di bawah 1x", 1f, image.zoom(), 0.001f);
    }

    /** A failing image must say so and offer a retry, never fail silently. */
    @Test
    public void failedImageShowsErrorAndRetry() throws Exception {
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertFalse(cells.isEmpty());
        ImageCell cell = cells.get(0);

        // Drive the failure path the way a dead URL would.
        java.lang.reflect.Method fail = ImageCell.class
                .getDeclaredMethod("fail", String.class, String.class);
        fail.setAccessible(true);
        fail.invoke(cell, "unduh", "HTTP 404");
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue("sel harus menandai kegagalan", cell.hasFailed());
        assertEquals("unduh", cell.stage());

        View error = cell.findViewWithTag("image_error");
        assertNotNull("kotak error harus ada", error);
        assertEquals("kotak error harus terlihat", View.VISIBLE, error.getVisibility());

        View retry = cell.findViewWithTag("image_retry");
        assertNotNull("tombol coba lagi harus ada", retry);
        assertEquals("tombol coba lagi harus terlihat", View.VISIBLE, retry.getVisibility());

        TextView detail = cell.findViewWithTag("image_error_detail");
        assertNotNull(detail);
        assertTrue("pesan harus menyebut tahap gagal: " + detail.getText(),
                detail.getText().toString().contains("unduh"));

        // The box keeps its height, so a failure does not leave a collapsed gap.
        assertTrue("tinggi tetap dipertahankan saat gagal", cell.getHeight() > 0);
    }

    /**
     * Retry re-attempts the load instead of leaving the error frozen.
     *
     * <p>The failure is forced first, exactly as {@code failedImageShowsErrorAndRetry}
     * does. Without that this test used to click retry on a cell that had never
     * failed, so the only thing it could observe was whichever async Glide callback
     * happened to land first — green on one machine, red on another. The assertions
     * then run before the looper is idled again, because {@code load()} sets both
     * flags synchronously and a later failure callback must not decide the outcome.
     */
    @Test
    public void retryButtonRestartsTheLoad() throws Exception {
        List<ImageCell> cells = findAll(activity.getWindow().getDecorView(), ImageCell.class);
        assertFalse("butuh sel gambar", cells.isEmpty());
        ImageCell cell = cells.get(0);

        java.lang.reflect.Method fail = ImageCell.class
                .getDeclaredMethod("fail", String.class, String.class);
        fail.setAccessible(true);
        fail.invoke(cell, "unduh", "HTTP 404");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("prasyarat: sel harus dalam keadaan gagal", cell.hasFailed());

        View retry = cell.findViewWithTag("image_retry");
        assertNotNull("tombol coba lagi harus ada setelah gagal", retry);
        assertEquals("tombol harus terlihat", View.VISIBLE, retry.getVisibility());

        ImageLoadLog.clear();
        retry.performClick();

        assertFalse("setelah coba lagi, status gagal harus direset", cell.hasFailed());
        View error = cell.findViewWithTag("image_error");
        assertEquals("kotak error harus disembunyikan saat mencoba lagi",
                View.GONE, error.getVisibility());
        List<String> recent = ImageLoadLog.recent();
        assertFalse("percobaan ulang harus tercatat", recent.isEmpty());
        String last = recent.get(recent.size() - 1);
        assertTrue("percobaan ulang harus masuk antrean lagi, dapat: " + last,
                last.contains("stage=antrean"));
    }

    /** Logging names the failing stage and never leaks the full URL token. */
    @Test
    public void loggingReportsStageWithoutLeakingTokens() {
        ImageLoadLog.clear();
        ImageLoadLog.fail("unduh", IMG_A, "HTTP 503");

        List<String> recent = ImageLoadLog.recent();
        assertFalse("harus ada catatan", recent.isEmpty());
        String line = recent.get(recent.size() - 1);
        assertTrue("catatan harus menyebut tahap: " + line, line.contains("stage=unduh"));
        assertFalse("token panjang tidak boleh bocor: " + line, line.contains("AVvXsA"));
        assertTrue("nama berkas boleh tampil: " + line, line.contains("m3.jpg"));
    }

    /** Illustrations keep their place between paragraphs inside the reader. */
    @Test
    public void illustrationsStayBetweenParagraphs() {
        RecyclerView.Adapter<?> adapter = list().getAdapter();
        assertNotNull(adapter);

        boolean sawTextThenImage = false;
        int lastWasImage = -1;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            int type = adapter.getItemViewType(i);
            if (type == 1 /* text */ && lastWasImage == -1) lastWasImage = 0;
            if (type == 2 /* image */) {
                if (lastWasImage == 0) sawTextThenImage = true;
                lastWasImage = 2;
            }
        }
        assertTrue("harus ada gambar setelah teks", sawTextThenImage);
    }

    /** Scrolling and navigation must keep working with images in the list. */
    @Test
    public void scrollingAndNavigationStillWork() {
        RecyclerView list = list();
        assertTrue("daftar harus bisa digulir", list.canScrollVertically(1));
        list.scrollBy(0, 800);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout();
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        assertTrue("posisi setelah gulir", manager.findFirstVisibleItemPosition() >= 0);
    }
}
