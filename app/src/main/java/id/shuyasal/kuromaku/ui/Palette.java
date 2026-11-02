package id.shuyasal.kuromaku.ui;

import android.graphics.Color;

/** Colour set per reading theme. */
public final class Palette {

    public final int background;
    public final int surface;
    public final int text;
    public final int muted;
    public final int accent;
    public final int divider;
    public final boolean darkIcons;
    public final String label;

    private Palette(int background, int surface, int text, int muted, int accent,
                    int divider, boolean darkIcons, String label) {
        this.background = background;
        this.surface = surface;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.divider = divider;
        this.darkIcons = darkIcons;
        this.label = label;
    }

    public static Palette of(int theme) {
        switch (theme) {
            case Prefs.THEME_DARK:
                return new Palette(
                        Color.parseColor("#111318"),
                        Color.parseColor("#1A1E26"),
                        Color.parseColor("#E4E2DE"),
                        Color.parseColor("#9AA1AE"),
                        Color.parseColor("#A8C7FA"),
                        Color.parseColor("#2A313D"),
                        true,
                        "Gelap");
            case Prefs.THEME_SEPIA:
                return new Palette(
                        Color.parseColor("#F5ECD9"),
                        Color.parseColor("#EFE3CB"),
                        Color.parseColor("#4A3C2A"),
                        Color.parseColor("#8A7355"),
                        Color.parseColor("#8A5A2B"),
                        Color.parseColor("#DFCFB0"),
                        false,
                        "Sepia");
            default:
                return new Palette(
                        Color.parseColor("#FDFBF7"),
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#24262B"),
                        Color.parseColor("#6B7280"),
                        Color.parseColor("#2F5FA8"),
                        Color.parseColor("#E6E2DA"),
                        false,
                        "Terang");
        }
    }
}
