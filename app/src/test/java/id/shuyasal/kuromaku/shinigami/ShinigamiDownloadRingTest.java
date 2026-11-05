package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Indikator lingkaran hanya menampilkan persentase yang benar-benar diberikan. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiDownloadRingTest {

    private ShinigamiDownloadRing ring() {
        Context context = ApplicationProvider.getApplicationContext();
        ShinigamiDownloadRing view = new ShinigamiDownloadRing(context);
        view.setColors(0xFF2196F3, 0xFF333333, 0xFFC0392B);
        view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(120, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(120, android.view.View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 120, 120);
        return view;
    }

    @Test public void percentIsClampedAndReported() {
        ShinigamiDownloadRing view = ring();
        view.setPercent(48, false);
        assertEquals(48, view.progressPercent());
        view.setPercent(150, false);
        assertEquals(100, view.progressPercent());
        view.setPercent(-10, false);
        assertEquals(0, view.progressPercent());
    }

    @Test public void doneStateShowsCheckInsteadOfPercent() {
        ShinigamiDownloadRing view = ring();
        view.setState(ShinigamiDownloadRing.RUNNING);
        view.setPercent(100, false);
        assertFalse(view.showsCheck());
        view.setState(ShinigamiDownloadRing.DONE);
        assertTrue(view.showsCheck());
        assertEquals(100, view.progressPercent());
    }

    @Test public void failedStateKeepsLastRealPercent() {
        ShinigamiDownloadRing view = ring();
        view.setState(ShinigamiDownloadRing.FAILED);
        view.setPercent(37, false);
        assertEquals(37, view.progressPercent());
        assertFalse(view.showsCheck());
    }
}
