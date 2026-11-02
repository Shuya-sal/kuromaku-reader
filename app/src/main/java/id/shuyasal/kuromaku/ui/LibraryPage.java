package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.NovelInfo;
import id.shuyasal.kuromaku.data.Volume;

/**
 * Builds the library page: novel detail on top, then an accordion of volumes.
 *
 * <p>Kept separate from the activity so the layout can be measured in a
 * Robolectric test without a network fetch: cover ratio, synopsis toggle and
 * accordion behaviour are all verified against real view geometry.
 */
public final class LibraryPage {

    /** Callbacks the hosting screen wires to navigation and state changes. */
    public interface Listener {
        void onOpenChapter(Chapter chapter);

        void onToggleSynopsis();

        void onToggleVolume(String volumeKey);

        void onOpenSource();
    }

    private static final int COVER_WIDTH_DP = 210;

    private final Context context;
    private final Prefs prefs;
    private final Palette palette;
    private final Catalog catalog;
    private final boolean synopsisOpen;
    private final Listener listener;

    public LibraryPage(Context context, Prefs prefs, Palette palette, Catalog catalog,
                       boolean synopsisOpen, Listener listener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.catalog = catalog;
        this.synopsisOpen = synopsisOpen;
        this.listener = listener;
    }

    /** Builds the scrollable content column for the library screen. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("library_content");

        addCover(content);
        addTitle(content);
        addMetadata(content);
        addSynopsis(content);
        addContinue(content);
        addVolumes(content);
        addAttribution(content);
        return content;
    }

    /** Cover inside a box that always keeps the artwork's real 2:3 ratio. */
    private void addCover(LinearLayout parent) {
        int available = context.getResources().getDisplayMetrics().widthPixels
                - Ui.dp(context, 28);
        int width = Math.min(Ui.dp(context, COVER_WIDTH_DP), Math.max(0, available));
        int height = NovelInfo.coverHeight(width);

        RatioFrameLayout holder = new RatioFrameLayout(context);
        holder.setTag("cover");
        holder.setRatio(NovelInfo.COVER_RATIO);
        holder.setBackground(Ui.rounded(palette.surface, palette.divider, 10, context));

        // Drawn first, so a failed download leaves this tidy box visible
        // instead of an empty hole.
        LinearLayout placeholder = new LinearLayout(context);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setTag("cover_placeholder");
        placeholder.addView(Ui.label(context, "Sampul", 13, palette.muted));
        placeholder.addView(Ui.label(context, "tidak tersedia", 11, palette.muted));
        holder.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView cover = new ImageView(context);
        cover.setTag("cover_image");
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // A failed download must never crash the library: the placeholder
        // behind the image stays visible instead.
        try {
            Glide.with(context)
                    .load(NovelInfo.COVER_URL)
                    .centerCrop()
                    .into(cover);
        } catch (Exception ignored) {
            // Offline or no image loader available: keep the placeholder.
        }

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 14));
        row.addView(holder, new LinearLayout.LayoutParams(width, height));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addTitle(LinearLayout parent) {
        TextView title = Ui.label(context, NovelInfo.TITLE, 21, palette.text);
        title.setTag("novel_title");
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(title);

        TextView japanese = Ui.label(context, NovelInfo.TITLE_JP, 12, palette.muted);
        japanese.setTag("novel_title_jp");
        japanese.setGravity(Gravity.CENTER_HORIZONTAL);
        japanese.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 12));
        parent.addView(japanese);
    }

    private void addMetadata(LinearLayout parent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("metadata");
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        card.setPadding(Ui.dp(context, 14), Ui.dp(context, 6),
                Ui.dp(context, 14), Ui.dp(context, 6));

        addMetaRow(card, "Penulis", NovelInfo.AUTHOR);
        addMetaRow(card, "Ilustrator", NovelInfo.ILLUSTRATOR);
        addMetaRow(card, "Genre", NovelInfo.GENRES);
        addMetaRow(card, "Status", NovelInfo.STATUS);
        addMetaRow(card, "Terjemahan", NovelInfo.TRANSLATION);

        parent.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addMetaRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(context, 7), 0, Ui.dp(context, 7));

        TextView name = Ui.label(context, label, 12, palette.muted);
        name.setMinWidth(Ui.dp(context, 92));
        row.addView(name);

        TextView text = Ui.label(context, value, 12, palette.text);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
    }

    private void addSynopsis(LinearLayout parent) {
        TextView heading = Ui.label(context, "Sinopsis", 15, palette.text);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 6));
        parent.addView(heading);

        String[] paragraphs = NovelInfo.SYNOPSIS;
        int visible = synopsisOpen
                ? paragraphs.length
                : Math.min(NovelInfo.SYNOPSIS_PREVIEW, paragraphs.length);

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < visible; i++) {
            if (i > 0) text.append("\n\n");
            text.append(paragraphs[i]);
            if (!synopsisOpen && visible < paragraphs.length && i == visible - 1) {
                text.append(" …");
            }
        }

        TextView body = Ui.label(context, text.toString(), 13, palette.text);
        body.setTag("synopsis");
        body.setLineSpacing(0, 1.45f);
        parent.addView(body);

        if (paragraphs.length > NovelInfo.SYNOPSIS_PREVIEW) {
            Button toggle = Ui.chip(context,
                    synopsisOpen ? "Tutup sinopsis" : "Baca selengkapnya", palette);
            toggle.setTag("synopsis_toggle");
            toggle.setTextColor(palette.accent);
            toggle.setOnClickListener(v -> listener.onToggleSynopsis());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, Ui.dp(context, 8), 0, 0);
            parent.addView(toggle, params);
        }
    }

    private void addContinue(LinearLayout parent) {
        Chapter resume = catalog.findByUrl(prefs.lastUrl());
        if (resume == null) return;

        int percent = prefs.progress(resume.url);
        String label = percent > 0
                ? "Lanjutkan Membaca · " + resume.label + " (" + percent + "%)"
                : "Lanjutkan Membaca · " + resume.label;

        Button button = Ui.chip(context, label, palette);
        button.setTag("continue");
        button.setTextSize(14);
        button.setTextColor(palette.accent);
        button.setOnClickListener(v -> listener.onOpenChapter(resume));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 14), 0, Ui.dp(context, 4));
        parent.addView(button, params);
    }

    private void addVolumes(LinearLayout parent) {
        TextView heading = Ui.label(context,
                "Volume & chapter · " + catalog.size() + " chapter dalam "
                        + catalog.volumes.size() + " volume", 15, palette.text);
        heading.setTag("volume_heading");
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 6));
        parent.addView(heading);

        for (Volume volume : catalog.volumes) {
            addVolume(parent, volume);
        }
    }

    private void addVolume(LinearLayout parent, Volume volume) {
        String key = String.valueOf(volume.number);
        boolean open = prefs.isExpanded(key);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setTag("volume:" + key);
        header.setBackground(Ui.rounded(palette.surface, palette.divider, 10, context));
        header.setPadding(Ui.dp(context, 12), Ui.dp(context, 11),
                Ui.dp(context, 12), Ui.dp(context, 11));

        TextView chevron = Ui.label(context, open ? "▾" : "▸", 13, palette.accent);
        chevron.setPadding(0, 0, Ui.dp(context, 8), 0);
        header.addView(chevron);

        TextView name = Ui.label(context, volume.title(), 15, palette.text);
        name.setTypeface(null, Typeface.BOLD);
        header.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        header.addView(Ui.label(context, volume.chapters.size() + " chapter", 11, palette.muted));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setTag("chapters:" + key);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            for (Chapter chapter : volume.chapters) {
                addChapter(body, chapter);
            }
        }

        header.setOnClickListener(v -> listener.onToggleVolume(key));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 4), 0, Ui.dp(context, 2));
        parent.addView(header, params);
        parent.addView(body);
    }

    private void addChapter(LinearLayout parent, Chapter chapter) {
        int progress = prefs.progress(chapter.url);
        boolean finished = progress > 95;
        boolean started = progress > 0 && !finished;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("chapter:" + chapter.url);
        row.setPadding(Ui.dp(context, 26), Ui.dp(context, 12),
                Ui.dp(context, 4), Ui.dp(context, 12));

        TextView label = Ui.label(context, chapter.label, 14,
                finished ? palette.muted : palette.text);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (finished) {
            row.addView(Ui.label(context, "selesai", 11, palette.muted));
        } else if (started) {
            row.addView(Ui.label(context, progress + "%", 11, palette.accent));
        }

        row.setOnClickListener(v -> listener.onOpenChapter(chapter));
        parent.addView(row);

        View line = new View(context);
        line.setBackgroundColor(palette.divider);
        parent.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 1))));
    }

    private void addAttribution(LinearLayout parent) {
        TextView attribution = Ui.label(context,
                "Sumber: Kaori Translation · kaoritranslation.blogspot.com\n"
                        + "Aplikasi ini hanya membuka isi publik dan tidak menyimpan salinan novel.",
                11, palette.muted);
        attribution.setTag("attribution");
        attribution.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 10));
        parent.addView(attribution);

        Button openSource = Ui.chip(context, "Kunjungi sumber asli", palette);
        openSource.setTag("source");
        openSource.setOnClickListener(v -> listener.onOpenSource());
        parent.addView(openSource);
    }
}
