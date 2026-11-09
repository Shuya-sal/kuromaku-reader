package id.shuyasal.kuromaku;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import id.shuyasal.kuromaku.anime.OtakudesuActivity;
import id.shuyasal.kuromaku.komiku.KomikuActivity;
import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;
import id.shuyasal.kuromaku.ui.GlassCard;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Halaman awal aplikasi: pemilih sumber bacaan.
 *
 * <p>Empat kartu menuju Novel, Manga Voratoon, Manga Shinigami, dan Anime Otakudesu. Halaman ini
 * hanya pintu masuk — tidak mengubah tampilan maupun data ketiga sumber.
 * Latar memakai gambar lokal dengan {@code centerCrop} dan lapisan gelap agar
 * teks tetap terbaca di mode terang maupun gelap.
 */
public class SourceHubActivity extends AppCompatActivity {

    private Palette palette;
    private ImageView background;
    private int activeResolvedTheme = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        tampilkanLaporanCrash();
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(palette.background);

        background = new ImageView(this);
        background.setTag("source_hub_background");
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImageResource(R.drawable.source_hub_background);
        background.setContentDescription(getString(R.string.source_hub_background_description));
        root.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Lapisan gelap: gambar tetap terlihat, teks tetap kontras.
        View scrim = new View(this);
        scrim.setTag("source_hub_scrim");
        GradientDrawable shade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3111318, 0x73111318, 0xC7111318});
        scrim.setBackground(shade);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setTag("source_hub_scroll");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 28));

        TextView eyebrow = Ui.label(this, getString(R.string.source_hub_eyebrow), 12, 0xCCE4E2DE);
        eyebrow.setLetterSpacing(0.18f);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setTag("source_hub_eyebrow");
        content.addView(eyebrow);

        TextView title = Ui.label(this, getString(R.string.source_hub_title), 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        title.setTag("source_hub_title");
        content.addView(title);

        TextView subtitle = Ui.label(this, getString(R.string.source_hub_subtitle), 13, 0xCCE4E2DE);
        subtitle.setPadding(0, Ui.dp(this, 8), 0, 0);
        content.addView(subtitle);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(0, Ui.dp(this, 24), 0, 0);

        cards.addView(card(R.string.source_novel, R.string.source_novel_note,
                R.drawable.ic_book, 0xFF7C5CFF,
                "source_hub_card_novel", () -> open(MainActivity.class)));
        cards.addView(card(R.string.source_voratoon, R.string.source_voratoon_note,
                R.drawable.ic_panels, 0xFF1FA2A6,
                "source_hub_card_voratoon", () -> open(MangaActivity.class)));
        cards.addView(card(R.string.source_shinigami, R.string.source_shinigami_note,
                R.drawable.ic_moon, 0xFFC2453F,
                "source_hub_card_shinigami", () -> open(ShinigamiActivity.class)));
        cards.addView(card(R.string.source_komiku, R.string.source_komiku_note,
                R.drawable.ic_panels, 0xFF2E9E6B,
                "source_hub_card_komiku", () -> open(KomikuActivity.class)));
        cards.addView(card(R.string.source_otakudesu, R.string.source_otakudesu_note,
                R.drawable.ic_film, 0xFF2F7DE1,
                "source_hub_card_otakudesu", () -> open(OtakudesuActivity.class)));

        content.addView(cards);

        TextView footer = Ui.label(this, getString(R.string.source_hub_footer), 11, 0x99E4E2DE);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(this, 28), 0, 0);
        content.addView(footer);

        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(0, 0, 0, 0);
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        setContentView(root);
    }

    private void open(Class<?> target) {
        // SINGLE_TOP + CLEAR_TOP: berpindah sumber tidak menumpuk activity.
        startActivity(new Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private View card(int titleRes, int noteRes, int iconRes, int accent,
                      String tag, Runnable action) {
        // Kartu kaca: latar semi-transparan + salinan ilustrasi yang diburamkan.
        GlassCard card = new GlassCard(this, palette, "source_hub_background");
        card.setBackgroundView(background);
        card.setTag(tag);
        card.setContentDescription(getString(titleRes));
        card.setOnClickListener(v -> action.run());
        card.setMinimumHeight(Ui.dp(this, 92));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));

        ImageView badge = new ImageView(this);
        badge.setImageDrawable(Ui.tinted(this, iconRes, Color.WHITE));
        badge.setBackground(Ui.rounded(accent, 0x40FFFFFF, 14, this));
        badge.setPadding(Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13));
        badge.setContentDescription(getString(titleRes));
        row.addView(badge, new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);

        // Kaca selalu gelap sehingga teks putih tetap kontras di kedua tema.
        int textColor = 0xFFF7F5F3;
        int noteColor = 0xD9F7F5F3;

        TextView name = Ui.label(this, getString(titleRes), 16, textColor);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(name);

        TextView note = Ui.label(this, getString(noteRes), 12, noteColor);
        note.setMaxLines(2);
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setPadding(0, Ui.dp(this, 3), 0, 0);
        text.addView(note);

        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(Ui.tinted(this, R.drawable.ic_chevron_right, 0xCCF7F5F3));
        chevron.setContentDescription(getString(R.string.source_hub_open));
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dp(this, 24), Ui.dp(this, 24)));

        card.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (card.backdropView() != null) {
            card.backdropView().setImageResource(R.drawable.source_hub_background);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 12);
        card.setLayoutParams(params);
        return card;
    }

    @Override
    protected void onResume() {
        super.onResume();
        int currentResolved = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && currentResolved != activeResolvedTheme) {
            recreate();
        }
    }

    /**
     * Tampilkan laporan crash terakhir saat aplikasi dibuka.
     *
     * <p>Dengan ini penyebab force close bisa disalin pengguna tanpa adb: cukup
     * buka aplikasi, salin laporan, kirim. Dialog tidak menghalangi pemakaian —
     * Source Hub tetap bisa dipakai setelah ditutup.
     */
    private void tampilkanLaporanCrash() {
        // Dua sumber: exception Java yang tertangkap saat crash, dan riwayat
        // kematian proses dari Android (crash native, ANR, dibunuh sistem).
        String tertangkap = id.shuyasal.kuromaku.offline.CrashLog.read(this);
        String riwayat = id.shuyasal.kuromaku.offline.ExitReports.summary(this);
        if (tertangkap.isEmpty() && riwayat.isEmpty()) return;
        final String laporan = tertangkap.isEmpty() ? riwayat
                : riwayat.isEmpty() ? tertangkap : tertangkap + "\n\n" + riwayat;

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.TextView isi = new android.widget.TextView(this);
        isi.setText(laporan);
        isi.setTextSize(11f);
        isi.setTypeface(Typeface.MONOSPACE);
        isi.setTextIsSelectable(true);
        int pad = id.shuyasal.kuromaku.ui.Ui.dp(this, 14);
        isi.setPadding(pad, pad, pad, pad);
        scroll.addView(isi);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Aplikasi terakhir berhenti tak terduga")
                .setMessage("Salin laporan ini dan kirimkan supaya penyebabnya bisa "
                        + "diperbaiki. Data dan unduhan Master tidak terpengaruh.")
                .setView(scroll)
                .setPositiveButton("Salin laporan", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData
                                .newPlainText("crash", laporan));
                        android.widget.Toast.makeText(this, "Laporan crash disalin.",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Hapus", (dialog, which) -> {
                    id.shuyasal.kuromaku.offline.CrashLog.clear(this);
                })
                .setNegativeButton("Tutup", null)
                .show();
    }
}
