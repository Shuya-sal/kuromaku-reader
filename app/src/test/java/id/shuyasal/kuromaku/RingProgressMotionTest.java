package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.ui.Motion;
import id.shuyasal.kuromaku.ui.RingProgress;

/**
 * Feature 2: the download ring must show the measured fraction and nothing else.
 *
 * <p>Two claims are checked here that the rest of the suite only asserted in a
 * comment: that the value reaches the view at once when the system asks for
 * reduced motion, and that a ring with no measurable total never reports a
 * percentage. A ring that animates towards an invented number would be a lie
 * drawn 60 times a second.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class RingProgressMotionTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        setAnimatorScale(1f);
    }

    @After
    public void tearDown() {
        setAnimatorScale(1f);
    }

    private void setAnimatorScale(float scale) {
        Settings.Global.putFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, scale);
    }

    private RingProgress ring() {
        RingProgress view = new RingProgress(context);
        view.colors(0xFF202020, 0xFF7FD4FF, 3);
        view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(40, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(40, android.view.View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 40, 40);
        return view;
    }

    /** With "Remove animations" on, the new value is painted immediately. */
    @Test
    public void withMotionOffTheRingJumpsStraightToTheMeasuredValue() {
        setAnimatorScale(0f);
        assertFalse("Remove animations harus terbaca", Motion.enabled(context));

        RingProgress view = ring();
        view.setProgress(67);

        assertEquals("nilai harus langsung tampil tanpa animasi", 67, view.percentShown());
    }

    /**
     * With motion on the ring still ends on exactly the value it was given: the
     * animation interpolates, it never overshoots or invents a fraction.
     */
    @Test
    public void withMotionOnTheRingSettlesOnTheSameValue() {
        setAnimatorScale(1f);
        assertTrue("animasi harus aktif dengan skala 1", Motion.enabled(context));

        RingProgress view = ring();
        view.setProgress(67);

        // Drive the clock in small steps instead of assuming the whole 180 ms
        // elapses within one idle. A CI runner schedules frames differently from a
        // laptop, so one large jump can leave the animation mid-flight and turn
        // this into a flaky test that says nothing about the ring.
        org.robolectric.shadows.ShadowLooper looper =
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
        for (int step = 0; step < 40 && view.percentShown() != 67; step++) {
            looper.idleFor(java.time.Duration.ofMillis(20));
        }

        assertEquals("setelah animasi selesai nilainya harus 67", 67, view.percentShown());
    }

    /** No total size known: the ring spins and reports no percentage at all. */
    @Test
    public void aRingWithoutAMeasuredTotalNeverReportsAPercentage() {
        RingProgress view = ring();
        view.setProgress(-1);

        assertTrue("total tidak diketahui berarti spinner", view.isIndeterminate());
        assertEquals("spinner tidak boleh melaporkan persen", -1, view.percentShown());
    }

    /** A fraction outside 0..100 is clamped, not drawn outside the ring. */
    @Test
    public void aFractionOutsideTheRangeIsClampedInsteadOfDrawnOutsideTheRing() {
        setAnimatorScale(0f);
        RingProgress view = ring();

        view.setProgress(250);
        assertEquals("di atas 100 harus dijepit ke 100", 100, view.percentShown());

        view.setProgress(0);
        assertEquals(0, view.percentShown());
    }

    /** Returning from "unknown" to a measured value clears the spinner. */
    @Test
    public void aMeasuredValueAfterAnUnknownTotalClearsTheSpinner() {
        // Motion off so the assertion reads the settled value, not frame zero of
        // the 180 ms interpolation.
        setAnimatorScale(0f);
        RingProgress view = ring();
        view.setProgress(-1);
        assertTrue(view.isIndeterminate());

        view.setProgress(40);
        assertFalse("cincin harus berhenti berputar", view.isIndeterminate());
        assertEquals(40, view.percentShown());
    }
}
