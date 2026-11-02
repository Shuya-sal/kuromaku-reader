package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import id.shuyasal.kuromaku.ui.ScrollMath;

/**
 * Regression tests for BUG 2: the last paragraph must be reachable and the
 * saved position must survive leaving and reopening a chapter.
 */
public class ScrollMathTest {

    @Test
    public void onlyReportsHundredWhenTheEndIsVisible() {
        assertEquals(0, ScrollMath.percent(-1, 180));
        assertEquals(50, ScrollMath.percent(89, 180));
        assertEquals(99, ScrollMath.percent(178, 180));
        assertEquals(100, ScrollMath.percent(179, 180));
        assertEquals(100, ScrollMath.percent(180, 180));
    }

    @Test
    public void neverClaimsFinishedBeforeTheLastBlock() {
        // A 400-block chapter: only the final block may report 100%.
        for (int last = 0; last < 399; last++) {
            int percent = ScrollMath.percent(last, 400);
            assertTrue("last=" + last + " reported " + percent, percent <= 99);
        }
        assertEquals(100, ScrollMath.percent(399, 400));
    }

    @Test
    public void handlesEmptyContent() {
        assertEquals(0, ScrollMath.percent(0, 0));
        assertEquals(0, ScrollMath.restoreIndex(50, 0));
    }

    @Test
    public void restoresExactBlockFromPercent() {
        assertEquals(0, ScrollMath.restoreIndex(0, 180));
        assertEquals(90, ScrollMath.restoreIndex(50, 180));
        assertEquals(179, ScrollMath.restoreIndex(100, 180));
        assertEquals(179, ScrollMath.restoreIndex(140, 180));
    }

    @Test
    public void restoreStaysInsideBounds() {
        assertEquals(0, ScrollMath.restoreIndex(-20, 10));
        assertEquals(9, ScrollMath.restoreIndex(100, 10));
        for (int percent = 0; percent <= 100; percent++) {
            int index = ScrollMath.restoreIndex(percent, 37);
            assertEquals(true, index >= 0 && index < 37);
        }
    }

    @Test
    public void footerPaddingClearsPanelAndGestureBar() {
        // 112px panel gap plus a 48px navigation bar inset.
        assertEquals(160, ScrollMath.footerPadding(112, 48));
        assertEquals(112, ScrollMath.footerPadding(112, 0));
        assertEquals(48, ScrollMath.footerPadding(0, 48));
        assertEquals(0, ScrollMath.footerPadding(-10, -10));
    }
}
