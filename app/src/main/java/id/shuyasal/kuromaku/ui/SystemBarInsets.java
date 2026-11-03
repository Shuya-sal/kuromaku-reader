package id.shuyasal.kuromaku.ui;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

/**
 * Holds the current system bar insets for one screen.
 *
 * <p>Why this class exists (BUG 2.6): each screen used to keep its own pair of
 * {@code int} fields and only applied padding "when the value changed". Two things
 * went wrong with that:
 *
 * <ol>
 *   <li>A theme change rebuilds the whole view tree, so the new toolbar starts at
 *       padding 0 while the old fields still hold the previous height. The guard
 *       concluded "nothing changed" and the status bar padding was never applied —
 *       the title and the Kembali/refresh/theme buttons slid under the clock.</li>
 *   <li>Conversely, a plain layout pass can deliver insets that report no system
 *       bars at all. Storing that zero erased the real height, so the next apply
 *       removed the padding that was already correct.</li>
 * </ol>
 *
 * <p>The rule is therefore: remember the real value, ignore an "empty" dispatch
 * when a real one is already known, and let callers re-apply unconditionally.
 * {@code setPadding} is idempotent, so applying the same insets many times can
 * never stack them.
 */
public final class SystemBarInsets {

    private Insets current = Insets.NONE;

    /**
     * Records the insets of one dispatch and returns the value callers should use.
     *
     * <p>An empty dispatch (top and bottom both zero) is only accepted when nothing
     * non-zero was known yet; these screens never hide the system bars, so a zero
     * reading while a real height is known is a transient measurement, not a state.
     */
    public Insets accept(WindowInsetsCompat windowInsets) {
        if (windowInsets == null) return current;
        Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
        boolean empty = bars.top == 0 && bars.bottom == 0;
        if (empty && (current.top > 0 || current.bottom > 0)) return current;
        current = bars;
        return current;
    }

    /** The last known insets; {@link Insets#NONE} before the first dispatch. */
    public Insets get() {
        return current;
    }

    /** For packages that must start from scratch (tests, window recreation). */
    public void reset() {
        current = Insets.NONE;
    }
}
