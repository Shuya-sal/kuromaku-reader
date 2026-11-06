package id.shuyasal.kuromaku.ui;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {
    private ThemeManager() {}

    /** Terapkan tema yang tersimpan di Prefs ke sistem Android (AppCompatDelegate). */
    public static void applyTheme(Context context) {
        if (context == null) return;
        int theme = new Prefs(context).theme();
        apply(theme);
    }

    /**
     * Terapkan nilai tema aktif:
     * - Light -> MODE_NIGHT_NO
     * - Dark -> MODE_NIGHT_YES
     * - System -> MODE_NIGHT_FOLLOW_SYSTEM
     */
    public static void apply(int theme) {
        int nightMode;
        switch (theme) {
            case Prefs.THEME_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case Prefs.THEME_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case Prefs.THEME_SYSTEM:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }
}
