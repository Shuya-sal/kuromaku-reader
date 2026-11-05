package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Kontrak hitungan ketukan: tiga ketukan cepat tidak boleh jadi dua. */
public class ShinigamiTapsTest {

    @Test public void threeQuickTapsResolveAsThree() {
        ShinigamiTaps taps = new ShinigamiTaps();
        taps.register(1000);
        taps.register(1080);
        taps.register(1160);
        assertEquals(3, taps.resolve());
    }

    @Test public void twoTapsResolveAsTwo() {
        ShinigamiTaps taps = new ShinigamiTaps();
        taps.register(1000);
        taps.register(1050);
        assertEquals(2, taps.resolve());
    }

    @Test public void slowTapStartsNewSequence() {
        ShinigamiTaps taps = new ShinigamiTaps();
        taps.register(1000);
        taps.register(1000 + ShinigamiTaps.WINDOW_MS + 1);
        assertEquals(1, taps.resolve());
    }

    @Test public void resolveClearsPending() {
        ShinigamiTaps taps = new ShinigamiTaps();
        taps.register(1000);
        taps.resolve();
        assertEquals(0, taps.pending());
        assertFalse(taps.pending() > 0);
        taps.register(2000);
        assertEquals(1, taps.resolve());
        assertTrue(ShinigamiTaps.WINDOW_MS >= 250 && ShinigamiTaps.WINDOW_MS <= 400);
    }
}
