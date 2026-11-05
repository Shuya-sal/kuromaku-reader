package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Reading preferences and per-chapter progress.
 *
 * <p>Theme, font size and justification belong to the app, but progress does
 * not: each novel keeps its own bookmark, scroll position and volume accordion
 * state. Two novels can therefore have chapters with similar names without ever
 * overwriting each other. A {@link #scoped(String)} instance writes behind a
 * {@code <novel>:} prefix; the unscoped instance keeps the original keys so the
 * reading position saved by earlier versions of the app is not lost.
 */
public final class Prefs {

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_SEPIA = 2;
    /** Ikuti tema sistem (terang/gelap) saat aplikasi dibuka. */
    public static final int THEME_SYSTEM = 3;

    /** The preference file that holds theme, bookmarks and per-chapter progress. */
    public static final String FILE_NAME = "kuromaku_reader";

    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT = "font_size";
    private static final String KEY_LINE = "line_spacing";
    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_PROGRESS = "progress:";
    private static final String KEY_SCROLL_POS = "scroll_pos:";
    private static final String KEY_SCROLL_OFF = "scroll_off:";
    private static final String KEY_JUSTIFY = "justify";
    private static final String KEY_EXPANDED = "expanded_volumes";

    private final SharedPreferences prefs;
    /** "" for the app-wide store, otherwise "<novelId>:". */
    private final String prefix;
    private final Context context;

    public Prefs(Context context) {
        this(context, "");
    }

    public Prefs(Context context, String novelId) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        this.prefix = novelId == null || novelId.isEmpty() ? "" : novelId + ":";
    }

    /** A view of the same store bound to one novel. */
    public Prefs scoped(String novelId) {
        return new Prefs(context, novelId);
    }

    private String key(String base) {
        return prefix + base;
    }

    public int theme() {
        return prefs.getInt(KEY_THEME, THEME_LIGHT);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    /**
     * Tema yang benar-benar dipakai untuk menggambar. Nilai tersimpan tetap
     * dikembalikan apa adanya oleh {@link #theme()} agar pilihan pengguna tidak
     * hilang saat mode sistem berubah.
     */
    public int resolvedTheme() {
        int stored = theme();
        if (stored != THEME_SYSTEM) return stored;
        boolean night = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return night ? THEME_DARK : THEME_LIGHT;
    }

    public int fontSize() {
        return prefs.getInt(KEY_FONT, 18);
    }

    public void setFontSize(int size) {
        prefs.edit().putInt(KEY_FONT, Math.max(13, Math.min(34, size))).apply();
    }

    public float lineSpacing() {
        return prefs.getFloat(KEY_LINE, 1.65f);
    }

    public void setLineSpacing(float spacing) {
        prefs.edit().putFloat(KEY_LINE, Math.max(1.2f, Math.min(2.2f, spacing))).apply();
    }

    public boolean justify() {
        return prefs.getBoolean(KEY_JUSTIFY, true);
    }

    public void setJustify(boolean justify) {
        prefs.edit().putBoolean(KEY_JUSTIFY, justify).apply();
    }

    /** Last chapter opened in this novel. */
    public String lastUrl() {
        String scoped = prefs.getString(key(KEY_LAST_URL), "");
        if (scoped != null && !scoped.isEmpty()) return scoped;
        // Fall back to a bookmark written before novels were separated.
        return prefs.getString(KEY_LAST_URL, "");
    }

    public void setLastUrl(String url) {
        prefs.edit().putString(key(KEY_LAST_URL), url == null ? "" : url).apply();
    }

    /**
     * Exact scroll position (first visible item + pixel offset) so reopening a
     * chapter lands on the same line instead of an approximation.
     */
    public void setScroll(String url, int position, int offset) {
        if (url == null) return;
        prefs.edit()
                .putInt(key(KEY_SCROLL_POS) + url, Math.max(0, position))
                .putInt(key(KEY_SCROLL_OFF) + url, Math.max(0, offset))
                .apply();
    }

    public int scrollPosition(String url) {
        if (url == null) return 0;
        return prefs.getInt(key(KEY_SCROLL_POS) + url,
                prefs.getInt(KEY_SCROLL_POS + url, 0));
    }

    public int scrollOffset(String url) {
        if (url == null) return 0;
        return prefs.getInt(key(KEY_SCROLL_OFF) + url,
                prefs.getInt(KEY_SCROLL_OFF + url, 0));
    }

    public boolean hasScroll(String url) {
        return url != null
                && (prefs.contains(key(KEY_SCROLL_POS) + url)
                || prefs.contains(KEY_SCROLL_POS + url));
    }

    public int progress(String url) {
        if (url == null) return 0;
        return prefs.getInt(key(KEY_PROGRESS) + url,
                prefs.getInt(KEY_PROGRESS + url, 0));
    }

    public void setProgress(String url, int value) {
        if (url == null) return;
        prefs.edit().putInt(key(KEY_PROGRESS) + url, Math.max(0, value)).apply();
    }

    public boolean isRead(String url) {
        return progress(url) > 95;
    }

    /** Volume keys whose chapter list is open on the library screen. */
    public java.util.Set<String> expandedVolumes() {
        java.util.Set<String> stored = prefs.getStringSet(key(KEY_EXPANDED), null);
        return stored == null
                ? new java.util.HashSet<String>()
                : new java.util.HashSet<String>(stored);
    }

    public boolean isExpanded(String volumeKey) {
        return volumeKey != null && expandedVolumes().contains(volumeKey);
    }

    /** Opens a closed volume and closes an open one; returns the new state. */
    public boolean toggleExpanded(String volumeKey) {
        if (volumeKey == null) return false;
        java.util.Set<String> expanded = expandedVolumes();
        boolean nowOpen;
        if (expanded.remove(volumeKey)) {
            nowOpen = false;
        } else {
            expanded.add(volumeKey);
            nowOpen = true;
        }
        prefs.edit().putStringSet(key(KEY_EXPANDED), expanded).apply();
        return nowOpen;
    }

    /**
     * Erases everything stored for this novel: progress, bookmarks, scroll
     * positions, the last chapter and the accordion state.
     *
     * <p>Only this novel's keys are touched, so deleting one novel can never
     * disturb another's reading position. This is what the "hapus progres
     * membaca" checkbox on the delete dialog calls — and what nothing else calls:
     * removing a novel from the shelf, or deleting its downloads, leaves progress
     * intact on purpose.
     */
    public void clearNovelData() {
        if (prefix.isEmpty()) return;
        java.util.Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        int removed = 0;
        for (String stored : all.keySet()) {
            if (!stored.startsWith(prefix)) continue;
            editor.remove(stored);
            removed++;
        }
        if (removed > 0) editor.apply();
    }

    /** True when this scoped store holds any reading state for its novel. */
    public boolean hasNovelData() {
        if (prefix.isEmpty()) return false;
        String last = prefs.getString(key(KEY_LAST_URL), "");
        if (last != null && !last.isEmpty()) return true;
        for (String stored : prefs.getAll().keySet()) {
            if (stored.startsWith(prefix)) return true;
        }
        return false;
    }
}
