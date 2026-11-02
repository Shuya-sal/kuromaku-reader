package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Novel;

/**
 * Library home: one card per novel, each with its cover, title and a one-line
 * summary of what has been loaded.
 *
 * <p>Tapping a card opens that novel's detail page. The cards are plain views
 * with stable tags so a Robolectric test can measure them and tap them without a
 * network fetch.
 */
public final class NovelListPage {

    /** Tapping a card opens the novel. */
    public interface Listener {
        void onOpenNovel(Novel novel);
    }

    private static final int CARD_WIDTH_DP = 108;

    private final Context context;
    private final Prefs prefs;
    private final Palette palette;
    private final List<Novel> novels;
    private final Listener listener;

    public NovelListPage(Context context, Prefs prefs, Palette palette, List<Novel> novels,
                         Listener listener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.novels = novels;
        this.listener = listener;
    }

    /** The scrollable column shown on the home screen. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("novel_list");

        TextView heading = Ui.label(context, "Perpustakaan", 20, palette.text);
        heading.setTag("library_heading");
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);

        TextView sub = Ui.label(context,
                novels.size() + " novel · sumber Kaori Translation", 12, palette.muted);
        sub.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 14));
        content.addView(sub);

        for (Novel novel : novels) {
            content.addView(card(novel));
        }
        return content;
    }

    /** One novel card: cover on the left, text on the right. */
    private View card(Novel novel) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setTag("novel_card:" + novel.id);
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        card.setPadding(Ui.dp(context, 10), Ui.dp(context, 10),
                Ui.dp(context, 12), Ui.dp(context, 10));

        addCover(card, novel);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(context, 12), 0, 0, 0);

        TextView title = Ui.label(context, novel.title, 16, palette.text);
        title.setTag("novel_card_title:" + novel.id);
        title.setTypeface(null, Typeface.BOLD);
        texts.addView(title);

        TextView japanese = Ui.label(context, novel.titleJp, 11, palette.muted);
        japanese.setMaxLines(2);
        japanese.setPadding(0, Ui.dp(context, 3), 0, Ui.dp(context, 6));
        texts.addView(japanese);

        Catalog catalog = id.shuyasal.kuromaku.Session.catalog(novel.id);
        String summary;
        if (catalog == null) {
            summary = "Belum dimuat";
        } else if (catalog.size() == 0) {
            summary = "Katalog kosong";
        } else {
            summary = catalog.volumes.size() + " volume · " + catalog.size() + " chapter";
        }
        TextView meta = Ui.label(context, summary, 11, palette.muted);
        meta.setTag("novel_card_meta:" + novel.id);
        texts.addView(meta);

        // Progress and continue-reading stay per novel: this reads only this
        // novel's own bookmark.
        String last = prefs.lastUrl();
        if (catalog != null && !last.isEmpty() && catalog.indexOf(last) >= 0) {
            id.shuyasal.kuromaku.data.Chapter resume = catalog.findByUrl(last);
            if (resume != null) {
                int percent = prefs.progress(resume.url);
                String label = percent > 0
                        ? "Lanjutkan: " + resume.label + " (" + percent + "%)"
                        : "Terakhir: " + resume.label;
                TextView progress = Ui.label(context, label, 11, palette.accent);
                progress.setTag("novel_card_progress:" + novel.id);
                progress.setPadding(0, Ui.dp(context, 6), 0, 0);
                texts.addView(progress);
            }
        }

        card.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        card.setOnClickListener(v -> listener.onOpenNovel(novel));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(context, 10));
        card.setLayoutParams(params);
        return card;
    }

    private void addCover(LinearLayout card, Novel novel) {
        int width = Ui.dp(context, CARD_WIDTH_DP);
        float ratio = novel.coverRatio > 0 ? novel.coverRatio : 0.70f;
        int height = Math.max(1, Math.round(width / ratio));

        RatioFrameLayout holder = new RatioFrameLayout(context);
        holder.setTag("novel_card_cover:" + novel.id);
        holder.setRatio(ratio);
        holder.setBackground(Ui.rounded(palette.background, palette.divider, 8, context));

        TextView placeholder = Ui.label(context, "Sampul", 10, palette.muted);
        placeholder.setTag("novel_card_placeholder:" + novel.id);
        placeholder.setGravity(Gravity.CENTER);
        holder.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(context);
        image.setTag("novel_card_image:" + novel.id);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            Glide.with(context).load(novel.coverUrl).centerCrop().into(image);
        } catch (Exception ignored) {
            // Placeholder stays.
        }

        card.addView(holder, new LinearLayout.LayoutParams(width, height));
    }
}
