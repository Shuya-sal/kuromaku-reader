package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiPageViewTest {
    @Test public void longBitmapHasFullColumnWidthAndProportionalHeight() {
        Context context = ApplicationProvider.getApplicationContext();
        ShinigamiPageView cell = new ShinigamiPageView(context);
        cell.showReady(Bitmap.createBitmap(80, 1500, Bitmap.Config.ARGB_8888));
        cell.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(320, cell.getMeasuredWidth());
        assertEquals(6000, cell.getMeasuredHeight());
        assertEquals(View.GONE, cell.findViewWithTag("shinigami_page_loading").getVisibility());
    }
    /**
     * Rebinding halaman yang sama tidak boleh mengosongkan gambar.
     *
     * <p>Inilah sumber kedipan saat menggulir: dulu setiap bind memanggil
     * showLoading() lalu showFile(), jadi permukaan gambar ditutup dan dibuka
     * ulang walau isinya sama.
     */
    @Test public void showingTheSameFileTwiceKeepsTheSurfaceVisible() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = new File(context.getCacheDir(), "page-rebind-test.png");
        Bitmap bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }

        ShinigamiPageView cell = new ShinigamiPageView(context);
        cell.showFile(file, ShinigamiQuality.AUTO);
        View surface = cell.findViewWithTag("shinigami_page_surface");
        assertEquals("permukaan gambar harus tampil", View.VISIBLE, surface.getVisibility());
        assertEquals(View.GONE,
                cell.findViewWithTag("shinigami_page_loading").getVisibility());

        cell.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int firstHeight = cell.getMeasuredHeight();
        double firstRatio = cell.ratio();

        // Rebind untuk halaman yang sama.
        cell.showFile(file, ShinigamiQuality.AUTO);

        assertEquals("permukaan tidak boleh ditutup saat rebind",
                View.VISIBLE, surface.getVisibility());
        assertEquals("loading tidak boleh muncul lagi",
                View.GONE, cell.findViewWithTag("shinigami_page_loading").getVisibility());
        assertEquals("rasio tidak berubah", firstRatio, cell.ratio(), 0.0001);

        cell.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals("tinggi tetap, tidak ada lompatan layout", firstHeight, cell.getMeasuredHeight());
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * Muat ulang tidak boleh menutupi gambar yang sudah tampil dengan spinner.
     *
     * <p>Inilah keluhan utama: gambar yang terlihat tiba-tiba menjadi area hitam
     * dengan indikator loading. Sekarang showLoading() pada halaman yang sudah
     * punya gambar valid tidak melakukan apa pun.
     */
    @Test public void loadingOverlayNeverReplacesAVisibleImage() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = new File(context.getCacheDir(), "page-loading-test.png");
        Bitmap bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }

        ShinigamiPageView cell = new ShinigamiPageView(context);
        cell.showFile(file, ShinigamiQuality.AUTO);
        View surface = cell.findViewWithTag("shinigami_page_surface");
        assertEquals(View.VISIBLE, surface.getVisibility());

        cell.showLoading();
        assertEquals("gambar tidak boleh hilang saat diminta loading",
                View.VISIBLE, surface.getVisibility());
        assertEquals("spinner tidak boleh menutupi gambar",
                View.GONE, cell.findViewWithTag("shinigami_page_loading").getVisibility());
        assertTrue(cell.hasContent());

        // Kegagalan yang datang belakangan juga tidak boleh menghapus gambar.
        cell.showFailure("HTTP 500");
        assertEquals(View.VISIBLE, surface.getVisibility());
        assertEquals(View.GONE, cell.findViewWithTag("shinigami_page_error").getVisibility());
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** Placeholder memakai rasio yang diketahui, jadi tidak melompat saat gambar tiba. */
    @Test public void placeholderUsesKnownRatioInsteadOfScreenHeight() {
        Context context = ApplicationProvider.getApplicationContext();
        ShinigamiPageView cell = new ShinigamiPageView(context);
        cell.setPlaceholderRatio(1.5d);
        cell.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(320, cell.getMeasuredWidth());
        assertEquals(480, cell.getMeasuredHeight());
        // Memuat ulang tidak boleh membuang rasio yang sudah diketahui.
        cell.showLoading();
        cell.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(480, cell.getMeasuredHeight());
    }

    @Test public void failureKeepsCellVisibleAndRetryOnlyReloadsThatPage() {
        ShinigamiPageView cell = new ShinigamiPageView(ApplicationProvider.getApplicationContext());
        int[] retries = {0};
        cell.setRetryAction(() -> retries[0]++);
        cell.showFailure("HTTP 403: akses gambar dibatasi.");
        cell.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertTrue(cell.getMeasuredHeight() > 0);
        assertEquals(View.VISIBLE, cell.findViewWithTag("shinigami_page_error").getVisibility());
        cell.findViewWithTag("shinigami_page_retry").performClick();
        assertEquals(1, retries[0]);
    }
}
