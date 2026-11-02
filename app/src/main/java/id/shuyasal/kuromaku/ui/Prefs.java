package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** Reading preferences and per-chapter progress. */
public final class Prefs {

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_SEPIA = 2;

    private static final String FILE = "kuromaku_reader";

    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT = "font_size";
    private static final String KEY_LINE = "line_spacing";
    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_PROGRESS = "progress:";
    private static final String KEY_JUSTIFY = "justify";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int theme() {
        return prefs.getInt(KEY_THEME, THEME_LIGHT);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
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

    public String lastUrl() {
        return prefs.getString(KEY_LAST_URL, "");
    }

    public void setLastUrl(String url) {
        prefs.edit().putString(KEY_LAST_URL, url == null ? "" : url).apply();
    }

    public int progress(String url) {
        if (url == null) return 0;
        return prefs.getInt(KEY_PROGRESS + url, 0);
    }

    public void setProgress(String url, int value) {
        if (url == null) return;
        prefs.edit().putInt(KEY_PROGRESS + url, Math.max(0, value)).apply();
    }

    public boolean isRead(String url) {
        return progress(url) > 95;
    }
}
