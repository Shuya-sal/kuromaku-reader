package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
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
