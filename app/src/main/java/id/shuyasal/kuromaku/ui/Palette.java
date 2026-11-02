package id.shuyasal.kuromaku.ui;

import android.graphics.drawable.GradientDrawable;

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
    /** Touch feedback colour, translucent so it sits on any surface. */
    public final int ripple;

    private Palette(int background, int surface, int text, int muted, int accent,
                    int divider, boolean darkIcons, String label, int ripple) {
        this.background = background;
        this.surface = surface;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.divider = divider;
        this.darkIcons = darkIcons;
        this.label = label;
        this.ripple = ripple;
    }

    public static Palette of(int theme) {
        switch (theme) {
            case Prefs.THEME_DARK:
                return new Palette(
                        android.graphics.Color.parseColor("#111318"),
                        android.graphics.Color.parseColor("#1A1E26"),
                        android.graphics.Color.parseColor("#E4E2DE"),
                        android.graphics.Color.parseColor("#9AA1AE"),
                        android.graphics.Color.parseColor("#A8C7FA"),
                        android.graphics.Color.parseColor("#2A313D"),
                        true,
                        "Gelap",
                        android.graphics.Color.parseColor("#33FFFFFF"));
            case Prefs.THEME_SEPIA:
                return new Palette(
                        android.graphics.Color.parseColor("#F5ECD9"),
                        android.graphics.Color.parseColor("#EFE3CB"),
                        android.graphics.Color.parseColor("#4A3C2A"),
                        android.graphics.Color.parseColor("#8A7355"),
                        android.graphics.Color.parseColor("#8A5A2B"),
                        android.graphics.Color.parseColor("#DFCFB0"),
                        false,
                        "Sepia",
                        android.graphics.Color.parseColor("#338A5A2B"));
            default:
                return new Palette(
                        android.graphics.Color.parseColor("#FDFBF7"),
                        android.graphics.Color.parseColor("#FFFFFF"),
                        android.graphics.Color.parseColor("#24262B"),
                        android.graphics.Color.parseColor("#6B7280"),
                        android.graphics.Color.parseColor("#2F5FA8"),
                        android.graphics.Color.parseColor("#E6E2DA"),
                        false,
                        "Terang",
                        android.graphics.Color.parseColor("#332F5FA8"));
        }
    }

    /** Icon resource that represents this theme in a toggle button. */
    public int themeIcon() {
        return darkIcons
                ? id.shuyasal.kuromaku.R.drawable.ic_moon
                : id.shuyasal.kuromaku.R.drawable.ic_sun;
    }

    /** Shared rounded shape helper kept for callers that only need a fill. */
    public GradientDrawable surfaceShape(android.content.Context context) {
        return Ui.rounded(surface, divider, 12, context);
    }
}
