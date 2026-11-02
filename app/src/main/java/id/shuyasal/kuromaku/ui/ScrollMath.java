package id.shuyasal.kuromaku.ui;

/**
 * Pure maths behind scroll position and reading progress.
 *
 * <p>Kept free of Android classes so the behaviour that BUG 2 touched (reaching
 * the last paragraph, restoring the previous position) can be unit tested.
 */
public final class ScrollMath {

    private ScrollMath() {
    }

    /**
     * Reading progress in percent. Reports 100 only when the final item is
     * actually visible, so "selesai" never lies.
     */
    public static int percent(int lastVisiblePosition, int itemCount) {
        if (itemCount <= 0) return 0;
        if (lastVisiblePosition >= itemCount - 1) return 100;
        if (lastVisiblePosition < 0) return 0;
        int value = Math.round((lastVisiblePosition + 1) * 100f / itemCount);
        return Math.max(0, Math.min(99, value));
    }

    /** Item index to scroll back to when reopening a chapter at {@code percent}. */
    public static int restoreIndex(int percent, int itemCount) {
        if (itemCount <= 0) return 0;
        int clamped = Math.max(0, Math.min(100, percent));
        if (clamped >= 100) return itemCount - 1;
        long index = Math.round(itemCount * (clamped / 100f));
        if (index >= itemCount) index = itemCount - 1;
        return (int) Math.max(0, index);
    }

    /**
     * Extra bottom padding so the final paragraph clears the navigation panel
     * and the system navigation bar.
     */
    public static int footerPadding(int basePaddingPx, int systemBottomInsetPx) {
        return Math.max(0, basePaddingPx) + Math.max(0, systemBottomInsetPx);
    }
}
